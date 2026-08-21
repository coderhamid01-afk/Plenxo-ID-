package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File

object CatboxUploader {
    suspend fun uploadImage(context: Context, imageUri: Uri): String {
        return com.example.network.CatboxUploader.uploadImage(context, imageUri)
    }

    suspend fun uploadFile(file: File): String {
        return com.example.network.CatboxUploader.uploadFile(file)
    }

    suspend fun uploadByteArray(
        byteArray: ByteArray,
        fileName: String = "upload.jpg",
        mimeType: String = "image/jpeg"
    ): String {
        return com.example.network.CatboxUploader.uploadByteArray(byteArray, fileName, mimeType)
    }
}
