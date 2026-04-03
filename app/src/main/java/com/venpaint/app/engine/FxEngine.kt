package com.venpaint.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.ScriptIntrinsicColorMatrix
import android.content.Context
import com.venpaint.app.model.FxEffect
import com.venpaint.app.model.FxPresets
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class FxEngine(private val context: Context) {

    private var renderScript: RenderScript? = null

    private fun getRS(): RenderScript {
        if (renderScript == null) {
            renderScript = RenderScript.create(context)
        }
        return renderScript!!
    }

    fun applyEffect(source: Bitmap, effect: FxEffect, params: Map<String, Float> = emptyMap()): Bitmap {
        val paramValues = mutableMapOf<String, Float>()
        effect.parameters.forEach { p ->
            paramValues[p.key] = params[p.key] ?: p.default
        }

        return when (effect.id) {
            "gaussian_blur" -> applyGaussianBlur(source, paramValues["radius"] ?: 5f)
            "motion_blur" -> applyMotionBlur(source, paramValues["radius"] ?: 5f, paramValues["angle"] ?: 0f)
            "sharpen" -> applySharpen(source, paramValues["amount"] ?: 50f)
            "brightness_contrast" -> applyBrightnessContrast(
                source,
                paramValues["brightness"] ?: 0f,
                paramValues["contrast"] ?: 0f
            )
            "hue_saturation" -> applyHueSaturation(
                source,
                paramValues["hue"] ?: 0f,
                paramValues["saturation"] ?: 0f,
                paramValues["lightness"] ?: 0f
            )
            "invert" -> applyInvert(source)
            "grayscale" -> applyGrayscale(source)
            "sepia" -> applySepia(source)
            "posterize" -> applyPosterize(source, paramValues["levels"] ?: 4f)
            "threshold" -> applyThreshold(source, paramValues["threshold"] ?: 128f)
            "noise" -> applyNoise(source, paramValues["amount"] ?: 30f)
            "pixelate" -> applyPixelate(source, paramValues["size"] ?: 5f)
            "vignette" -> applyVignette(source, paramValues["intensity"] ?: 50f)
            "opacity" -> applyOpacity(source, paramValues["opacity"] ?: 100f)
            "color_balance" -> applyColorBalance(
                source,
                paramValues["shadowsR"] ?: 0f, paramValues["shadowsG"] ?: 0f, paramValues["shadowsB"] ?: 0f,
                paramValues["midR"] ?: 0f, paramValues["midG"] ?: 0f, paramValues["midB"] ?: 0f
            )
            else -> source
        }
    }

    fun applyGaussianBlur(source: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return source
        val rs = getRS()
        val input = Allocation.createFromBitmap(rs, source)
        val output = Allocation.createTyped(rs, input.type)
        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        blur.setRadius(radius.coerceIn(0.1f, 25f))
        blur.setInput(input)
        blur.forEach(output)
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        output.copyTo(result)
        input.destroy()
        output.destroy()
        blur.destroy()
        return result
    }

    fun applyMotionBlur(source: Bitmap, radius: Float, angle: Float): Bitmap {
        if (radius <= 0f) return source
        val rad = Math.toRadians(angle.toDouble())
        val dx = (radius * Math.cos(rad)).toInt()
        val dy = (radius * Math.sin(rad)).toInt()
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            alpha = 255
        }
        val steps = maxOf(1, radius.toInt())
        for (i in steps downTo 0) {
            val frac = i.toFloat() / steps.toFloat()
            paint.alpha = (180 / steps).coerceAtLeast(1)
            canvas.drawBitmap(source, dx * frac, dy * frac, paint)
        }
        paint.alpha = 255
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun applySharpen(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return source
        val factor = amount / 100f
        val kernel = floatArrayOf(
            0f, -factor, 0f,
            -factor, 1f + 4f * factor, -factor,
            0f, -factor, 0f
        )
        return applyConvolution(source, kernel)
    }

    fun applyBrightnessContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val b = brightness
        val c = (contrast / 100f * 255f)
        val factor = (259f * (c + 255f)) / (255f * (259f - c))

        for (i in pixels.indices) {
            var pixel = pixels[i]
            val a = Color.alpha(pixel)
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var bl = Color.blue(pixel)

            r = (factor * (r - 128f + b) + 128f).coerceIn(0f, 255f).toInt()
            g = (factor * (g - 128f + b) + 128f).coerceIn(0f, 255f).toInt()
            bl = (factor * (bl - 128f + b) + 128f).coerceIn(0f, 255f).toInt()

            pixels[i] = Color.argb(a, r, g, bl)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyHueSaturation(source: Bitmap, hue: Float, saturation: Float, lightness: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val hShift = hue / 360f
        val sMult = 1f + saturation / 100f
        val lAdd = lightness / 100f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = Color.alpha(pixel)
            var r = Color.red(pixel) / 255f
            var g = Color.green(pixel) / 255f
            var b = Color.blue(pixel) / 255f

            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min

            var h = when {
                delta == 0f -> 0f
                max == r -> ((g - b) / delta) % 6f
                max == g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            }
            val s = if (max == 0f) 0f else delta / max
            val l = (max + min) / 2f

            var newH = (h + hShift) % 1f
            if (newH < 0) newH += 1f
            var newS = (s * sMult).coerceIn(0f, 1f)
            var newL = (l + lAdd).coerceIn(0f, 1f)

            val c = (1f - abs(2f * newL - 1f)) * newS
            val x = c * (1f - abs((newH * 6f) % 2f - 1f))
            val m = newL - c / 2f

            val (rr, gg, bb) = when {
                newH < 1/6f -> Triple(c, x, 0f)
                newH < 2/6f -> Triple(x, c, 0f)
                newH < 3/6f -> Triple(0f, c, x)
                newH < 4/6f -> Triple(0f, x, c)
                newH < 5/6f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

            pixels[i] = Color.argb(a,
                ((rr + m) * 255f).coerceIn(0f, 255f).toInt(),
                ((gg + m) * 255f).coerceIn(0f, 255f).toInt(),
                ((bb + m) * 255f).coerceIn(0f, 255f).toInt())
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyInvert(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            val r = 255 - Color.red(pixels[i])
            val g = 255 - Color.green(pixels[i])
            val b = 255 - Color.blue(pixels[i])
            pixels[i] = Color.argb(a, r, g, b)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val matrix = android.graphics.ColorMatrix().apply {
            setSaturation(0f)
        }
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun applySepia(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            val sr = minOf(255, (r * 0.393f + g * 0.769f + b * 0.189f).toInt())
            val sg = minOf(255, (r * 0.349f + g * 0.686f + b * 0.168f).toInt())
            val sb = minOf(255, (r * 0.272f + g * 0.534f + b * 0.131f).toInt())
            pixels[i] = Color.argb(a, sr, sg, sb)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyPosterize(source: Bitmap, levels: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val n = levels.coerceIn(2f, 20f)
        val step = 255f / (n - 1f)
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            val r = ((Color.red(pixels[i]) / step).toInt() * step).coerceIn(0f, 255f).toInt()
            val g = ((Color.green(pixels[i]) / step).toInt() * step).coerceIn(0f, 255f).toInt()
            val b = ((Color.blue(pixels[i]) / step).toInt() * step).coerceIn(0f, 255f).toInt()
            pixels[i] = Color.argb(a, r, g, b)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyThreshold(source: Bitmap, threshold: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val t = threshold.coerceIn(0f, 255f)
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            val gray = (r * 0.299f + g * 0.587f + b * 0.114f).toInt()
            val v = if (gray >= t) 255 else 0
            pixels[i] = Color.argb(a, v, v, v)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyNoise(source: Bitmap, amount: Float): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(source.width * source.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val intensity = amount / 100f * 255f
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            var r = Color.red(pixels[i]) + (Random.nextFloat() * intensity - intensity / 2f).toInt()
            var g = Color.green(pixels[i]) + (Random.nextFloat() * intensity - intensity / 2f).toInt()
            var b = Color.blue(pixels[i]) + (Random.nextFloat() * intensity - intensity / 2f).toInt()
            pixels[i] = Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun applyPixelate(source: Bitmap, size: Float): Bitmap {
        if (size <= 1f) return source
        val pixelSize = size.coerceIn(2f, 50f).toInt()
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = false
        }
        canvas.drawBitmap(source, 0f, 0f, null)
        val smallW = (source.width / pixelSize).coerceAtLeast(1)
        val smallH = (source.height / pixelSize).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, smallW, smallH, false)
        val scaledBack = Bitmap.createScaledBitmap(scaled, source.width, source.height, false)
        val pixels = IntArray(source.width * source.height)
        scaledBack.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        scaled.recycle()
        scaledBack.recycle()
        return result
    }

    fun applyVignette(source: Bitmap, intensity: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val cx = source.width / 2f
        val cy = source.height / 2f
        val maxR = sqrt(cx * cx + cy * cy)
        val innerR = maxR * (1f - intensity / 100f * 0.7f)

        val colorArr = longArrayOf(0x00000000L, 0xAA000000L, 0xFF000000L)
        val shader = android.graphics.RadialGradient(cx, cy, innerR, cx, cy, maxR,
            colorArr, null, android.graphics.Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint().apply {
            setShader(shader)
        }
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), paint)
        return result
    }

    fun applyOpacity(source: Bitmap, opacity: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            this.alpha = (opacity / 100f * 255f).coerceIn(0f, 255f).toInt()
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun applyColorBalance(source: Bitmap, sr: Float, sg: Float, sb: Float, mr: Float, mg: Float, mb: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val shadowFactor = 0.3f
        val midFactor = 0.6f
        val highlightFactor = 0.1f

        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            var r = Color.red(pixels[i])
            var g = Color.green(pixels[i])
            var b = Color.blue(pixels[i])

            val lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f

            r += (sr * shadowFactor * (1f - lum) + mr * midFactor + 0f * lum).toInt()
            g += (sg * shadowFactor * (1f - lum) + mg * midFactor + 0f * lum).toInt()
            b += (sb * shadowFactor * (1f - lum) + mb * midFactor + 0f * lum).toInt()

            pixels[i] = Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun applyConvolution(source: Bitmap, kernel: FloatArray): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        val outPixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val w = source.width
        val h = source.height
        val kSize = 3
        val kHalf = kSize / 2

        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0f; var g = 0f; var b = 0f; var a = 0f
                for (ky in 0 until kSize) {
                    for (kx in 0 until kSize) {
                        val px = (x + kx - kHalf).coerceIn(0, w - 1)
                        val py = (y + ky - kHalf).coerceIn(0, h - 1)
                        val pixel = pixels[py * w + px]
                        val weight = kernel[ky * kSize + kx]
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                        a += Color.alpha(pixel) * weight
                    }
                }
                outPixels[y * w + x] = Color.argb(
                    a.coerceIn(0f, 255f).toInt(),
                    r.coerceIn(0f, 255f).toInt(),
                    g.coerceIn(0f, 255f).toInt(),
                    b.coerceIn(0f, 255f).toInt()
                )
            }
        }
        result.setPixels(outPixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun destroy() {
        renderScript?.destroy()
        renderScript = null
    }
}
