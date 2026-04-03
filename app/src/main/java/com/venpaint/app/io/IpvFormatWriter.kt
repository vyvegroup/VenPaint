package com.venpaint.app.io

import android.graphics.Bitmap
import android.util.Log
import com.venpaint.app.engine.Layer
import com.venpaint.app.engine.LayerManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes .ipv (ibisPaint) binary chunk format files.
 *
 * The .ipv format is a series of binary chunks. Each chunk has:
 * - 4 bytes: chunk type identifier (big-endian)
 * - 4 bytes: data length (big-endian uint32)
 * - N bytes: data (depends on chunk type)
 * - 4 bytes: checksum (big-endian int32, must equal -(length+8) so length+checksum+8=0)
 *
 * Key chunk types:
 * - 0x01000100 (AddCanvas): Canvas creation metadata
 * - 0x01000200 (StartEdit): Edit session start info
 * - 0x01000500 (Image): Embedded PNG data for layers
 * - 0x01000600 (MetaInfo): Session metadata
 * - 0x30000e04 (ArtInfoSub): Artwork title
 */
class IpvFormatWriter {

    companion object {
        private const val TAG = "IpvFormatWriter"

        // Chunk type identifiers
        const val CHUNK_ADD_CANVAS   = 0x01000100
        const val CHUNK_START_EDIT   = 0x01000200
        const val CHUNK_IMAGE        = 0x01000500
        const val CHUNK_META_INFO    = 0x01000600
        const val CHUNK_ART_INFO_SUB = 0x30000e04

        // Artwork type constants
        const val ARTWORK_TYPE_ILLUSTRATION: Byte = 0x00

        // Default app info
        const val APP_NAME = "VenPaint"
        const val APP_VERSION = "1.0.0"
        const val DEFAULT_DEVICE = "Android"
        const val DEFAULT_IDENTIFIER = "VenPaint"
    }

    /**
     * Result of an .ipv write operation.
     */
    data class WriteResult(
        val success: Boolean,
        val file: File? = null,
        val bytes: ByteArray? = null,
        val error: String? = null
    )

    /**
     * Write an .ipv file from a LayerManager.
     *
     * @param layerManager The layer manager containing the project layers
     * @param outputFile The destination file
     * @param artworkName The name of the artwork
     * @return WriteResult indicating success or failure
     */
    fun writeIpvFile(
        layerManager: LayerManager,
        outputFile: File,
        artworkName: String = "Untitled"
    ): WriteResult {
        return try {
            val layers = layerManager.getLayers()
            if (layers.isEmpty()) {
                return WriteResult(success = false, error = "No layers to save")
            }

            val firstBitmap = layers.firstOrNull()?.bitmap
            val width = firstBitmap?.width ?: 1080
            val height = firstBitmap?.height ?: 1920

            val bytes = buildIpvBytes(layers, width, height, artworkName)

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                out.write(bytes)
            }

            Log.d(TAG, "Written .ipv file: ${outputFile.absolutePath} (${bytes.size} bytes)")
            WriteResult(success = true, file = outputFile, bytes = bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write .ipv file", e)
            WriteResult(success = false, error = e.message)
        }
    }

    /**
     * Build the complete .ipv file as a byte array.
     */
    fun buildIpvBytes(
        layers: List<Layer>,
        width: Int,
        height: Int,
        artworkName: String
    ): ByteArray {
        val output = ByteArrayOutputStream()

        val timestamp = System.currentTimeMillis() / 1000.0

        // 1. AddCanvas chunk (required - minimum viable .ipv)
        writeAddCanvasChunk(output, timestamp, width, height, DEFAULT_IDENTIFIER)

        // 2. StartEdit chunk
        writeStartEditChunk(output, timestamp, APP_NAME, APP_VERSION, DEFAULT_DEVICE)

        // 3. Image chunks - one per layer (bottom to top)
        // Write layers in reverse order (bottom layer first, which is index 0)
        for (layer in layers) {
            val bitmap = layer.bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                writeImageChunk(output, timestamp, bitmap)
            }
        }

        // 4. MetaInfo chunk with artwork name
        writeMetaInfoChunk(output, timestamp, artworkName)

        // 5. ArtInfoSub chunk with artwork title
        writeArtInfoSubChunk(output, artworkName)

