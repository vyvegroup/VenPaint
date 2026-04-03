package com.venpaint.app.io

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.venpaint.app.engine.BlendMode
import com.venpaint.app.engine.Layer
import com.venpaint.app.engine.LayerManager
import com.venpaint.app.util.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Saves and loads VenPaint projects.
 * Project format is a ZIP file containing:
 * - project.json: metadata and layer info
 * - layer_0.png, layer_1.png, ...: layer bitmaps
 */
class ProjectSaver(private val context: Context) {

    companion object {
        private const val TAG = "ProjectSaver"
        private const val PROJECT_DIR = "VenPaint/Projects"
        const val PROJECT_EXTENSION = ".ipv"
        private const val LEGACY_EXTENSION = ".vpp"
        private const val METADATA_FILE = "project.json"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Project metadata.
     */
    data class ProjectMetadata(
        val name: String,
        val width: Int,
        val height: Int,
        val layerCount: Int,
        val activeLayerIndex: Int,
        val layers: List<LayerMetadata>
    )

    data class LayerMetadata(
        val index: Int,
        val name: String,
        val opacity: Float,
        val blendMode: String,
        val isVisible: Boolean,
        val filename: String
    )

    /**
     * Save the current project in .ipv format (default).
     */
    fun saveProject(
        layerManager: LayerManager,
        projectName: String = "Untitled"
    ): File? {
        return saveAsIpv(layerManager, projectName)
    }

    /**
     * Save the current project as .ipv (ibisPaint binary chunk format).
     */
    fun saveAsIpv(
        layerManager: LayerManager,
        projectName: String = "Untitled"
    ): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), PROJECT_DIR)
            if (!dir.exists()) dir.mkdirs()

            val filename = projectName.sanitize() + PROJECT_EXTENSION
            val file = File(dir, filename)

            val writer = IpvFormatWriter()
            val result = writer.writeIpvFile(layerManager, file, projectName)

            if (result.success) {
                Log.d(TAG, "Project saved as .ipv: ${file.absolutePath}")
                file
            } else {
                Log.e(TAG, "Failed to save as .ipv: ${result.error}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save project as .ipv", e)
            null
        }
    }

    /**
     * Save the current project as .vpp (legacy ZIP format).
     */
    fun saveAsVpp(
        layerManager: LayerManager,
        projectName: String = "Untitled"
    ): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), PROJECT_DIR)
            if (!dir.exists()) dir.mkdirs()

            val filename = projectName.sanitize() + LEGACY_EXTENSION
            val file = File(dir, filename)

            ZipOutputStream(FileOutputStream(file)).use { zipOut ->
                // Write metadata
                val metadata = buildMetadata(layerManager, projectName)
                val metadataJson = gson.toJson(metadata)
                addZipEntry(zipOut, METADATA_FILE, metadataJson.toByteArray())

                // Write layer bitmaps
                val layers = layerManager.getLayers()
                for ((index, layer) in layers.withIndex()) {
                    val layerFilename = "layer_$index.png"
                    val bitmap = layer.bitmap
                    if (bitmap != null && !bitmap.isRecycled) {
                        val baos = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        addZipEntry(zipOut, layerFilename, baos.toByteArray())
                    }
                }
            }

            Log.d(TAG, "Project saved as .vpp: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save project as .vpp", e)
            null
        }
    }

    /**
     * Load a project from a file.
     * Supports both .ipv (binary chunk format) and .vpp (legacy ZIP format).
     */
    fun loadProject(file: File, layerManager: LayerManager): Boolean {
        return try {
            val bytes = FileUtils.readBytes(file) ?: return false
            loadProjectFromBytes(bytes, layerManager)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load project", e)
            false
        }
    }

    /**
     * Load a project from bytes.
     * Automatically detects format: .ipv binary chunks vs .vpp ZIP.
     */
    fun loadProjectFromBytes(bytes: ByteArray, layerManager: LayerManager): Boolean {
        // Try loading as .ipv binary chunk format first
        val ipvImporter = IpvImporter(context)
        val ipvResult = ipvImporter.importFromBytes(bytes)
        if (ipvResult.success) {
            ipvImporter.applyToLayerManager(ipvResult, layerManager)
            Log.d(TAG, "Loaded project as .ipv binary format (${ipvResult.layers.size} layers)")
            return true
        }

        // Fall back to legacy .vpp ZIP format
        return try {
            var metadata: ProjectMetadata? = null
            val layerBitmaps = mutableMapOf<Int, Bitmap>()

            java.util.zip.ZipInputStream(bytes.inputStream()).use { zipIn ->
                var entry: java.util.zip.ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    when {
                        name == METADATA_FILE -> {
                            val content = BufferedReader(InputStreamReader(zipIn)).readText()
                            metadata = gson.fromJson(content, ProjectMetadata::class.java)
                        }
                        name.startsWith("layer_") && name.endsWith(".png") -> {
                            val indexStr = name.removePrefix("layer_").removeSuffix(".png")
                            val index = indexStr.toIntOrNull() ?: continue
                            val bitmap = android.graphics.BitmapFactory.decodeStream(zipIn)
                            if (bitmap != null) {
                                layerBitmaps[index] = bitmap
                            }
                        }
                    }
                }
            }

            val md = metadata ?: return false

            // Clear existing layers
            layerManager.recycleAll()
            layerManager.resize(md.width, md.height)

            // Recreate layers
            for (layerMeta in md.layers) {
                val layer = layerManager.addLayer(layerMeta.name) ?: break
                layer.opacity = layerMeta.opacity
                layer.isVisible = layerMeta.isVisible
                layer.blendMode = BlendMode.entries.find { it.name == layerMeta.blendMode } ?: BlendMode.NORMAL

                val bitmap = layerBitmaps[layerMeta.index]
                if (bitmap != null) {
                    layer.bitmap?.recycle()
                    layer.bitmap = bitmap
                }
            }

            // Restore active layer
            if (md.activeLayerIndex in 0 until layerManager.layerCount) {
                layerManager.setActiveLayer(md.activeLayerIndex)
            }

            Log.d(TAG, "Loaded project as .vpp ZIP format (${md.layers.size} layers)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load project from bytes", e)
            false
        }
    }

    /**
     * Build project metadata from the current layer manager state.
     */
    private fun buildMetadata(layerManager: LayerManager, projectName: String): ProjectMetadata {
        val layers = layerManager.getLayers().mapIndexed { index, layer ->
            LayerMetadata(
                index = index,
                name = layer.name,
                opacity = layer.opacity,
                blendMode = layer.blendMode.name,
                isVisible = layer.isVisible,
                filename = "layer_$index.png"
            )
        }

        // Get canvas dimensions from the first layer
        val firstBitmap = layerManager.getLayer(0)?.bitmap
        val width = firstBitmap?.width ?: 1080
        val height = firstBitmap?.height ?: 1920

        return ProjectMetadata(
            name = projectName,
            width = width,
            height = height,
            layerCount = layers.size,
            activeLayerIndex = layerManager.activeLayerIndex,
            layers = layers
        )
    }

    /**
     * Add a file entry to a ZIP output stream.
     */
    private fun addZipEntry(zipOut: ZipOutputStream, filename: String, data: ByteArray) {
        val entry = ZipEntry(filename)
        zipOut.putNextEntry(entry)
        zipOut.write(data)
        zipOut.closeEntry()
    }

    /**
     * Get a list of saved projects (both .ipv and .vpp formats).
     */
    fun getSavedProjects(): List<File> {
        val dir = File(context.getExternalFilesDir(null), PROJECT_DIR)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles { f ->
            val ext = f.extension.lowercase()
            ext == PROJECT_EXTENSION.removePrefix(".") ||
            ext == LEGACY_EXTENSION.removePrefix(".")
        }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Delete a saved project.
     */
    fun deleteProject(file: File): Boolean {
        return file.exists() && file.delete()
    }
}

/**
 * Sanitize a string for use as a filename.
 */
private fun String.sanitize(): String {
    return this.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
}
