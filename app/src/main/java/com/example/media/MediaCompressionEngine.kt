package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object MediaCompressionEngine {

    /**
     * Processes an image entirely in the background, downscales it to max 1920x1080,
     * and transcodes it to WebP format at 75% quality.
     */
    suspend fun compressImage(context: Context, sourceUri: Uri, outputFile: File): Result<File> =
        withContext(Dispatchers.Default) {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var sourceBitmap: Bitmap? = null

            try {
                // 1. Decode bounds to read image metadata without loading full pixels
                inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream == null) {
                    return@withContext Result.failure(Exception("Cannot open input stream for $sourceUri"))
                }

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close() // Close immediately to avoid leaks

                // 2. Calculate inSampleSize to restrict dimensions to 1920x1080
                options.inSampleSize = calculateInSampleSize(options, 1920, 1080)
                options.inJustDecodeBounds = false

                // 3. Decode actual bitmap with calculated sample size
                inputStream = context.contentResolver.openInputStream(sourceUri)
                sourceBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                
                if (sourceBitmap == null) {
                    return@withContext Result.failure(Exception("Failed to decode bitmap"))
                }

                // 4. Transcode to WebP at 75% quality
                outputStream = FileOutputStream(outputFile)
                val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                
                val success = sourceBitmap.compress(format, 75, outputStream)
                if (!success) {
                    return@withContext Result.failure(Exception("Failed to compress bitmap to WebP"))
                }

                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // Explicit Stream Reclamation
                try {
                    inputStream?.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
                try {
                    outputStream?.flush()
                    outputStream?.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
                // Free native heap allocations immediately
                sourceBitmap?.recycle()
            }
        }

    /**
     * Programmatically calculate inSampleSize to scale down image dimensions.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
