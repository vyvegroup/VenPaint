package com.venpaint.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * Represents a single layer in the drawing.
 * Each layer holds its own off-screen bitmap.
 */
class Layer(
    val id: Long = System.currentTimeMillis(),
    var name: String = "Layer",
    var bitmap: Bitmap? = null,
    var isVisible: Boolean = true,
    var opacity: Float = 1.0f,
    var blendMode: BlendMode = BlendMode.NORMAL
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Create or recreate the bitmap at the given dimensions.
     */
    fun createBitmap(width: Int, height: Int) {
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            // Ensure the bitmap is transparent
            val canvas = Canvas(this)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
    }

    /**
     * Get the canvas for this layer's bitmap.
     */
    fun getCanvas(): Canvas? {
        return bitmap?.let { Canvas(it) }
    }

    /**
     * Clear the layer bitmap to transparent.
     */
    fun clear() {
        bitmap?.let {
            val canvas = Canvas(it)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
    }

    /**
     * Draw this layer onto the given canvas, respecting visibility, opacity, and blend mode.
     */
    fun drawOn(canvas: Canvas) {
        if (!isVisible || bitmap == null || bitmap!!.isRecycled) return

        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = (opacity * 255).toInt()
        paint.xfermode = blendMode.porterDuffXfermode

        canvas.drawBitmap(bitmap!!, 0f, 0f, paint)
    }

    /**
     * Draw a portion of this layer onto the given canvas with a transform.
     */
    fun drawOn(canvas: Canvas, matrix: Matrix) {
        if (!isVisible || bitmap == null || bitmap!!.isRecycled) return

        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = (opacity * 255).toInt()
        paint.xfermode = blendMode.porterDuffXfermode
        paint.isFilterBitmap = true

        canvas.drawBitmap(bitmap!!, matrix, paint)
    }

    /**
     * Get a thumbnail of this layer.
     */
    fun getThumbnail(maxSize: Int = 128): Bitmap? {
        val src = bitmap ?: return null
        if (src.isRecycled) return null

        val ratio = maxSize.toFloat() / maxOf(src.width, src.height).toFloat()
        val width = (src.width * ratio).toInt().coerceAtLeast(1)
        val height = (src.height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    /**
     * Check if this layer has any non-transparent pixels.
     */
    fun hasContent(): Boolean {
        val bmp = bitmap ?: return false
        if (bmp.isRecycled) return false

        val pixels = IntArray(1)
        // Sample a few points
        for (x in 0 until bmp.width step max(1, bmp.width / 10)) {
            for (y in 0 until bmp.height step max(1, bmp.height / 10)) {
                bmp.getPixels(pixels, 0, 1, x, y, 1, 1)
                if (Color.alpha(pixels[0]) > 0) return true
            }
        }
        return false
    }

    /**
     * Duplicate this layer.
     */
    fun duplicate(newName: String? = null): Layer {
        val newLayer = Layer(
            id = System.currentTimeMillis(),
            name = newName ?: "$name copy",
            isVisible = isVisible,
            opacity = opacity,
            blendMode = blendMode
        )
        bitmap?.let { src ->
            if (!src.isRecycled) {
                newLayer.bitmap = src.copy(src.config, true)
            }
        }
        return newLayer
    }

    /**
     * Release bitmap resources.
     */
    fun recycle() {
        bitmap?.recycle()
        bitmap = null
    }

    /**
     * Merge another layer onto this layer.
     */
    fun mergeFrom(other: Layer) {
        val otherBitmap = other.bitmap ?: return
        if (otherBitmap.isRecycled) return

        val thisBitmap = this.bitmap ?: return
        if (thisBitmap.isRecycled) return

        val canvas = Canvas(thisBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.alpha = (other.opacity * 255).toInt()
        paint.xfermode = other.blendMode.porterDuffXfermode
        canvas.drawBitmap(otherBitmap, 0f, 0f, paint)
    }

    /**
     * Get the bounds of non-transparent content.
     */
    fun getContentBounds(): RectF? {
        val bmp = bitmap ?: return null
        if (bmp.isRecycled) return null

        var minX = bmp.width.toFloat()
        var minY = bmp.height.toFloat()
        var maxX = 0f
        var maxY = 0f

        val pixels = IntArray(bmp.width)
        var foundContent = false

        for (y in 0 until bmp.height) {
            bmp.getPixels(pixels, 0, bmp.width, 0, y, bmp.width, 1)
            for (x in pixels.indices) {
                if (Color.alpha(pixels[x]) > 0) {
                    minX = minX.coerceAtMost(x.toFloat())
                    minY = minY.coerceAtMost(y.toFloat())
                    maxX = maxX.coerceAtLeast(x.toFloat() + 1)
                    maxY = maxY.coerceAtLeast(y.toFloat() + 1)
                    foundContent = true
                }
            }
        }

        return if (foundContent) RectF(minX, minY, maxX, maxY) else null
    }
}

/**
 * Supported blend modes.
 */
enum class BlendMode(val porterDuffXfermode: PorterDuffXfermode) {
    NORMAL(PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)),
    MULTIPLY(PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)),
    SCREEN(PorterDuffXfermode(PorterDuff.Mode.SCREEN)),
    OVERLAY(PorterDuffXfermode(PorterDuff.Mode.OVERLAY));

    companion object {
        fun fromIndex(index: Int): BlendMode = entries.getOrElse(index) { NORMAL }
    }
}
