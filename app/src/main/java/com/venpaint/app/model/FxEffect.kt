package com.venpaint.app.model

enum class FxCategory {
    BLUR, NOISE, COLOR_ADJUST, SHARPEN, DISTORT, STYLIZE, LIGHT
}

data class FxEffect(
    val id: String,
    val name: String,
    val category: FxCategory,
    val description: String = "",
    val parameters: List<FxParameter> = emptyList()
)

data class FxParameter(
    val name: String,
    val key: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val step: Float = 1f
)

object FxPresets {
    val effects: List<FxEffect> = listOf(
        FxEffect("gaussian_blur", "Gaussian Blur", FxCategory.BLUR, "Blur image with Gaussian filter",
            listOf(FxParameter("Radius", "radius", 0f, 50f, 5f, 0.5f))),
        FxEffect("motion_blur", "Motion Blur", FxCategory.BLUR, "Directional motion blur",
            listOf(FxParameter("Radius", "radius", 0f, 50f, 5f, 0.5f),
                FxParameter("Angle", "angle", 0f, 360f, 0f, 1f))),
        FxEffect("radial_blur", "Radial Blur", FxCategory.BLUR, "Circular radial blur",
            listOf(FxParameter("Radius", "radius", 0f, 50f, 5f, 0.5f))),
        FxEffect("sharpen", "Sharpen", FxCategory.SHARPEN, "Sharpen image details",
            listOf(FxParameter("Amount", "amount", 0f, 100f, 50f, 1f))),
        FxEffect("unsharp_mask", "Unsharp Mask", FxCategory.SHARPEN, "Sharpen with unsharp mask",
            listOf(FxParameter("Amount", "amount", 0f, 100f, 50f, 1f),
                FxParameter("Radius", "radius", 0f, 10f, 1f, 0.1f))),
        FxEffect("brightness_contrast", "Brightness/Contrast", FxCategory.COLOR_ADJUST, "Adjust brightness and contrast",
            listOf(FxParameter("Brightness", "brightness", -100f, 100f, 0f, 1f),
                FxParameter("Contrast", "contrast", -100f, 100f, 0f, 1f))),
        FxEffect("hue_saturation", "Hue/Saturation", FxCategory.COLOR_ADJUST, "Adjust hue and saturation",
            listOf(FxParameter("Hue", "hue", -180f, 180f, 0f, 1f),
                FxParameter("Saturation", "saturation", -100f, 100f, 0f, 1f),
                FxParameter("Lightness", "lightness", -100f, 100f, 0f, 1f))),
        FxEffect("color_balance", "Color Balance", FxCategory.COLOR_ADJUST, "Adjust color balance",
            listOf(FxParameter("Shadows R", "shadowsR", -100f, 100f, 0f),
                FxParameter("Shadows G", "shadowsG", -100f, 100f, 0f),
                FxParameter("Shadows B", "shadowsB", -100f, 100f, 0f),
                FxParameter("Midtones R", "midR", -100f, 100f, 0f),
                FxParameter("Midtones G", "midG", -100f, 100f, 0f),
                FxParameter("Midtones B", "midB", -100f, 100f, 0f))),
        FxEffect("invert", "Invert Colors", FxCategory.COLOR_ADJUST, "Invert all colors"),
        FxEffect("grayscale", "Grayscale", FxCategory.COLOR_ADJUST, "Convert to grayscale"),
        FxEffect("sepia", "Sepia", FxCategory.STYLIZE, "Apply sepia tone"),
        FxEffect("posterize", "Posterize", FxCategory.STYLIZE, "Reduce color levels",
            listOf(FxParameter("Levels", "levels", 2f, 20f, 4f, 1f))),
        FxEffect("threshold", "Threshold", FxCategory.STYLIZE, "Black and white threshold",
            listOf(FxParameter("Threshold", "threshold", 0f, 255f, 128f, 1f))),
        FxEffect("noise", "Add Noise", FxCategory.NOISE, "Add random noise",
            listOf(FxParameter("Amount", "amount", 0f, 100f, 30f, 1f))),
        FxEffect("pixelate", "Pixelate", FxCategory.DISTORT, "Pixelate image",
            listOf(FxParameter("Size", "size", 1f, 50f, 5f, 1f))),
        FxEffect("vignette", "Vignette", FxCategory.LIGHT, "Add vignette effect",
            listOf(FxParameter("Intensity", "intensity", 0f, 100f, 50f, 1f))),
        FxEffect("opacity", "Opacity", FxCategory.LIGHT, "Adjust layer opacity",
            listOf(FxParameter("Opacity", "opacity", 0f, 100f, 100f, 1f)))
    )
}
