package com.venpaint.app.brush

/**
 * Enum of all supported brush types in VenPaint.
 */
enum class BrushType(val displayName: String) {
    PENCIL("Pencil"),
    PEN("Pen"),
    AIRBRUSH("Airbrush"),
    WATERCOLOR("Watercolor"),
    FLAT_BRUSH("Flat Brush"),
    ROUND_BRUSH("Round Brush"),
    CRAYON("Crayon"),
    ERASER("Eraser");

    companion object {
        fun fromIndex(index: Int): BrushType = entries.getOrElse(index) { PEN }
        fun fromName(name: String): BrushType? = entries.find { it.name.equals(name, ignoreCase = true) }
    }
}
