package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object VideoAutoDownloader {

    private const val TAG = "VideoAutoDownloader"
    private const val PREFS_NAME = "catbox_video_downloader_prefs"
    private const val KEY_SAVED_VIDEOS = "saved_video_urls"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    /**
     * Checks if a video message received by User B should be saved to the device's Camera Roll / Gallery.
     * Runs asynchronously on Dispatchers.IO.
     */
    fun checkAndAutoSaveVideo(
        context: Context,
        scope: CoroutineScope,
        messageId: String,
        senderId: String,
        currentUserId: String,
        messageType: String,
        mediaUrl: String
    ) {
        if (senderId == currentUserId) return // Only auto-save incoming videos received by User B
        val type = messageType.uppercase()
        if (type != "VIDEO" && !mediaUrl.contains(".mp4") && !mediaUrl.contains(".mov") && !mediaUrl.contains(".mkv")) return
        if (mediaUrl.isBlank() || !mediaUrl.startsWith("http")) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_SAVED_VIDEOS, emptySet()) ?: emptySet()

        if (savedSet.contains(mediaUrl) || savedSet.contains(messageId)) {
            Log.d(TAG, "Video $messageId ($mediaUrl) already saved to Camera Roll. Skipping.")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Auto-downloading incoming video to Camera Roll: $mediaUrl")
                val request = Request.Builder().url(mediaUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    Log.e(TAG, "Failed to download video stream from $mediaUrl: ${response.code}")
                    return@launch
                }

                val fileName = "VID_${System.currentTimeMillis()}_${messageId.take(8)}.mp4"
                val inputStream = response.body!!.byteStream()

                val savedUri = saveVideoToMediaStore(context, inputStream, fileName)

                if (savedUri != null) {
                    val updatedSet = savedSet.toMutableSet().apply {
                        add(mediaUrl)
                        add(messageId)
                    }
                    prefs.edit().putStringSet(KEY_SAVED_VIDEOS, updatedSet).apply()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "📹 Video saved to Camera Roll", Toast.LENGTH_SHORT).show()
                    }
                    Log.d(TAG, "Successfully saved video to MediaStore: $savedUri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-saving video to Camera Roll: ${e.message}", e)
            }
        }
    }

    private fun saveVideoToMediaStore(
        context: Context,
        inputStream: InputStream,
        fileName: String
    ): Uri? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Plenxo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            try {
                resolver.openOutputStream(videoUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)

                return videoUri
            } catch (e: Exception) {
                resolver.delete(videoUri, null, null)
                Log.e(TAG, "Failed to write video to MediaStore Q+: ${e.message}", e)
                return null
            }
        } else {
            @Suppress("DEPRECATION")
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val plenxoDir = File(moviesDir, "Plenxo")
            if (!plenxoDir.exists()) plenxoDir.mkdirs()

            val targetFile = File(plenxoDir, fileName)
            return try {
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save video to legacy storage: ${e.message}", e)
                null
            }
        }
    }
}
