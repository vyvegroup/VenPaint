package com.venpaint.app.brush

import android.graphics.Color

data class Brush(
    var type: BrushType = BrushType.PEN,
    var size: Float = 10f,
    var opacity: Float = 1.0f,
    var hardness: Float = 0.8f,
    var color: Int = Color.BLACK,
    var spacing: Float = 0.1f,
    var name: String = "Custom Brush",
    var scatter: Float = 0f,
    var density: Float = 1.0f,
    var angle: Float = 0f,
    var jitter: Float = 0f
) {
    companion object {
        fun default(): Brush = Brush()

        fun forType(type: BrushType): Brush = when (type) {
            BrushType.PENCIL -> Brush(type = type, size = 3f, opacity = 0.9f, hardness = 0.3f, spacing = 0.05f, name = "Pencil")
            BrushType.PEN -> Brush(type = type, size = 10f, opacity = 1.0f, hardness = 1.0f, spacing = 0.1f, name = "Pen")
            BrushType.FLAT_BRUSH -> Brush(type = type, size = 15f, opacity = 0.9f, hardness = 0.6f, spacing = 0.08f, name = "Flat Brush")
            BrushType.ROUND_BRUSH -> Brush(type = type, size = 15f, opacity = 0.9f, hardness = 0.8f, spacing = 0.1f, name = "Round Brush")
            BrushType.AIRBRUSH -> Brush(type = type, size = 30f, opacity = 0.3f, hardness = 0.0f, spacing = 0.05f, density = 0.5f, name = "Airbrush")
            BrushType.WATERCOLOR -> Brush(type = type, size = 20f, opacity = 0.4f, hardness = 0.0f, spacing = 0.02f, scatter = 5f, name = "Watercolor")
            BrushType.CRAYON -> Brush(type = type, size = 8f, opacity = 0.8f, hardness = 0.1f, spacing = 0.03f, scatter = 2f, jitter = 3f, name = "Crayon")
            BrushType.OIL_PASTEL -> Brush(type = type, size = 12f, opacity = 0.7f, hardness = 0.2f, spacing = 0.04f, scatter = 3f, name = "Oil Pastel")
            BrushType.MARKER -> Brush(type = type, size = 18f, opacity = 0.5f, hardness = 0.5f, spacing = 0.06f, name = "Marker")
            BrushType.CHARCOAL -> Brush(type = type, size = 10f, opacity = 0.7f, hardness = 0.0f, spacing = 0.02f, scatter = 4f, jitter = 5f, name = "Charcoal")
            BrushType.SMUDGE -> Brush(type = type, size = 20f, opacity = 0.8f, hardness = 0.5f, spacing = 0.05f, name = "Smudge")
            BrushType.FINGER -> Brush(type = type, size = 25f, opacity = 0.6f, hardness = 0.0f, spacing = 0.08f, name = "Finger")
            BrushType.PIXEL_PEN -> Brush(type = type, size = 4f, opacity = 1.0f, hardness = 1.0f, spacing = 0.0f, name = "Pixel Pen")
            BrushType.SYMMETRY -> Brush(type = type, size = 10f, opacity = 1.0f, hardness = 0.8f, spacing = 0.1f, name = "Symmetry")
            BrushType.ERASER -> Brush(type = type, size = 20f, opacity = 1.0f, hardness = 1.0f, spacing = 0.1f, color = Color.TRANSPARENT, name = "Eraser")
            BrushType.FILL -> Brush(type = type, size = 10f, opacity = 1.0f, hardness = 1.0f, spacing = 0.0f, name = "Fill")
            BrushType.EYEDROPPER -> Brush(type = type, size = 5f, opacity = 1.0f, hardness = 1.0f, spacing = 0.0f, name = "Eyedropper")
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.name,
        "size" to size,
        "opacity" to opacity,
        "hardness" to hardness,
        "color" to color,
        "spacing" to spacing,
        "name" to name,
        "scatter" to scatter,
        "density" to density,
        "angle" to angle,
        "jitter" to jitter
    )

    fun companionFromMap(map: Map<String, Any>): Brush {
        val brush = Brush()
        (map["type"] as? String)?.let { BrushType.fromName(it) }?.let { brush.type = it }
        (map["size"] as? Number)?.let { brush.size = it.toFloat() }
        (map["opacity"] as? Number)?.let { brush.opacity = it.toFloat() }
        (map["hardness"] as? Number)?.let { brush.hardness = it.toFloat() }
        (map["color"] as? Number)?.let { brush.color = it.toInt() }
        (map["spacing"] as? Number)?.let { brush.spacing = it.toFloat() }
        (map["name"] as? String)?.let { brush.name = it }
        (map["scatter"] as? Number)?.let { brush.scatter = it.toFloat() }
        (map["density"] as? Number)?.let { brush.density = it.toFloat() }
        (map["angle"] as? Number)?.let { brush.angle = it.toFloat() }
        (map["jitter"] as? Number)?.let { brush.jitter = it.toFloat() }
        return brush
    }

    fun clone(): Brush = copy()
}
