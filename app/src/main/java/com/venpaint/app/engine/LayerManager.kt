package com.venpaint.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/**
 * Manages a stack of layers and handles operations like add, delete, reorder, merge, etc.
 */
class LayerManager(private var canvasWidth: Int, private var canvasHeight: Int) {

    companion object {
        const val MAX_LAYERS = 100
    }

    private val layers = mutableListOf<Layer>()
    var activeLayerIndex: Int = 0
        private set

    val layerCount: Int get() = layers.size

    val activeLayer: Layer?
        get() = layers.getOrNull(activeLayerIndex)

    /**
     * Initialize with a default transparent layer.
     */
    fun initialize() {
        layers.clear()
        activeLayerIndex = 0
        addLayer("Background")
    }

    /**
     * Add a new layer on top.
     */
    fun addLayer(name: String = "Layer ${layers.size + 1}"): Layer? {
        if (layers.size >= MAX_LAYERS) return null

        val layer = Layer(
            name = name,
            isVisible = true,
            opacity = 1.0f,
            blendMode = BlendMode.NORMAL
        )
        layer.createBitmap(canvasWidth, canvasHeight)
        layers.add(layer)
        activeLayerIndex = layers.size - 1
        return layer
    }

    /**
     * Add a layer at a specific position.
     */
    fun addLayerAt(index: Int, name: String = "Layer"): Layer? {
        if (layers.size >= MAX_LAYERS) return null
        val safeIndex = index.coerceIn(0, layers.size)

        val layer = Layer(name = name)
        layer.createBitmap(canvasWidth, canvasHeight)
        layers.add(safeIndex, layer)
        activeLayerIndex = safeIndex
        return layer
    }

    /**
     * Remove a layer by index.
     */
    fun removeLayer(index: Int): Boolean {
        if (layers.isEmpty()) return false
        if (index !in layers.indices) return false

        layers[index].recycle()
        layers.removeAt(index)

        if (layers.isEmpty()) {
            addLayer("Background")
            return true
        }

        // Adjust active layer index
        activeLayerIndex = when {
            activeLayerIndex >= layers.size -> layers.size - 1
            activeLayerIndex > index -> activeLayerIndex - 1
            activeLayerIndex == index && activeLayerIndex >= layers.size -> layers.size - 1
            else -> activeLayerIndex
        }
        return true
    }

    /**
     * Remove the active layer.
     */
    fun removeActiveLayer(): Boolean {
        return removeLayer(activeLayerIndex)
    }

    /**
     * Move a layer from one position to another.
     */
    fun moveLayer(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in layers.indices || toIndex !in layers.indices) return false
        if (fromIndex == toIndex) return false

        val layer = layers.removeAt(fromIndex)
        layers.add(toIndex, layer)

        // Update active layer index
        when {
            activeLayerIndex == fromIndex -> activeLayerIndex = toIndex
            fromIndex < activeLayerIndex && toIndex >= activeLayerIndex -> activeLayerIndex--
            fromIndex > activeLayerIndex && toIndex <= activeLayerIndex -> activeLayerIndex++
        }
        return true
    }

    /**
     * Duplicate the active layer.
     */
    fun duplicateActiveLayer(): Layer? {
        val src = activeLayer ?: return null
        val dup = src.duplicate()

        val insertIndex = (activeLayerIndex + 1).coerceAtMost(layers.size)
        layers.add(insertIndex, dup)
        activeLayerIndex = insertIndex
        return dup
    }

    /**
     * Merge the active layer with the one below it.
     */
    fun mergeDown(): Boolean {
        if (activeLayerIndex <= 0) return false

        val upper = layers[activeLayerIndex]
        val lower = layers[activeLayerIndex - 1]

        lower.mergeFrom(upper)
        upper.recycle()
        layers.removeAt(activeLayerIndex)
        activeLayerIndex--

        return true
    }

    /**
     * Flatten all layers into a single layer.
     */
    fun flattenLayers() {
        if (layers.size <= 1) return

        val merged = getFlattenedBitmap() ?: return
        layers.forEach { it.recycle() }
        layers.clear()

        val layer = Layer(name = "Background")
        layer.bitmap = merged
        layers.add(layer)
        activeLayerIndex = 0
    }

    /**
     * Get a layer at a given index.
     */
    fun getLayer(index: Int): Layer? = layers.getOrNull(index)

    /**
     * Get the layer list (read-only copy of references).
     */
    fun getLayers(): List<Layer> = layers.toList()

    /**
     * Toggle visibility of a layer.
     */
    fun toggleVisibility(index: Int) {
        if (index in layers.indices) {
            layers[index].isVisible = !layers[index].isVisible
        }
    }

    /**
     * Toggle visibility of the active layer.
     */
    fun toggleActiveVisibility() {
        toggleVisibility(activeLayerIndex)
    }

    /**
     * Set the active layer index.
     */
    fun setActiveLayer(index: Int) {
        if (index in layers.indices) {
            activeLayerIndex = index
        }
    }

    /**
     * Set the opacity of a layer.
     */
    fun setLayerOpacity(index: Int, opacity: Float) {
        if (index in layers.indices) {
            layers[index].opacity = opacity.coerceIn(0f, 1f)
        }
    }

    /**
     * Set the blend mode of a layer.
     */
    fun setLayerBlendMode(index: Int, blendMode: BlendMode) {
        if (index in layers.indices) {
            layers[index].blendMode = blendMode
        }
    }

    /**
     * Get a flattened bitmap of all visible layers.
     */
    fun getFlattenedBitmap(): Bitmap? {
        if (layers.isEmpty()) return null

        val result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)

        for (layer in layers) {
            layer.drawOn(canvas)
        }

        return result
    }

    /**
     * Render all visible layers onto the given canvas.
     */
    fun renderLayers(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT)
        for (layer in layers) {
            layer.drawOn(canvas)
        }
    }

    /**
     * Clear the active layer.
     */
    fun clearActiveLayer() {
        activeLayer?.clear()
    }

    /**
     * Rename a layer.
     */
    fun renameLayer(index: Int, name: String) {
        if (index in layers.indices) {
            layers[index].name = name
        }
    }

    /**
     * Resize the canvas and all layers.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        canvasWidth = newWidth
        canvasHeight = newHeight

        for (layer in layers) {
            val oldBitmap = layer.bitmap
            if (oldBitmap != null && !oldBitmap.isRecycled) {
                val newBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(newBitmap)
                canvas.drawBitmap(oldBitmap, 0f, 0f, null)
                layer.bitmap = newBitmap
                oldBitmap.recycle()
            } else {
                layer.createBitmap(newWidth, newHeight)
            }
        }
    }

    /**
     * Recycle all layer bitmaps.
     */
    fun recycleAll() {
        layers.forEach { it.recycle() }
        layers.clear()
    }

    /**
     * Check if we can add more layers.
     */
    fun canAddLayer(): Boolean = layers.size < MAX_LAYERS
}
