package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QRCodeUtils {

    /**
     * Generates a high-resolution 512x512 QR code bitmap with custom dark/light colors
     * and an optional circular logo/avatar overlay in the center.
     */
    fun generateCustomQRCode(
        data: String,
        logoBitmap: Bitmap? = null,
        size: Int = 512,
        darkColor: Int = 0xFF1E1E2E.toInt(),
        lightColor: Int = 0xFFFFFFFF.toInt()
    ): Bitmap {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
                // Use High error correction so logo overlay in the center doesn't corrupt QR readability
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            }

            val qrWriter = QRCodeWriter()
            val bitMatrix = qrWriter.encode(data, BarcodeFormat.QR_CODE, size, size, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height

            val pixels = IntArray(matrixWidth * matrixHeight)
            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }

            val baseBitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            baseBitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)

            if (logoBitmap == null) {
                return baseBitmap
            }

            // Draw center circular container and overlay logo
            val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)

            val centerX = matrixWidth / 2f
            val centerY = matrixHeight / 2f
            val overlayRadius = matrixWidth * 0.13f

            // Clean circular white container in center
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, overlayRadius, bgPaint)

            // Subtle outer border stroke
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkColor
                style = Paint.Style.STROKE
                strokeWidth = matrixWidth * 0.008f
            }
            canvas.drawCircle(centerX, centerY, overlayRadius, borderPaint)

            // Draw circular cropped logo inside the center container
            val logoRadius = overlayRadius * 0.85f
            val logoSize = (logoRadius * 2).toInt()

            if (logoSize > 0) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
                val clipPath = Path().apply {
                    addCircle(centerX, centerY, logoRadius, Path.Direction.CW)
                }

                canvas.save()
                canvas.clipPath(clipPath)
                canvas.drawBitmap(scaledLogo, centerX - logoRadius, centerY - logoRadius, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
            }

            mutableBitmap
        } catch (e: Exception) {
            Log.e("QRCodeUtils", "Failed to generate custom QR code: ${e.message}", e)
            // Fallback fallback blank white bitmap
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        }
    }
}
