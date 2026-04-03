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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Imports .ipv files from ibisPaint.
 *
 * .ipv files use a custom binary chunk format (NOT ZIP). Each chunk has:
 * - 4 bytes: chunk type (big-endian int32)
 * - 4 bytes: data length (big-endian uint32)
 * - N bytes: data
 * - 4 bytes: checksum (big-endian int32, must equal -(length+8))
 *
 * This importer also supports:
 * - Legacy .vpp ZIP format (backward compatibility)
 * - Raw image files as fallback
 */
class IpvImporter(private val context: Context) {

    companion object {
        private const val TAG = "IpvImporter"

        // Chunk type identifiers (matching IpvFormatWriter)
        private const val CHUNK_ADD_CANVAS   = 0x01000100
        private const val CHUNK_START_EDIT   = 0x01000200
        private const val CHUNK_IMAGE        = 0x01000500
        private const val CHUNK_META_INFO    = 0x01000600
        private const val CHUNK_ART_INFO_SUB = 0x30000e04
    }

    /**
     * Result of an import operation.
     */
    data class ImportResult(
        val success: Boolean,
        val layers: List<ImportedLayer> = emptyList(),
        val width: Int = 0,
        val height: Int = 0,
        val artworkName: String? = null,
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
     * Tries multiple formats in order:
     * 1. Binary .ipv chunk format
     * 2. Legacy .vpp ZIP format (backward compatibility)
     * 3. Raw image fallback
     */
    fun importFromBytes(bytes: ByteArray): ImportResult {
        // Try binary .ipv chunk format first
        val ipvResult = tryImportAsIpvChunks(bytes)
        if (ipvResult.success) return ipvResult

        // Try legacy ZIP format (.vpp / old .ipv)
        val zipResult = tryImportAsZip(bytes)
        if (zipResult.success) return zipResult

        // Try as raw image
        val imageResult = tryImportAsImage(bytes)
        if (imageResult.success) return imageResult

        return ImportResult(
            success = false,
            error = "Unable to parse file. Not a valid .ipv binary, ZIP archive, or image."
        )
    }

    // ==================== Binary .ipv Chunk Parser ====================

    /**
     * Try to parse the bytes as a binary .ipv chunk format file.
     */
    private fun tryImportAsIpvChunks(bytes: ByteArray): ImportResult {
        return try {
            val importedLayers = mutableListOf<ImportedLayer>()
            var canvasWidth = 0
            var canvasHeight = 0
            var artworkName: String? = null

            val stream = ByteArrayInputStream(bytes)
            var layerIndex = 0

            while (stream.available() > 0) {
                // Read chunk header: type (4 bytes) + length (4 bytes)
                val header = ByteArray(8)
                val headerRead = stream.read(header)
                if (headerRead < 8) break

                val type = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                val length = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).int

                // Validate length
                if (length < 0 || length > 100_000_000) {
                    Log.w(TAG, "Invalid chunk length $length for type 0x${type.toString(16)}, skipping")
                    break
                }

                // Read chunk data
                val data = ByteArray(length)
                val dataRead = stream.read(data)
                if (dataRead < length) break

                // Read and validate checksum (4 bytes)
                val checksumBytes = ByteArray(4)
                val checksumRead = stream.read(checksumBytes)
                if (checksumRead < 4) break

                val checksum = ByteBuffer.wrap(checksumBytes).order(ByteOrder.BIG_ENDIAN).int
                // Validate: (length + checksum + 8) should be 0
                val validation = length + checksum + 8
                if (validation != 0) {
                    Log.w(TAG, "Checksum mismatch for chunk 0x${type.toString(16)}: " +
                            "length=$length, checksum=$checksum, validation=$validation")
                    // Try to continue parsing anyway - some files may have slight variations
                }

                // Process chunk by type
                when (type) {
                    CHUNK_ADD_CANVAS -> {
                        val canvasInfo = parseAddCanvas(data)
                        if (canvasInfo != null) {
                            canvasWidth = canvasInfo.width
                            canvasHeight = canvasInfo.height
                            Log.d(TAG, "AddCanvas: ${canvasWidth}x${canvasHeight}, id=${canvasInfo.identifier}")
                        }
                    }
                    CHUNK_IMAGE -> {
                        val bitmap = parseImageChunk(data)
                        if (bitmap != null) {
                            layerIndex++
                            importedLayers.add(
                                ImportedLayer(
                                    name = "Layer $layerIndex",
                                    bitmap = bitmap,
                                    opacity = 1.0f,
                                    isVisible = true
                                )
                            )
                            Log.d(TAG, "Image chunk: layer $layerIndex, ${bitmap.width}x${bitmap.height}")
                        }
                    }
                    CHUNK_META_INFO -> {
                        val name = parseMetaInfo(data)
                        if (name != null && artworkName == null) {
                            artworkName = name
                            Log.d(TAG, "MetaInfo: artworkName=$name")
                        }
                    }
                    CHUNK_ART_INFO_SUB -> {
                        val name = parseArtInfoSub(data)
                        if (name != null) {
                            artworkName = name
                            Log.d(TAG, "ArtInfoSub: artworkName=$name")
                        }
                    }
                    CHUNK_START_EDIT -> {
                        // Parse for logging purposes
                        val editInfo = parseStartEdit(data)
                        Log.d(TAG, "StartEdit: ${editInfo?.appName} ${editInfo?.version}")
                    }
                    else -> {
                        Log.d(TAG, "Skipping unknown chunk type: 0x${type.toString(16)} ($length bytes)")
                    }
                }
            }

            if (importedLayers.isEmpty()) {
                return ImportResult(success = false, error = "No image layers found in .ipv binary format")
            }

            // Use detected dimensions or fall back to layer bitmap dimensions
            if (canvasWidth == 0 || canvasHeight == 0) {
                canvasWidth = importedLayers.maxOf { it.bitmap.width }
                canvasHeight = importedLayers.maxOf { it.bitmap.height }
            }

            ImportResult(
                success = true,
                layers = importedLayers,
                width = canvasWidth,
                height = canvasHeight,
                artworkName = artworkName
            )
        } catch (e: Exception) {
            Log.d(TAG, "Not a valid .ipv binary file: ${e.message}")
            ImportResult(success = false, error = "Not a valid .ipv binary format: ${e.message}")
        }
    }

