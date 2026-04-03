package com.venpaint.app.engine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DiscretePathEffect
import android.graphics.MaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.venpaint.app.brush.Brush
import com.venpaint.app.brush.BrushType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core brush rendering engine that handles drawing strokes onto bitmaps.
 * Supports multiple brush types with different rendering behaviors.
 */
class BrushEngine {

    private val strokePath = Path()
    private var lastPoint: PointF? = null
    private var lastMidPoint: PointF? = null
    private var isDrawing = false
    private var accumulatedDistance = 0f
    private var strokeAngle = 0f
    private var randomSeed = 0L

    // Pre-allocated paints for performance
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tempPath = Path()

    /**
     * Start a new stroke at the given point.
     */
    fun startStroke(point: PointF, brush: Brush, canvas: Canvas) {
        resetStroke()
        isDrawing = true
        lastPoint = point
        lastMidPoint = point
        accumulatedDistance = 0f
        randomSeed = System.currentTimeMillis()
        strokeAngle = 0f

        setupPaint(brush)
        drawStrokePoint(point, brush, canvas, point, 1.0f)
    }

    /**
     * Continue the stroke to a new point.
     */
    fun continueStroke(
        point: PointF,
        brush: Brush,
        canvas: Canvas,
        pressure: Float = 1.0f
    ) {
        if (!isDrawing) return

        val last = lastPoint ?: return
        val mid = PointF(
            (last.x + point.x) / 2f,
            (last.y + point.y) / 2f
        )

        // Calculate distance for spacing
        val dx = point.x - last.x
        val dy = point.y - last.y
        val distance = sqrt(dx * dx + dy * dy)
        accumulatedDistance += distance

        // Update stroke angle for flat brush
        if (distance > 1f) {
            strokeAngle = kotlin.math.atan2(dy, dx).toFloat()
        }

        // Draw interpolated points based on brush spacing
        val spacingPixels = brush.spacing * brush.size
        if (spacingPixels < 1f || brush.type == BrushType.PEN || brush.type == BrushType.PENCIL) {
            // For pen/pencil, draw the bezier segment directly
            drawStrokeSegment(lastMidPoint!!, mid, brush, canvas, pressure)
        } else {
            // For other brushes, stamp along the path
            drawStampedStroke(lastMidPoint!!, mid, brush, canvas, spacingPixels, pressure)
        }

        lastMidPoint = mid
        lastPoint = point
    }

    /**
     * End the current stroke.
     */
    fun endStroke(point: PointF, brush: Brush, canvas: Canvas) {
        if (!isDrawing) return

        val last = lastPoint ?: return
        drawStrokeSegment(lastMidPoint ?: last, point, brush, canvas, 1.0f)

        isDrawing = false
        lastPoint = null
        lastMidPoint = null
    }

    /**
     * Cancel the current stroke.
     */
    fun cancelStroke() {
        isDrawing = false
        lastPoint = null
        lastMidPoint = null
        strokePath.reset()
        accumulatedDistance = 0f
    }

    /**
     * Draw a single dot (e.g., for tap without move).
     */
    fun drawDot(point: PointF, brush: Brush, canvas: Canvas) {
        setupPaint(brush)
        drawStrokePoint(point, brush, canvas, point, 1.0f)
    }

    /**
     * Fill an area with the brush color on the given canvas.
     */
    fun fillArea(canvas: Canvas, color: Int, bounds: RectF) {
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRect(bounds, paint)
    }

    /**
     * Reset stroke state.
     */
    private fun resetStroke() {
        strokePath.reset()
        lastPoint = null
        lastMidPoint = null
        accumulatedDistance = 0f
    }

