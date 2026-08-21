package com.example.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.File
import java.util.concurrent.TimeUnit

class CatboxRepositoryImpl : CatboxRepository {

    companion object {
        private const val CATBOX_URL = "https://catbox.moe/user/api.php"
        const val CATBOX_USERHASH = "9522593a4a22790d1bf20a178"
        private const val TAG = "CatboxRepository"

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun uploadFile(
        file: File,
        mimeType: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("Target file is empty or missing: ${file.absolutePath}")
        }

        Log.d(TAG, "Uploading file '${file.name}' (${file.length()} bytes) to Catbox...")
        onProgress(5)

        val rawRequestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val progressRequestBody = ProgressRequestBody(rawRequestBody, onProgress)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart("fileToUpload", file.name, progressRequestBody)
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty() || !responseText.startsWith("http")) {
            Log.e(TAG, "Catbox upload failed ($responseCode): $responseText")
            throw IllegalStateException("Catbox upload failed ($responseCode): $responseText")
        }

        onProgress(100)
        Log.d(TAG, "Catbox upload succeeded! URL: $responseText")
        responseText
    }

    override suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String?,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val resolvedName = getFileName(context, uri)
        val resolvedMime = mimeType ?: getMimeType(context, uri)
        val tempFile = File(context.cacheDir, "catbox_upload_${System.currentTimeMillis()}_$resolvedName")

        try {
            onProgress(5)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalArgumentException("Cannot open stream for Uri: $uri")

            onProgress(15)
            uploadFile(tempFile, resolvedMime) { progress ->
                val scaledProgress = 15 + ((progress * 85) / 100)
                onProgress(scaledProgress.coerceIn(15, 100))
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override suspend fun uploadTextPayload(
        text: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        Log.d(TAG, "Uploading text payload (${bytes.size} bytes) to Catbox as $fileName...")
        onProgress(10)

        val rawRequestBody = bytes.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
        val progressRequestBody = ProgressRequestBody(rawRequestBody, onProgress)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart("fileToUpload", fileName, progressRequestBody)
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty() || !responseText.startsWith("http")) {
            Log.e(TAG, "Catbox text payload upload failed ($responseCode): $responseText")
            throw IllegalStateException("Catbox upload failed ($responseCode): $responseText")
        }

        onProgress(100)
        Log.d(TAG, "Catbox text upload succeeded! URL: $responseText")
        responseText
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.let { name = it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve file name for Uri: $uri", e)
        }
        return name
    }

    private fun getMimeType(context: Context, uri: Uri): String {
        return try {
            val type = context.contentResolver.getType(uri)
            if (!type.isNullOrBlank()) type
            else {
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            }
        } catch (e: Exception) {
            "application/octet-stream"
        }
    }

    private class ProgressRequestBody(
        private val requestBody: RequestBody,
        private val onProgress: (Int) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType? = requestBody.contentType()

        override fun contentLength(): Long = try {
            requestBody.contentLength()
        } catch (e: Exception) {
            -1L
        }

        override fun writeTo(sink: BufferedSink) {
            val totalBytes = contentLength()
            if (totalBytes <= 0L) {
                requestBody.writeTo(sink)
                onProgress(100)
                return
            }

            var bytesWritten = 0L
            val countingSink = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    val progress = ((bytesWritten.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                    onProgress(progress)
                }
            }

            val bufferedSink = countingSink.buffer()
            requestBody.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
}
