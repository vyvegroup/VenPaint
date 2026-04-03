package com.venpaint.app.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.venpaint.app.engine.Layer
import com.venpaint.app.engine.LayerManager
import com.venpaint.app.util.FileUtils
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Imports .ipv files from ibisPaint.
 * .ipv files are ZIP archives containing layer data (PNG/JPEG images) and metadata.
 */
class IpvImporter(private val context: Context) {

    companion object {
        private const val TAG = "IpvImporter"
    }

    /**
     * Result of an import operation.
     */
    data class ImportResult(
        val success: Boolean,
        val layers: List<ImportedLayer> = emptyList(),
        val width: Int = 0,
        val height: Int = 0,
        val error: String? = null
    )

    data class ImportedLayer(
        val name: String,
        val bitmap: Bitmap,
        val opacity: Float = 1.0f,
        val isVisible: Boolean = true
    )

    /**
     * Import an .ipv file from a Uri.
     */
    fun importFromUri(uri: Uri): ImportResult {
        return try {
            val bytes = FileUtils.readBytesFromUri(context, uri)
            if (bytes == null) {
                ImportResult(success = false, error = "Failed to read file")
            } else {
                importFromBytes(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(success = false, error = e.message)
        }
    }

    /**
     * Import an .ipv file from bytes.
     */
    fun importFromBytes(bytes: ByteArray): ImportResult {
        // Try as ZIP first (standard .ipv format)
        val zipResult = tryImportAsZip(bytes)
        if (zipResult.success) return zipResult

        // Try as raw image
        val imageResult = tryImportAsImage(bytes)
        if (imageResult.success) return imageResult

        return ImportResult(success = false, error = "Unable to parse .ipv file. Not a valid ZIP archive or image.")
    }

    /**
     * Try to parse the bytes as a ZIP archive.
     */
    private fun tryImportAsZip(bytes: ByteArray): ImportResult {
        return try {
            val importedLayers = mutableListOf<ImportedLayer>()
            var maxWidth = 0
            var maxHeight = 0
            var metadata: JSONObject? = null

            ZipInputStream(bytes.inputStream()).use { zipIn ->
                var entry: ZipEntry?
                val entries = mutableListOf<String>()

                while (zipIn.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    entries.add(name)

                    when {
                        // Look for JSON metadata
                        name.endsWith(".json", ignoreCase = true) ||
                        name == "info.json" ||
                        name == "meta.json" ||
                        name.contains("metadata") -> {
                            val content = readZipEntryText(zipIn)
                            if (content != null) {
                                try {
                                    metadata = JSONObject(content)
                                } catch (e: Exception) {
                                    // Not valid JSON, ignore
                                }
                            }
                        }
                        // Look for XML metadata
                        name.endsWith(".xml", ignoreCase = true) -> {
                            // ibisPaint sometimes uses XML for metadata
                            val content = readZipEntryText(zipIn)
                            // Could parse XML here if needed
                        }
                        // Look for image files (layers)
                        name.endsWith(".png", ignoreCase = true) ||
                        name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) -> {
                            val bitmap = BitmapFactory.decodeStream(zipIn)
                            if (bitmap != null) {
                                maxWidth = maxOf(maxWidth, bitmap.width)
                                maxHeight = maxOf(maxHeight, bitmap.height)

                                val layerName = File(name).nameWithoutExtension
                                importedLayers.add(
                                    ImportedLayer(
                                        name = layerName,
                                        bitmap = bitmap,
                                        opacity = 1.0f,
                                        isVisible = true
                                    )
                                )
                            }
                        }
                        // Skip unknown file types
                        else -> {
                            // Consume the entry to move to the next
                            zipIn.readBytes()
                        }
                    }
                }
            }

            if (importedLayers.isEmpty()) {
                return ImportResult(success = false, error = "No image layers found in ZIP archive")
            }

            // Apply metadata if available
            metadata?.let { meta ->
                try {
                    if (meta.has("width")) maxWidth = meta.getInt("width")
                    if (meta.has("height")) maxHeight = meta.getInt("height")
                } catch (e: Exception) {
                    // Use detected dimensions
                }
            }

            // Reverse layers so the first entry in the ZIP is on top
            importedLayers.reverse()

            ImportResult(
                success = true,
                layers = importedLayers,
                width = maxWidth,
                height = maxHeight
            )
        } catch (e: Exception) {
            Log.d(TAG, "Not a ZIP file: ${e.message}")
            ImportResult(success = false, error = "Not a ZIP file")
        }
    }

    /**
     * Try to parse the bytes as a raw image.
     */
    private fun tryImportAsImage(bytes: ByteArray): ImportResult {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                ImportResult(success = false, error = "Could not decode image")
            } else {
                ImportResult(
                    success = true,
                    layers = listOf(
                        ImportedLayer(
                            name = "Layer 1",
                            bitmap = bitmap,
                            opacity = 1.0f,
                            isVisible = true
                        )
                    ),
                    width = bitmap.width,
                    height = bitmap.height
                )
            }
        } catch (e: Exception) {
            ImportResult(success = false, error = "Could not decode as image")
        }
    }

    /**
     * Apply imported layers to a LayerManager.
     */
    fun applyToLayerManager(result: ImportResult, layerManager: LayerManager) {
        if (!result.success) return

        // Resize layer manager to match imported dimensions
        layerManager.resize(result.width, result.height)

        // Add imported layers
        for ((index, importedLayer) in result.layers.withIndex()) {
            if (index == 0) {
                // Replace the first layer
                val existing = layerManager.getLayer(0)
                if (existing != null) {
                    existing.bitmap?.recycle()
                    existing.bitmap = importedLayer.bitmap
                    existing.name = importedLayer.name
                    existing.opacity = importedLayer.opacity
                    existing.isVisible = importedLayer.isVisible
                }
            } else {
                // Add new layers
                val layer = layerManager.addLayer(importedLayer.name)
                if (layer != null) {
                    layer.bitmap?.recycle()
                    layer.bitmap = importedLayer.bitmap
                    layer.opacity = importedLayer.opacity
                    layer.isVisible = importedLayer.isVisible
                }
            }
        }
    }

    /**
     * Read text content from a zip entry.
     */
    private fun readZipEntryText(zipIn: ZipInputStream): String? {
        return try {
            BufferedReader(InputStreamReader(zipIn)).use { reader ->
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(line)
                }
                sb.toString()
            }
        } catch (e: Exception) {
            null
        }
    }
}
