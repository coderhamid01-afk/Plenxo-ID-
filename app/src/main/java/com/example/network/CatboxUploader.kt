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
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object CatboxUploader {

    private const val CATBOX_URL = "https://catbox.moe/user/api.php"
    private const val CATBOX_USERHASH = "9522593a4a22790d1bf20a178"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Uploads an image Uri to Catbox API (https://catbox.moe/user/api.php)
     * and returns the direct CDN URL string (e.g. https://files.catbox.moe/abc123.png).
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        val tempFile = try {
            com.example.util.BitmapUtils.getOptimizedCompressedFile(context, imageUri)
        } catch (e: Exception) {
            Log.e("CatboxUploader", "Bitmap compression failed, fallback to direct stream copy: ${e.message}")
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw IllegalArgumentException("Cannot open image stream for Uri: $imageUri")
            val file = File(context.cacheDir, "catbox_upload_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output -> inputStream.copyTo(output) }
            file
        }

        try {
            uploadFile(tempFile)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Uploads a File payload to Catbox API using multipart/form-data with reqtype="fileupload" and fileToUpload.
     * Returns direct RAW PLAIN TEXT URL string.
     */
    suspend fun uploadFile(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("File is empty or does not exist.")
        }

        Log.d("CatboxUploader", "Uploading file '${file.name}' (${file.length()} bytes) to Catbox...")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart(
                "fileToUpload",
                file.name,
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty() || !responseText.startsWith("http")) {
            Log.e("CatboxUploader", "Catbox upload failed. HTTP $responseCode: $responseText")
            throw IllegalStateException("Failed to upload image to Catbox. HTTP $responseCode: $responseText")
        }

        Log.d("CatboxUploader", "Catbox image upload succeeded! URL: $responseText")
        responseText
    }

    /**
     * Uploads a ByteArray payload to Catbox API using multipart/form-data with reqtype="fileupload" and fileToUpload.
     */
    suspend fun uploadByteArray(
        byteArray: ByteArray,
        fileName: String = "upload.jpg",
        mimeType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("ByteArray is empty.")
        }

        Log.d("CatboxUploader", "Uploading byte array (${byteArray.size} bytes) to Catbox...")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart(
                "fileToUpload",
                fileName,
                byteArray.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty() || !responseText.startsWith("http")) {
            Log.e("CatboxUploader", "Catbox upload failed. HTTP $responseCode: $responseText")
            throw IllegalStateException("Failed to upload image to Catbox. HTTP $responseCode: $responseText")
        }

        Log.d("CatboxUploader", "Catbox image upload succeeded! URL: $responseText")
        responseText
    }
}
