package com.venpaint.app.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File utility functions for saving, loading, and managing project files.
 */
object FileUtils {

    /**
     * Application directory name for storing VenPaint files.
     */
    private const val APP_DIR = "VenPaint"

    /**
     * Get the app-specific directory.
     */
    fun getAppDir(context: Context): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), APP_DIR)
    }

    /**
     * Ensure the app directory exists.
     */
    fun ensureAppDir(context: Context): File {
        val dir = getAppDir(context)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Generate a unique filename with timestamp.
     */
    fun generateFilename(prefix: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_${timestamp}.$extension"
    }

    /**
     * Save a bitmap to the app directory.
     * Returns the saved file or null on failure.
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, format: Bitmap.CompressFormat, extension: String): File? {
        return try {
            val dir = ensureAppDir(context)
            val file = File(dir, generateFilename("VenPaint", extension))
            FileOutputStream(file).use { out ->
                bitmap.compress(format, 100, out)
            }
            // Notify the media scanner
            scanFile(context, file)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save a bitmap to MediaStore (for Android 10+).
     * Returns the content URI or null on failure.
     */
    fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, mimeType: String, displayName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/$APP_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                val format = when (mimeType) {
                    "image/png" -> Bitmap.CompressFormat.PNG
                    "image/jpeg" -> Bitmap.CompressFormat.JPEG
                    else -> Bitmap.CompressFormat.PNG
                }
                bitmap.compress(format, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Save raw bytes to a file.
     */
    fun saveBytes(context: Context, bytes: ByteArray, filename: String): File? {
        return try {
            val dir = ensureAppDir(context)
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                out.write(bytes)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Read bytes from a file.
     */
    fun readBytes(file: File): ByteArray? {
        return try {
            FileInputStream(file).use { fis ->
                fis.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Read bytes from a Uri.
     */
    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copy input stream to output stream.
     */
    fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
    }

    /**
     * Scan a file so it appears in the gallery / file browser.
     */
    private fun scanFile(context: Context, file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(file.extension),
                null
            )
        } catch (e: Exception) {
            // Ignore scan failures
        }
    }

    /**
     * Get file extension from a filename.
     */
    fun getExtension(filename: String): String {
        val dotIndex = filename.lastIndexOf('.')
        return if (dotIndex >= 0) filename.substring(dotIndex + 1).lowercase() else ""
    }

    /**
     * Check if the file extension is supported.
     */
    fun isSupportedImageExtension(extension: String): Boolean {
        return extension in listOf("png", "jpg", "jpeg", "webp")
    }

    /**
     * Load a bitmap from a file path.
     */
    fun loadBitmap(file: File): Bitmap? {
        return try {
            BitmapFactoryCompat.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load a bitmap from a Uri.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactoryCompat.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Compat wrapper for BitmapFactory.
 */
object BitmapFactoryCompat {
    fun decodeFile(path: String): Bitmap? {
        return android.graphics.BitmapFactory.decodeFile(path)
    }

    fun decodeStream(inputStream: InputStream): Bitmap? {
        return android.graphics.BitmapFactory.decodeStream(inputStream)
    }
}
