package com.example.network

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val storagePath = inputData.getString(KEY_STORAGE_PATH) ?: return Result.failure()

        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure()
        }

        return try {
            val secureUrl = CatboxStorageManager.uploadImage(
                applicationContext, 
                Uri.fromFile(file)
            )
            
            val outputData = workDataOf("secure_url" to secureUrl)
            Result.success(outputData)
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_STORAGE_PATH = "storage_path"
    }
}

