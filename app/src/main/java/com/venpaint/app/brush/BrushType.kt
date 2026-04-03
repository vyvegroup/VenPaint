package com.venpaint.app.brush

enum class BrushType(val displayName: String, val index: Int) {
    PENCIL("Pencil", 0),
    PEN("Pen", 1),
    FLAT_BRUSH("Flat Brush", 2),
    ROUND_BRUSH("Round Brush", 3),
    AIRBRUSH("Airbrush", 4),
    WATERCOLOR("Watercolor", 5),
    CRAYON("Crayon", 6),
    OIL_PASTEL("Oil Pastel", 7),
    MARKER("Marker", 8),
    CHARCOAL("Charcoal", 9),
    SMUDGE("Smudge", 10),
    FINGER("Finger", 11),
    PIXEL_PEN("Pixel Pen", 12),
    SYMMETRY("Symmetry", 13),
    ERASER("Eraser", 20),
    FILL("Fill", 21),
    EYEDROPPER("Eyedropper", 22);

    companion object {
        private val byIndex = entries.associateBy { it.index }
        private val byName = entries.associateBy { it.name }

        fun fromIndex(index: Int): BrushType = byIndex[index] ?: PEN

        fun fromName(name: String): BrushType = byName[name] ?: PEN

        val drawingTypes: List<BrushType>
            get() = entries.filter { it.index < 20 }

        val allTypes: List<BrushType>
            get() = entries.toList()
    }
}
