package com.venpaint.app.brush

import android.graphics.Color

/**
 * Data model representing a brush with all its parameters.
 */
data class Brush(
    var type: BrushType = BrushType.PEN,
    var size: Float = 10f,
    var opacity: Float = 1.0f,
    var hardness: Float = 0.8f,
    var color: Int = Color.BLACK,
    var spacing: Float = 0.1f,
    var name: String = "Custom Brush"
) {
    companion object {
        /**
         * Create a default brush.
         */
        fun default(): Brush = Brush()

        /**
         * Create a brush of a specific type with sensible defaults.
         */
        fun forType(type: BrushType): Brush = when (type) {
            BrushType.PENCIL -> Brush(
                type = type, size = 3f, opacity = 0.9f, hardness = 0.3f,
                spacing = 0.05f, name = "Pencil"
            )
            BrushType.PEN -> Brush(
                type = type, size = 10f, opacity = 1.0f, hardness = 1.0f,
                spacing = 0.1f, name = "Pen"
            )
            BrushType.AIRBRUSH -> Brush(
                type = type, size = 30f, opacity = 0.3f, hardness = 0.0f,
                spacing = 0.05f, name = "Airbrush"
            )
            BrushType.WATERCOLOR -> Brush(
                type = type, size = 20f, opacity = 0.4f, hardness = 0.0f,
                spacing = 0.02f, name = "Watercolor"
            )
            BrushType.FLAT_BRUSH -> Brush(
                type = type, size = 15f, opacity = 0.9f, hardness = 0.6f,
                spacing = 0.08f, name = "Flat Brush"
            )
            BrushType.ROUND_BRUSH -> Brush(
                type = type, size = 15f, opacity = 0.9f, hardness = 0.8f,
                spacing = 0.1f, name = "Round Brush"
            )
            BrushType.CRAYON -> Brush(
                type = type, size = 8f, opacity = 0.8f, hardness = 0.1f,
                spacing = 0.03f, name = "Crayon"
            )
            BrushType.ERASER -> Brush(
                type = type, size = 20f, opacity = 1.0f, hardness = 1.0f,
                spacing = 0.1f, color = Color.TRANSPARENT, name = "Eraser"
            )
        }
    }

    /**
     * Convert to a serializable map for JSON export.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.name,
        "size" to size,
        "opacity" to opacity,
        "hardness" to hardness,
        "color" to color,
        "spacing" to spacing,
        "name" to name
    )

    /**
     * Create a brush from a map (e.g., parsed JSON).
     */
    fun companionFromMap(map: Map<String, Any>): Brush {
        val brush = Brush()
        (map["type"] as? String)?.let { BrushType.fromName(it) }?.let { brush.type = it }
        (map["size"] as? Number)?.let { brush.size = it.toFloat() }
        (map["opacity"] as? Number)?.let { brush.opacity = it.toFloat() }
        (map["hardness"] as? Number)?.let { brush.hardness = it.toFloat() }
        (map["color"] as? Number)?.let { brush.color = it.toInt() }
        (map["spacing"] as? Number)?.let { brush.spacing = it.toFloat() }
        (map["name"] as? String)?.let { brush.name = it }
        return brush
    }

    /**
     * Clone this brush.
     */
    fun clone(): Brush = copy()
}
