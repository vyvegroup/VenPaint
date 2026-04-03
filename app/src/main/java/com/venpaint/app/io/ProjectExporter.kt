package com.venpaint.app.io

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.venpaint.app.engine.BlendMode
import com.venpaint.app.engine.LayerManager
import com.venpaint.app.util.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Exports the project as various image formats: PNG, JPG, PSD.
 */
class ProjectExporter(private val context: Context) {

    companion object {
        private const val TAG = "ProjectExporter"
        private const val EXPORT_DIR = "VenPaint/Exports"
    }

    /**
     * Export format options.
     */
    enum class ExportFormat(val extension: String, val mimeType: String) {
        PNG("png", "image/png"),
        JPG("jpg", "image/jpeg"),
        PSD("psd", "image/psd")
    }

    /**
     * Result of an export operation.
     */
    data class ExportResult(
        val success: Boolean,
        val uri: Uri? = null,
        val file: File? = null,
        val error: String? = null
    )

    /**
     * Export the flattened canvas as an image.
     */
    fun exportImage(
        layerManager: LayerManager,
        format: ExportFormat,
        quality: Int = 100,
        filename: String? = null
    ): ExportResult {
        val bitmap = layerManager.getFlattenedBitmap()
            ?: return ExportResult(success = false, error = "Failed to flatten layers")

        val name = filename ?: FileUtils.generateFilename("VenPaint", format.extension)

        return when (format) {
            ExportFormat.PNG -> exportPng(bitmap, name, quality)
            ExportFormat.JPG -> exportJpg(bitmap, name, quality)
            ExportFormat.PSD -> exportPsd(layerManager, name)
        }
    }

    /**
     * Export as PNG using MediaStore (for Android 10+) or file system.
     */
    private fun exportPng(bitmap: Bitmap, filename: String, quality: Int): ExportResult {
        return try {
            val uri = saveToMediaStore(bitmap, Bitmap.CompressFormat.PNG, filename, "image/png")
            if (uri != null) {
                ExportResult(success = true, uri = uri)
            } else {
                // Fallback to file system
                val file = saveToFileSystem(bitmap, Bitmap.CompressFormat.PNG, filename, quality)
                if (file != null) {
                    ExportResult(success = true, file = file)
                } else {
                    ExportResult(success = false, error = "Failed to save PNG")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PNG export failed", e)
            ExportResult(success = false, error = e.message)
        }
    }

    /**
     * Export as JPG using MediaStore or file system.
     */
    private fun exportJpg(bitmap: Bitmap, filename: String, quality: Int): ExportResult {
        return try {
            // JPG doesn't support transparency, so we need to add a white background
            val flatBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flatBitmap)
            canvas.drawColor(0xFFFFFFFF.toInt())
            canvas.drawBitmap(bitmap, 0f, 0f, null)

            val uri = saveToMediaStore(flatBitmap, Bitmap.CompressFormat.JPEG, filename, "image/jpeg")
            if (uri != null) {
                ExportResult(success = true, uri = uri)
            } else {
                val file = saveToFileSystem(flatBitmap, Bitmap.CompressFormat.JPEG, filename, quality)
                if (file != null) {
                    ExportResult(success = true, file = file)
                } else {
                    ExportResult(success = false, error = "Failed to save JPG")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JPG export failed", e)
            ExportResult(success = false, error = e.message)
        }
    }

    /**
     * Export as PSD (Adobe Photoshop) format.
     * This creates a simplified PSD file with layer data.
     */
    private fun exportPsd(layerManager: LayerManager, filename: String): ExportResult {
        return try {
            val psdBytes = createPsdFile(layerManager)
            val name = filename.removeSuffix(".psd") + ".psd"

            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, name)
            FileOutputStream(file).use { out ->
                out.write(psdBytes)
            }

            ExportResult(success = true, file = file)
        } catch (e: Exception) {
            Log.e(TAG, "PSD export failed", e)
            ExportResult(success = false, error = "PSD export failed: ${e.message}")
        }
    }

    /**
     * Create a simplified PSD file binary.
     * PSD file format: https://www.adobe.com/devnet-apps/photoshop/fileformatashtml/
     * This creates a basic PSD with merged image data only.
     */
    private fun createPsdFile(layerManager: LayerManager): ByteArray {
        val bitmap = layerManager.getFlattenedBitmap() ?: return ByteArray(0)
        val width = bitmap.width
        val height = bitmap.height
        val channels = 4 // RGBA

        // PSD Header (26 bytes)
        val header = ByteArray(26)
        // Signature: "8BPS"
        header[0] = '8'.code.toByte()
        header[1] = 'B'.code.toByte()
        header[2] = 'P'.code.toByte()
        header[3] = 'S'.code.toByte()
        // Version: 1
        header[4] = 0
        header[5] = 1
        // Reserved (6 bytes)
        header[6] = 0; header[7] = 0; header[8] = 0; header[9] = 0; header[10] = 0; header[11] = 0
        // Channels: 4 (RGB + Alpha)
        header[12] = 0; header[13] = 4
        // Height (4 bytes, big-endian)
        writeBigEndianInt(header, 14, height)
        // Width (4 bytes, big-endian)
        writeBigEndianInt(header, 18, width)
        // Depth: 8 bits per channel
        header[22] = 0; header[23] = 8
        // Color Mode: 3 = RGB
        header[24] = 0; header[25] = 3

        // Color Mode Data Section (0 bytes)
        val colorModeData = writeBigEndianInt(0)

        // Image Resources Section (0 bytes)
        val imageResources = writeBigEndianInt(0)

        // Layer and Mask Information Section (0 bytes for simplicity - merged image)
        val layerAndMaskInfo = writeBigEndianInt(0)

        // Image Data Section
        // Compression: 0 = Raw data
        val compression = writeBigEndianInt(0)

        // Read pixel data from bitmap (PSD stores in planar order: R, G, B, A)
        val pixelData = getImageDataForPsd(bitmap)

        // Combine all sections
        val outputStream = java.io.ByteArrayOutputStream()
        outputStream.write(header)
        outputStream.write(colorModeData)
        outputStream.write(imageResources)
        outputStream.write(layerAndMaskInfo)
        outputStream.write(compression)
        outputStream.write(pixelData)

        return outputStream.toByteArray()
    }

    /**
     * Extract pixel data in PSD planar format (R plane, G plane, B plane, A plane).
     */
    private fun getImageDataForPsd(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // PSD stores channels in planar order: R, G, B, A
        val data = ByteArray(width * height * 4)
        var offset = 0

        // Red plane
        for (i in pixels.indices) {
            data[offset++] = ((pixels[i] shr 16) and 0xFF).toByte()
        }
        // Green plane
        for (i in pixels.indices) {
            data[offset++] = ((pixels[i] shr 8) and 0xFF).toByte()
        }
        // Blue plane
        for (i in pixels.indices) {
            data[offset++] = (pixels[i] and 0xFF).toByte()
        }
        // Alpha plane
        for (i in pixels.indices) {
            data[offset++] = ((pixels[i] shr 24) and 0xFF).toByte()
        }

        return data
    }

    /**
     * Save bitmap to MediaStore.
     */
    private fun saveToMediaStore(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        filename: String,
        mimeType: String
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/$EXPORT_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(format, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Save bitmap to the app's file system.
     */
    private fun saveToFileSystem(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        filename: String,
        quality: Int
    ): File? {
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun writeBigEndianInt(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    private fun writeBigEndianInt(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value shr 24).toByte()
        array[offset + 1] = (value shr 16).toByte()
        array[offset + 2] = (value shr 8).toByte()
        array[offset + 3] = value.toByte()
    }
}
