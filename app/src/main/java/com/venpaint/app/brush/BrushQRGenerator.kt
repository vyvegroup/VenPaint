package com.venpaint.app.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.util.Base64

/**
 * Generates QR codes from brush parameters for sharing.
 * Format: VP:<base64-encoded-json>
 */
class BrushQRGenerator(private val context: Context) {

    companion object {
        private const val QR_PREFIX = "VP:"
        private const val QR_SIZE = 600
    }

    private val gson = Gson()

    /**
     * Generate a QR code bitmap from a brush.
     */
    fun generateQR(brush: Brush): Bitmap? {
        return try {
            val json = gson.toJson(brush.toMap())
            val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
            val qrData = "$QR_PREFIX$encoded"

            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.encodeBitmap(qrData, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Encode a brush to a QR data string.
     */
    fun encodeBrushToString(brush: Brush): String? {
        return try {
            val json = gson.toJson(brush.toMap())
            val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
            "$QR_PREFIX$encoded"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save QR code as an image file.
     */
    fun saveQR(brush: Brush): Bitmap? {
        val bitmap = generateQR(brush) ?: return null
        // Caller can save the bitmap using FileUtils
        return bitmap
    }
}