    /**
     * Setup the base paint for the current brush.
     */
    private fun setupPaint(brush: Brush) {
        basePaint.reset()
        basePaint.isAntiAlias = true

        when (brush.type) {
            BrushType.ERASER -> {
                basePaint.color = Color.TRANSPARENT
                basePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                basePaint.style = Paint.Style.STROKE
                basePaint.strokeCap = Paint.Cap.ROUND
                basePaint.strokeJoin = Paint.Join.ROUND
                basePaint.strokeWidth = brush.size
                basePaint.alpha = 255
            }
            BrushType.PEN -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.STROKE
                basePaint.strokeCap = Paint.Cap.ROUND
                basePaint.strokeJoin = Paint.Join.ROUND
                basePaint.strokeWidth = brush.size
                basePaint.alpha = (brush.opacity * 255).toInt()
            }
            BrushType.PENCIL -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.STROKE
                basePaint.strokeCap = Paint.Cap.ROUND
                basePaint.strokeJoin = Paint.Join.ROUND
                basePaint.strokeWidth = brush.size * 0.7f
                basePaint.alpha = (brush.opacity * 180).toInt()
                // Add slight texture
                basePaint.pathEffect = DiscretePathEffect(1.5f, 0.5f)
            }
            BrushType.AIRBRUSH -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.FILL
                basePaint.alpha = (brush.opacity * 60).toInt()
                val blurRadius = brush.size * brush.hardness.coerceAtLeast(0.1f)
                basePaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
            BrushType.WATERCOLOR -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.FILL
                basePaint.alpha = (brush.opacity * 100).toInt()
                basePaint.maskFilter = BlurMaskFilter(
                    brush.size * 0.8f,
                    BlurMaskFilter.Blur.NORMAL
                )
            }
            BrushType.FLAT_BRUSH -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.STROKE
                basePaint.strokeCap = Paint.Cap.SQUARE
                basePaint.strokeJoin = Paint.Join.MITER
                basePaint.strokeWidth = brush.size
                basePaint.alpha = (brush.opacity * 255).toInt()
            }
            BrushType.ROUND_BRUSH -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.STROKE
                basePaint.strokeCap = Paint.Cap.ROUND
                basePaint.strokeJoin = Paint.Join.ROUND
                basePaint.strokeWidth = brush.size
                basePaint.alpha = (brush.opacity * 255).toInt()
            }
            BrushType.CRAYON -> {
                basePaint.color = brush.color
                basePaint.style = Paint.Style.STROKE
                basePaint.strokeCap = Paint.Cap.BUTT
                basePaint.strokeJoin = Paint.Join.BEVEL
                basePaint.strokeWidth = brush.size * 1.2f
                basePaint.alpha = (brush.opacity * 200).toInt()
                basePaint.pathEffect = DiscretePathEffect(3f, 2f)
            }
        }
    }

    /**
     * Draw a bezier segment between two points.
     */
    private fun drawStrokeSegment(
        from: PointF,
        to: PointF,
        brush: Brush,
        canvas: Canvas,
        pressure: Float
    ) {
        when (brush.type) {
            BrushType.ERASER,
            BrushType.PEN,
            BrushType.PENCIL,
            BrushType.FLAT_BRUSH,
            BrushType.ROUND_BRUSH,
            BrushType.CRAYON -> {
                // Draw as smooth bezier curve
                strokePath.reset()
                strokePath.moveTo(from.x, from.y)

                // Use quadratic bezier for smooth curves
                val controlX = from.x
                val controlY = from.y
                strokePath.quadTo(controlX, controlY, to.x, to.y)

                // Apply pressure to size
                val pressureSize = basePaint.strokeWidth * pressure.coerceIn(0.1f, 1.5f)
                basePaint.strokeWidth = pressureSize

                canvas.drawPath(strokePath, basePaint)
            }
            BrushType.AIRBRUSH -> {
                // Stamp airbrush dots along the segment
                val steps = ((distance(from, to) / (brush.size * 0.3f)).toInt()).coerceAtLeast(1)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = from.x + (to.x - from.x) * t
                    val y = from.y + (to.y - from.y) * t
                    val radius = brush.size * 0.5f * pressure.coerceIn(0.2f, 1.2f)
                    canvas.drawCircle(x, y, radius, basePaint)
                }
            }
            BrushType.WATERCOLOR -> {
                // Draw watercolor-style strokes with multiple overlapping circles
                val steps = ((distance(from, to) / (brush.size * 0.2f)).toInt()).coerceAtLeast(1)
                stampPaint.set(basePaint)

                // Use a pseudo-random based on position for watercolor texture
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = from.x + (to.x - from.x) * t
                    val y = from.y + (to.y - from.y) * t
                    val radius = brush.size * 0.5f * (0.8f + 0.4f * pseudoRandom(x.toInt(), y.toInt()))

                    // Vary opacity for watercolor effect
                    stampPaint.alpha = (brush.opacity * (60 + 40 * pseudoRandom(i, x.toInt())).coerceIn(20f, 120f)).toInt()
                    canvas.drawCircle(x, y, radius, stampPaint)
                }
            }
        }
    }

    /**
     * Draw stamped points along a path for brushes that use spacing.
     */
    private fun drawStampedStroke(
        from: PointF,
        to: PointF,
        brush: Brush,
        canvas: Canvas,
        spacing: Float,
        pressure: Float
    ) {
        val dist = distance(from, to)
        if (dist < spacing * 0.5f) return

        val steps = (dist / spacing).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            drawStrokePoint(PointF(x, y), brush, canvas, PointF(x, y), pressure)
        }
    }

    /**
     * Draw a single stroke point (stamp) at the given position.
     */
    private fun drawStrokePoint(
        point: PointF,
        brush: Brush,
        canvas: Canvas,
        velocity: PointF,
        pressure: Float
    ) {
        when (brush.type) {
            BrushType.ERASER -> {
                val radius = brush.size * 0.5f * pressure.coerceIn(0.2f, 1.5f)
                canvas.drawCircle(point.x, point.y, radius, basePaint)
            }
            BrushType.PEN, BrushType.ROUND_BRUSH -> {
                val radius = brush.size * 0.5f * pressure.coerceIn(0.2f, 1.5f)
                canvas.drawCircle(point.x, point.y, radius, basePaint)
            }
            BrushType.PENCIL -> {
                val radius = brush.size * 0.35f * pressure.coerceIn(0.3f, 1.0f)
                stampPaint.set(basePaint)
                // Add slight randomness for pencil texture
                val offsetX = pseudoRandom(point.x.toInt(), point.y.toInt()) * 1f
                val offsetY = pseudoRandom(point.y.toInt(), point.x.toInt()) * 1f
                canvas.drawCircle(point.x + offsetX, point.y + offsetY, radius, stampPaint)
            }
            BrushType.AIRBRUSH -> {
                val radius = brush.size * 0.5f * pressure.coerceIn(0.3f, 1.2f)
                stampPaint.set(basePaint)
                canvas.drawCircle(point.x, point.y, radius, stampPaint)
            }
            BrushType.WATERCOLOR -> {
                stampPaint.set(basePaint)
                val radius = brush.size * 0.5f * (0.7f + 0.3f * pseudoRandom(point.x.toInt(), point.y.toInt()))
                stampPaint.alpha = (brush.opacity * 80).toInt()
                canvas.drawCircle(point.x, point.y, radius, stampPaint)

                // Add secondary splatter
                val splatterCount = (3 * pressure).toInt().coerceAtLeast(1)
                for (i in 0 until splatterCount) {
                    val angle = pseudoRandom(point.x.toInt() + i, point.y.toInt()) * Math.PI.toFloat() * 2f
                    val dist = brush.size * 0.3f * pseudoRandom(i, point.x.toInt())
                    val sx = point.x + cos(angle) * dist
                    val sy = point.y + sin(angle) * dist
                    stampPaint.alpha = (brush.opacity * 40).toInt()
                    canvas.drawCircle(sx, sy, radius * 0.3f, stampPaint)
                }
            }
            BrushType.FLAT_BRUSH -> {
                // Draw rectangular stamp, rotated to stroke direction
                val halfW = brush.size * 0.5f * pressure.coerceIn(0.2f, 1.5f)
                val halfH = brush.size * 0.15f
                canvas.save()
                canvas.rotate(strokeAngle * 180f / Math.PI.toFloat(), point.x, point.y)
                canvas.drawRect(
                    point.x - halfW, point.y - halfH,
                    point.x + halfW, point.y + halfH,
                    basePaint
                )
                canvas.restore()
            }
            BrushType.CRAYON -> {
                stampPaint.set(basePaint)
                val radius = brush.size * 0.6f * pressure.coerceIn(0.3f, 1.0f)
                // Draw multiple overlapping small circles for texture
                val count = 5
                for (i in 0 until count) {
                    val ox = pseudoRandom(i, point.x.toInt()) * brush.size * 0.3f - brush.size * 0.15f
                    val oy = pseudoRandom(point.y.toInt(), i) * brush.size * 0.3f - brush.size * 0.15f
                    stampPaint.alpha = (brush.opacity * (150 + 80 * pseudoRandom(i * 7, point.x.toInt() + point.y.toInt()))).toInt().coerceIn(0, 255)
                    canvas.drawCircle(point.x + ox, point.y + oy, radius * 0.4f, stampPaint)
                }
            }
        }
    }

    /**
     * Simple pseudo-random number generator (deterministic based on seed).
     */
    private fun pseudoRandom(seed1: Int, seed2: Int): Float {
        var x = ((seed1 * 374761393 + seed2 * 668265263) xor randomSeed.toInt()).toLong()
        x = ((x xor (x shr 13)) * 1274126177)
        x = x xor (x shr 16)
        return ((x and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat())
    }

    /**
     * Calculate distance between two points.
     */
    private fun distance(a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
}
