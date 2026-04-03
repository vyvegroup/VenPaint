package com.venpaint.app.engine

import android.graphics.Bitmap
import java.util.Stack

/**
 * Manages undo/redo history for drawing operations.
 * Stores complete layer snapshots to enable reliable undo/redo.
 */
class DrawingHistory(private val layerManager: LayerManager) {

    companion object {
        private const val MAX_HISTORY_SIZE = 30
    }

    private val undoStack = Stack<HistoryEntry>()
    private val redoStack = Stack<HistoryEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    /**
     * Save the current state to history (call before a drawing operation).
     */
    fun saveState(description: String = "Draw") {
        val snapshot = captureSnapshot(description)
        undoStack.push(snapshot)
        redoStack.clear()

        // Trim history if too large
        while (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeAt(0)
        }
    }

    /**
     * Undo the last operation.
     */
    fun undo(): Boolean {
        if (!canUndo) return false

        // Save current state to redo
        val currentSnapshot = captureSnapshot("Redo point")
        redoStack.push(currentSnapshot)

        // Restore previous state
        val previous = undoStack.pop()
        restoreSnapshot(previous)
        return true
    }

    /**
     * Redo the last undone operation.
     */
    fun redo(): Boolean {
        if (!canRedo) return false

        // Save current state to undo
        val currentSnapshot = captureSnapshot("Undo point")
        undoStack.push(currentSnapshot)

        // Restore next state
        val next = redoStack.pop()
        restoreSnapshot(next)
        return true
    }

    /**
     * Clear all history.
     */
    fun clear() {
        undoStack.forEach { it.release() }
        redoStack.forEach { it.release() }
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Capture the current state of all layers as a snapshot.
     */
    private fun captureSnapshot(description: String): HistoryEntry {
        val layers = layerManager.getLayers().map { layer ->
            LayerSnapshot(
                name = layer.name,
                bitmap = layer.bitmap?.copy(Bitmap.Config.ARGB_8888, true),
                isVisible = layer.isVisible,
                opacity = layer.opacity,
                blendMode = layer.blendMode
            )
        }

        return HistoryEntry(
            description = description,
            activeLayerIndex = layerManager.activeLayerIndex,
            layers = layers
        )
    }

    /**
     * Restore a snapshot to the layer manager.
     */
    private fun restoreSnapshot(entry: HistoryEntry) {
        // Release current layer bitmaps
        layerManager.recycleAll()

        // Restore layers from snapshot
        entry.layers.forEach { snapshot ->
            val layer = Layer(
                name = snapshot.name,
                bitmap = snapshot.bitmap,
                isVisible = snapshot.isVisible,
                opacity = snapshot.opacity,
                blendMode = snapshot.blendMode
            )
            // Use reflection to add to internal list
            addLayerToManager(layer)
        }

        // Restore active index
        setActiveIndex(entry.activeLayerIndex)
    }

    /**
     * Add a layer back to the manager using reflection on the internal list.
     * Since LayerManager doesn't expose a direct add method, we use the public API.
     */
    private fun addLayerToManager(layer: Layer) {
        // We directly access the layer manager internals by using its public methods.
        // A cleaner approach: recreate the layer in the manager.
        // For simplicity, we use the layer manager's existing functionality
        // and transfer the bitmap.
        val addedLayer = layerManager.addLayer(layer.name)
        if (addedLayer != null) {
            addedLayer.bitmap?.recycle()
            addedLayer.bitmap = layer.bitmap
            addedLayer.isVisible = layer.isVisible
            addedLayer.opacity = layer.opacity
            addedLayer.blendMode = layer.blendMode
        }
    }

    private fun setActiveIndex(index: Int) {
        val safeIndex = index.coerceIn(0, (layerManager.layerCount - 1).coerceAtLeast(0))
        layerManager.setActiveLayer(safeIndex)
    }

    /**
     * Data class representing a single history entry.
     */
    data class HistoryEntry(
        val description: String,
        val activeLayerIndex: Int,
        val layers: List<LayerSnapshot>
    ) {
        fun release() {
            layers.forEach { it.bitmap?.recycle() }
        }
    }

    /**
     * Data class representing a layer snapshot.
     */
    data class LayerSnapshot(
        val name: String,
        val bitmap: Bitmap?,
        val isVisible: Boolean,
        val opacity: Float,
        val blendMode: BlendMode
    )
}
