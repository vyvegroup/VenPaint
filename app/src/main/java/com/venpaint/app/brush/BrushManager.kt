package com.venpaint.app.brush

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Manages brush presets: save, load, delete user brushes.
 */
class BrushManager(private val context: Context) {

    companion object {
        private const val PRESETS_DIR = "VenPaint/brushes"
        private const val PRESET_EXTENSION = ".vpbrush"
    }

    private val gson = Gson()
    private val customBrushes = mutableListOf<Brush>()

    init {
        loadCustomBrushes()
    }

    /**
     * Get the built-in brush presets.
     */
    fun getBuiltInPresets(): List<Brush> = BrushType.entries.map { Brush.forType(it) }

    /**
     * Get all custom brushes.
     */
    fun getCustomBrushes(): List<Brush> = customBrushes.toList()

    /**
     * Get all brushes (built-in + custom).
     */
    fun getAllBrushes(): List<Brush> = getBuiltInPresets() + customBrushes

    /**
     * Save a custom brush preset.
     */
    fun saveBrush(brush: Brush, name: String? = null): Boolean {
        return try {
            val presetName = name ?: brush.name
            val brushData = brush.copy(name = presetName)
            val json = gson.toJson(brushData.toMap())

            val dir = File(context.getExternalFilesDir(null), PRESETS_DIR)
            if (!dir.exists()) dir.mkdirs()

            val filename = sanitizeFilename(presetName) + PRESET_EXTENSION
            val file = File(dir, filename)
            FileWriter(file).use { it.write(json) }

            // Update in-memory list
            val existing = customBrushes.find { it.name == presetName }
            if (existing != null) {
                customBrushes[customBrushes.indexOf(existing)] = brushData
            } else {
                customBrushes.add(brushData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete a custom brush preset.
     */
    fun deleteBrush(brush: Brush): Boolean {
        val index = customBrushes.indexOf(brush)
        if (index == -1) return false

        return try {
            val dir = File(context.getExternalFilesDir(null), PRESETS_DIR)
            val filename = sanitizeFilename(brush.name) + PRESET_EXTENSION
            val file = File(dir, filename)
            if (file.exists()) file.delete()

            customBrushes.removeAt(index)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Load custom brushes from storage.
     */
    private fun loadCustomBrushes() {
        customBrushes.clear()
        try {
            val dir = File(context.getExternalFilesDir(null), PRESETS_DIR)
            if (!dir.exists() || !dir.isDirectory) return

            val files = dir.listFiles { f -> f.extension == PRESET_EXTENSION.removePrefix(".") }
            files?.forEach { file ->
                try {
                    val json = FileReader(file).readText()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val map: Map<String, Any> = gson.fromJson(json, type)
                    val brush = Brush().companionFromMap(map)
                    customBrushes.add(brush)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Sanitize a filename by removing special characters.
     */
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}
