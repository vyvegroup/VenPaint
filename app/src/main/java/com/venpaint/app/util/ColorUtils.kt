package com.venpaint.app.util

import android.graphics.Color

/**
 * Utility functions for color manipulation and conversion.
 */
object ColorUtils {

    /**
     * Convert ARGB int to hex string.
     */
    fun colorToHex(color: Int): String {
        return String.format("#%08X", color)
    }

    /**
     * Parse hex string to ARGB int color.
     */
    fun hexToColor(hex: String): Int {
        val cleaned = hex.removePrefix("#")
        return when (cleaned.length) {
            6 -> Color.parseColor("#$cleaned")
            8 -> {
                val a = cleaned.substring(0, 2).toInt(16)
                val r = cleaned.substring(2, 4).toInt(16)
                val g = cleaned.substring(4, 6).toInt(16)
                val b = cleaned.substring(6, 8).toInt(16)
                Color.argb(a, r, g, b)
            }
            else -> Color.BLACK
        }
    }

    /**
     * Get the alpha component (0-255).
     */
    fun getAlpha(color: Int): Int = Color.alpha(color)

    /**
     * Get the red component (0-255).
     */
    fun getRed(color: Int): Int = Color.red(color)

    /**
     * Get the green component (0-255).
     */
    fun getGreen(color: Int): Int = Color.green(color)

    /**
     * Get the blue component (0-255).
     */
    fun getBlue(color: Int): Int = Color.blue(color)

    /**
     * Set alpha on a color.
     */
    fun withAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    /**
     * Set alpha on a color (0-255).
     */
    fun withAlphaInt(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    /**
     * Blend two colors together.
     */
    fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        return Color.argb(a.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /**
     * Lighten a color by a factor (0 = no change, 1 = white).
     */
    fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * factor).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * factor).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * factor).toInt()
        return Color.argb(Color.alpha(color), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /**
     * Darken a color by a factor (0 = no change, 1 = black).
     */
    fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * (1f - factor)).toInt()
        val g = (Color.green(color) * (1f - factor)).toInt()
        val b = (Color.blue(color) * (1f - factor)).toInt()
        return Color.argb(Color.alpha(color), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /**
     * Convert HSV (0-360, 0-1, 0-1) to ARGB int.
     */
    fun hsvToColor(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        return Color.HSVToColor(alpha, floatArrayOf(h, s, v))
    }

    /**
     * Convert ARGB int to HSV array [h, s, v].
     */
    fun colorToHsv(color: Int): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv
    }

    /**
     * Get a contrasting text color (black or white) for the given background.
     */
    fun getContrastTextColor(bgColor: Int): Int {
        val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    /**
     * Default color palette for the color picker.
     */
    val defaultPalette: List<Int> = listOf(
        Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.WHITE,
        Color.RED, 0xFFFF6347.toInt(), 0xFFFF8C00.toInt(), Color.YELLOW, 0xFFFFD700.toInt(),
        Color.GREEN, 0xFF32CD32.toInt(), 0xFF00CED1.toInt(), Color.BLUE, 0xFF4169E1.toInt(),
        0xFF8A2BE2.toInt(), Color.MAGENTA, 0xFFFF69B4.toInt(), 0xFF8B4513.toInt(), 0xFFF5DEB3.toInt()
    )
}
