package com.example.database

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class WallpaperDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val wallpaperId = inputData.getString("WALLPAPER_ID_INPUT") ?: return Result.failure()
        val cloudUrl = inputData.getString("CLOUD_URL_INPUT") ?: return Result.failure()

        return try {
            Log.d("WallpaperDownloadWorker", "Starting download for wallpaper $wallpaperId from $cloudUrl")
            
            val dir = File(context.filesDir, "wallpapers")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val destinationFile = File(dir, "${wallpaperId}.4k")
            
            // Streaming read from URL
            val url = URL(cloudUrl)
            val connection = url.openConnection()
            connection.connect()
            
            BufferedInputStream(connection.getInputStream()).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            
            Log.d("WallpaperDownloadWorker", "Successfully downloaded wallpaper $wallpaperId to ${destinationFile.absolutePath}")
            
            // Update SQLite Room table via AppDatabase
            val database = AppDatabase.getDatabase(context)
            val dao = database.chatWallpaperDao()
            val existing = dao.getWallpaperById(wallpaperId)
            if (existing != null) {
                val updated = existing.copy(
                    isDownloaded = true,
                    localFilePath = destinationFile.absolutePath,
                    fileSizeInBytes = destinationFile.length()
                )
                dao.insertWallpaper(updated)
            } else {
                val entity = ChatWallpaperEntity(
                    wallpaperId = wallpaperId,
                    category = "UNKNOWN",
                    cloudUrl = cloudUrl,
                    thumbnailCloudUrl = cloudUrl,
                    localFilePath = destinationFile.absolutePath,
                    isDownloaded = true,
                    fileSizeInBytes = destinationFile.length()
                )
                dao.insertWallpaper(entity)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperDownloadWorker", "Error downloading wallpaper $wallpaperId", e)
            Result.retry()
        }
    }
}
