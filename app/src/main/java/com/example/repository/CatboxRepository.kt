package com.example.repository

import android.content.Context
import android.net.Uri
import java.io.File

interface CatboxRepository {
    /**
     * Uploads a File to Catbox.moe with real-time progress callbacks.
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String = "application/octet-stream",
        onProgress: (Int) -> Unit = {}
    ): String

    /**
     * Uploads a content Uri to Catbox.moe asynchronously on Dispatchers.IO with progress tracking.
     */
    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        onProgress: (Int) -> Unit = {}
    ): String

    /**
     * Uploads a raw text payload (e.g. heavy text up to 170 MB) as a Catbox text file asset.
     */
    suspend fun uploadTextPayload(
        text: String,
        fileName: String = "text_payload.txt",
        onProgress: (Int) -> Unit = {}
    ): String
}