    /**
     * Parse AddCanvas chunk (0x01000100) data.
     * Returns canvas info or null on failure.
     */
    private fun parseAddCanvas(data: ByteArray): CanvasInfo? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            // 8 bytes: timestamp as BE double
            val timestamp = buf.double

            // 4 bytes: width as BE u32
            val width = buf.int

            // 4 bytes: height as BE u32
            val height = buf.int

            // String: identifier (BE u16 length + UTF-8)
            val identifier = readString(buf)

            // 1 byte: artwork type
            val artworkType = buf.get().toInt() and 0xFF

            CanvasInfo(timestamp, width, height, identifier, artworkType)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse AddCanvas chunk", e)
            null
        }
    }

    private data class CanvasInfo(
        val timestamp: Double,
        val width: Int,
        val height: Int,
        val identifier: String,
        val artworkType: Int
    )

    /**
     * Parse Image chunk (0x01000500) data and extract the embedded PNG bitmap.
     *
     * Real .ipv format (from reverse engineering):
     * - 8 bytes: timestamp as BE double
     * - 4 bytes: flags (0xFFFFFFFF = real image, 0x00000000 = preview)
     * - 4 bytes: unknown
     * - 2 bytes: unknown
     * - 2 bytes: unknown
     * - variable: PNG data (search for PNG signature 89 50 4E 47)
     *
     * The PNG may not be at a fixed offset - we search for the PNG signature.
     */
    private fun parseImageChunk(data: ByteArray): Bitmap? {
        return try {
            // Search for PNG signature within the chunk data
            val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            var pngStart = -1
            for (i in 0..minOf(data.size - 8, 200)) {
                var found = true
                for (j in PNG_SIGNATURE.indices) {
                    if (data[i + j] != PNG_SIGNATURE[j]) {
                        found = false
                        break
                    }
                }
                if (found) {
                    pngStart = i
                    break
                }
            }

            if (pngStart < 0) {
                Log.w(TAG, "No PNG signature found in Image chunk (size=${data.size})")
                return null
            }

            // Extract PNG data from signature to IEND
            val IEND_SIGNATURE = byteArrayOf(0x49, 0x45, 0x4E, 0x44)
            var pngEnd = -1
            for (i in pngStart until data.size - 4) {
                if (data[i] == IEND_SIGNATURE[0] && data[i+1] == IEND_SIGNATURE[1] &&
                    data[i+2] == IEND_SIGNATURE[2] && data[i+3] == IEND_SIGNATURE[3]) {
                    // IEND chunk: type(4) + CRC(4) = 8 bytes after IEND marker
                    pngEnd = i + 8
                    break
                }
            }

            if (pngEnd < 0 || pngEnd <= pngStart) {
                // Fallback: use rest of data as PNG
                pngEnd = data.size
            }

            val pngData = data.copyOfRange(pngStart, pngEnd)
            Log.d(TAG, "Extracted PNG from Image chunk: offset=$pngStart, size=${pngData.size}")

            BitmapFactory.decodeByteArray(pngData, 0, pngData.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Image chunk", e)
            null
        }
    }

    /**
     * Parse MetaInfo chunk (0x01000600) data to extract artwork name.
     *
     * MetaInfo has a complex structure. The artwork name is embedded as a
     * formatted string (BE u16 length + UTF-8) somewhere in the data.
     * We search for readable strings to find the artwork name.
     */
    private fun parseMetaInfo(data: ByteArray): String? {
        return try {
            // Search for readable UTF-8 strings in the metadata
            var bestName: String? = null
            var i = 0
            while (i < data.size - 2) {
                val strLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                if (strLen in 3..200 && i + 2 + strLen <= data.size) {
                    val candidate = String(data, i + 2, strLen, Charsets.UTF_8)
                    // Check if it's a readable name (not binary data)
                    if (candidate.all { ch -> ch.isLetterOrDigit() || ch.isWhitespace() || ch in "._-!@#$%^&*()" }) {
                        if (bestName == null || candidate.length > bestName.length) {
                            bestName = candidate
                        }
                    }
                }
                i += 1
            }
            bestName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MetaInfo chunk", e)
            null
        }
    }

    /**
     * Parse ArtInfoSub chunk (0x30000e04) data to extract artwork title.
     * Data format:
     * - String: artwork title
     */
    private fun parseArtInfoSub(data: ByteArray): String? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            readString(buf)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ArtInfoSub chunk", e)
            null
        }
    }

    /**
     * Parse StartEdit chunk (0x01000200) for logging.
     */
    private fun parseStartEdit(data: ByteArray): StartEditInfo? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            // 4 bytes: unknown
            buf.int

            // 8 bytes: timestamp as BE double
            val timestamp = buf.double

            // Strings: app name, version, device
            val appName = readString(buf)
            val version = readString(buf)
            val device = readString(buf)

            StartEditInfo(timestamp, appName, version, device)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse StartEdit chunk", e)
            null
        }
    }

    private data class StartEditInfo(
        val timestamp: Double,
        val appName: String,
        val version: String,
        val device: String
    )

    /**
     * Read a .ipv format string from a ByteBuffer: BE u16 length + UTF-8 bytes.
     */
    private fun readString(buf: ByteBuffer): String {
        val length = buf.short.toInt() and 0xFFFF
        if (length <= 0 || length > buf.remaining()) {
            return ""
        }
        val bytes = ByteArray(length)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    // ==================== Legacy ZIP Format Parser ====================

    /**
     * Try to parse the bytes as a ZIP archive (legacy .vpp format).
     */
    private fun tryImportAsZip(bytes: ByteArray): ImportResult {
        return try {
            val importedLayers = mutableListOf<ImportedLayer>()
            var maxWidth = 0
            var maxHeight = 0
            var metadata: JSONObject? = null

            ZipInputStream(bytes.inputStream()).use { zipIn ->
                var entry: ZipEntry?

                while (zipIn.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name

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
                            readZipEntryText(zipIn) // Consume
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

    // ==================== Raw Image Fallback ====================

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

    // ==================== Layer Manager Integration ====================

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

    // ==================== Utility Methods ====================

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