        return output.toByteArray()
    }

    /**
     * Write a generic chunk to the output stream.
     * Format: type(4 BE) + length(4 BE) + data + checksum(4 BE)
     * Checksum: -(length + 8) as signed 32-bit big-endian
     */
    private fun writeChunk(output: ByteArrayOutputStream, type: Int, data: ByteArray) {
        val length = data.size

        // Write chunk type (4 bytes, big-endian)
        output.write(intToBytesBE(type))

        // Write data length (4 bytes, big-endian unsigned)
        output.write(intToBytesBE(length))

        // Write data
        output.write(data)

        // Write checksum: -(length + 8) as signed 32-bit
        // The constraint is: (length + checksum + 8) == 0
        val checksum = -(length + 8)
        output.write(intToBytesBE(checksum))
    }

    /**
     * Write an AddCanvas chunk (0x01000100).
     * Data format:
     * - 8 bytes: timestamp as BE double
     * - 4 bytes: width as BE u32
     * - 4 bytes: height as BE u32
     * - 2 + N bytes: identifier string (BE u16 length + UTF-8)
     * - 1 byte: artwork type (0 = Illustration)
     */
    private fun writeAddCanvasChunk(
        output: ByteArrayOutputStream,
        timestamp: Double,
        width: Int,
        height: Int,
        identifier: String
    ) {
        val data = ByteArrayOutputStream()

        // Timestamp as BE double (8 bytes)
        data.write(doubleToBytesBE(timestamp))

        // Width as BE u32 (4 bytes)
        data.write(intToBytesBE(width))

        // Height as BE u32 (4 bytes)
        data.write(intToBytesBE(height))

        // Identifier string (BE u16 length + UTF-8 bytes)
        writeString(data, identifier)

        // Artwork type (1 byte: 0 = Illustration)
        data.write(ARTWORK_TYPE_ILLUSTRATION.toInt())

        writeChunk(output, CHUNK_ADD_CANVAS, data.toByteArray())
    }

    /**
     * Write a StartEdit chunk (0x01000200).
     * Data format:
     * - 4 bytes: unknown (0x00000000)
     * - 8 bytes: timestamp as BE double
     * - String: app name
     * - String: version
     * - String: device name
     */
    private fun writeStartEditChunk(
        output: ByteArrayOutputStream,
        timestamp: Double,
        appName: String,
        version: String,
        deviceName: String
    ) {
        val data = ByteArrayOutputStream()

        // Unknown 4 bytes (0x00000000)
        data.write(intToBytesBE(0))

        // Timestamp as BE double (8 bytes)
        data.write(doubleToBytesBE(timestamp))

        // App name string
        writeString(data, appName)

        // Version string
        writeString(data, version)

        // Device name string
        writeString(data, deviceName)

        writeChunk(output, CHUNK_START_EDIT, data.toByteArray())
    }

    /**
     * Write an Image chunk (0x01000500) containing layer PNG data.
     *
     * Real .ipv format (from reverse engineering actual .ipv files):
     * - 8 bytes: timestamp as BE double
     * - 4 bytes: 0xFFFFFFFF (flag: real image data, not preview)
     * - 4 bytes: 0x00000000 (unknown)
     * - 2 bytes: 0x0000 (unknown)
     * - 2 bytes: 0x0002 (unknown, may be related to canvas)
     * - N bytes: PNG data (standard PNG starting with 89 50 4E 47)
     */
    private fun writeImageChunk(
        output: ByteArrayOutputStream,
        timestamp: Double,
        bitmap: Bitmap
    ) {
        // Compress bitmap to PNG
        val pngBaos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngBaos)
        val pngData = pngBaos.toByteArray()

        val data = ByteArrayOutputStream()

        // Timestamp as BE double (8 bytes)
        data.write(doubleToBytesBE(timestamp))

        // Flags: 0xFFFFFFFF = real image data
        data.write(intToBytesBE(-1))

        // Unknown: 0x00000000
        data.write(intToBytesBE(0))

        // Unknown: 2 bytes
        data.write(0x00)
        data.write(0x00)

        // Unknown: 2 bytes (0x0002 in real files)
        data.write(0x00)
        data.write(0x02)

        // PNG data (standard PNG)
        data.write(pngData)

        writeChunk(output, CHUNK_IMAGE, data.toByteArray())
    }

    /**
     * Write a MetaInfo chunk (0x01000600) containing session metadata.
     * Data format:
     * - 8 bytes: timestamp as BE double
     * - String: artwork name
     */
    private fun writeMetaInfoChunk(
        output: ByteArrayOutputStream,
        timestamp: Double,
        artworkName: String
    ) {
        val data = ByteArrayOutputStream()

        // Timestamp as BE double (8 bytes)
        data.write(doubleToBytesBE(timestamp))

        // Artwork name string
        writeString(data, artworkName)

        writeChunk(output, CHUNK_META_INFO, data.toByteArray())
    }

    /**
     * Write an ArtInfoSub chunk (0x30000e04) containing the artwork title.
     * Data format:
     * - String: artwork title
     */
    private fun writeArtInfoSubChunk(
        output: ByteArrayOutputStream,
        artworkName: String
    ) {
        val data = ByteArrayOutputStream()

        // Artwork title string
        writeString(data, artworkName)

        writeChunk(output, CHUNK_ART_INFO_SUB, data.toByteArray())
    }

    /**
     * Write a string in .ipv format: BE uint16 length + UTF-8 bytes.
     */
    private fun writeString(output: ByteArrayOutputStream, value: String) {
        val utf8Bytes = value.toByteArray(Charsets.UTF_8)
        // Length as BE u16
        output.write(((utf8Bytes.size shr 8) and 0xFF).toByte().toInt())
        output.write((utf8Bytes.size and 0xFF).toByte().toInt())
        // UTF-8 data
        output.write(utf8Bytes)
    }

    /**
     * Convert an Int to 4 bytes big-endian.
     */
    private fun intToBytesBE(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    /**
     * Convert a Double to 8 bytes big-endian.
     */
    private fun doubleToBytesBE(value: Double): ByteArray {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putDouble(value)
        return buffer.array()
    }
}
