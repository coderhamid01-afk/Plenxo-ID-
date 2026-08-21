package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object CatboxStorageManager {

    private const val CATBOX_URL = "https://catbox.moe/user/api.php"
    const val CATBOX_USERHASH = "9522593a4a22790d1bf20a178"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Compresses and uploads an image URI to Catbox REST API (https://catbox.moe/user/api.php)
     * and returns direct CDN URL plain text (e.g., https://files.catbox.moe/abc123.jpg).
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): String {
        return CatboxUploader.uploadImage(context, imageUri)
    }

    suspend fun uploadImageFile(file: File): String {
        return CatboxUploader.uploadFile(file)
    }

    /**
     * Uploads a recorded audio file to Catbox API (https://catbox.moe/user/api.php)
     * and returns the direct public URL.
     *
     * @param file Recorded .m4a file
     * @return Direct public Catbox URL (e.g., https://files.catbox.moe/abc123.m4a)
     */
    suspend fun uploadVoiceNote(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("Audio file is empty or does not exist.")
        }

        Log.d("CatboxStorageManager", "Uploading audio file '${file.name}' (${file.length()} bytes) to Catbox...")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart(
                "fileToUpload",
                file.name,
                file.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty()) {
            Log.e("CatboxStorageManager", "Catbox upload failed. HTTP $responseCode: $responseText")
            throw IllegalStateException("Catbox upload failed ($responseCode): $responseText")
        }

        Log.d("CatboxStorageManager", "Catbox upload succeeded! URL: $responseText")
        return@withContext responseText
    }
}
