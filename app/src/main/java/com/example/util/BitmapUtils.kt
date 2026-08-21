package com.example.util

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.graphics.BitmapFactory

object BitmapUtils {
    fun getOptimizedCompressedFile(context: Context, uri: Uri): File {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val scaledBitmap = scaleBitmap(bitmap, 500, 500)
        
        val file = File(context.cacheDir, "temp_optimized_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return file
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = Math.min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
