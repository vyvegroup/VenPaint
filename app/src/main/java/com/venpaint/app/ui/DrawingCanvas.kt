package com.venpaint.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.venpaint.app.brush.Brush
import com.venpaint.app.engine.BrushEngine
import com.venpaint.app.engine.LayerManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View that serves as the main drawing canvas.
 * Handles touch events for drawing with pressure sensitivity,
 * supports zoom and pan with gesture detectors.
 */
class DrawingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val MIN_ZOOM = 0.1f
        private const val MAX_ZOOM = 10f
        private const val TOUCH_TOLERANCE = 4f
    }

    // Canvas dimensions
    var canvasWidth: Int = 1080
        private set
    var canvasHeight: Int = 1920
        private set

    // Managers
    var layerManager: LayerManager? = null
        set(value) {
            field = value
            if (value != null) {
                canvasWidth = value.getLayer(0)?.bitmap?.width ?: canvasWidth
                canvasHeight = value.getLayer(0)?.bitmap?.height ?: canvasHeight
                invalidate()
            }
        }

    // Current brush
    var currentBrush: Brush = Brush.default()

    // Brush engine
    private val brushEngine = BrushEngine()

    // Off-screen bitmap for compositing
    private var compositeBitmap: Bitmap? = null
    private var compositeCanvas: Canvas? = null

    // Checkerboard pattern for transparency
    private var checkerboardBitmap: Bitmap? = null

    // Zoom/Pan state
    private val transformMatrix = Matrix()
    private var zoomScale = 1f
    private var panX = 0f
    private var panY = 0f

    // Touch state
    private var isDrawing = false
    private var isPanning = false
    private var isPinching = false
    private var lastTouchPoint: PointF? = null
    private var touchStartTime = 0L

    // Gesture detectors
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    // Callbacks
    var onDrawStart: (() -> Unit)? = null
    var onDrawEnd: (() -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null

    init {
        // Enable drawing cache
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // Initialize gesture detectors
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    /**
     * Initialize the canvas with the given dimensions.
     */
    fun initialize(width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height
        createCompositeBitmap()
        createCheckerboard()
        invalidate()
    }

    /**
     * Reset zoom and pan to fit the canvas in view.
     */
    fun resetView() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        val scaleX = viewWidth / canvasWidth
        val scaleY = viewHeight / canvasHeight
        zoomScale = min(scaleX, scaleY) * 0.9f

        panX = (viewWidth - canvasWidth * zoomScale) / 2f
        panY = (viewHeight - canvasHeight * zoomScale) / 2f

        updateTransformMatrix()
        invalidate()
    }

    /**
     * Get the current zoom level.
     */
    fun getZoomLevel(): Float = zoomScale

    /**
     * Set zoom level.
     */
    fun setZoomLevel(zoom: Float) {
        zoomScale = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        updateTransformMatrix()
        invalidate()
        onZoomChanged?.invoke(zoomScale)
    }

    /**
     * Create the off-screen composite bitmap.
     */
    private fun createCompositeBitmap() {
        compositeBitmap?.recycle()
        compositeBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        compositeCanvas = Canvas(compositeBitmap!!)
    }

    /**
     * Create a checkerboard pattern for transparency indication.
     */
    private fun createCheckerboard() {
        val size = 16
        val checkerCount = 8
        val totalSize = size * checkerCount
        checkerboardBitmap = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(checkerboardBitmap!!)
        val light = Color.parseColor("#FF3A3A50")
        val dark = Color.parseColor("#FF2A2A3D")

        for (i in 0 until checkerCount) {
            for (j in 0 until checkerCount) {
                canvas.drawRect(
                    (i * size).toFloat(),
                    (j * size).toFloat(),
                    ((i + 1) * size).toFloat(),
                    ((j + 1) * size).toFloat(),
                    android.graphics.Paint().apply {
                        color = if ((i + j) % 2 == 0) light else dark
                    }
                )
            }
        }
    }

    private fun updateTransformMatrix() {
        transformMatrix.reset()
        transformMatrix.postTranslate(panX, panY)
        transformMatrix.postScale(zoomScale, zoomScale, panX, panY)
    }

    // ==================== Touch Handling ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        if (scaleGestureDetector.isInProgress) {
            isPinching = true
            return true
        }

        isPinching = false

        // Handle single-finger drawing
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        // Convert screen coordinates to canvas coordinates
        val canvasPoint = screenToCanvas(x, y)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    isDrawing = true
                    touchStartTime = System.currentTimeMillis()
                    lastTouchPoint = canvasPoint

                    onDrawStart?.invoke()

                    // Start stroke on the active layer
                    val layer = layerManager?.activeLayer
                    if (layer != null) {
                        val layerCanvas = layer.getCanvas()
                        if (layerCanvas != null) {
                            brushEngine.startStroke(canvasPoint, currentBrush, layerCanvas)
                        }
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDrawing && !isPinching && event.pointerCount == 1) {
                    val pressure = event.getPressure(0).coerceIn(0.1f, 1.5f)

                    val layer = layerManager?.activeLayer
                    if (layer != null) {
                        val layerCanvas = layer.getCanvas()
                        if (layerCanvas != null) {
                            brushEngine.continueStroke(canvasPoint, currentBrush, layerCanvas, pressure)
                        }
                    }
                    invalidate()
                } else if (event.pointerCount == 2) {
                    // Two finger pan
                    isDrawing = false
                    brushEngine.cancelStroke()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing && !isPinching) {
                    val layer = layerManager?.activeLayer
                    if (layer != null) {
                        val layerCanvas = layer.getCanvas()
                        if (layerCanvas != null) {
                            brushEngine.endStroke(canvasPoint, currentBrush, layerCanvas)
                        }
                    }
                    isDrawing = false
                    onDrawEnd?.invoke()
                    invalidate()
                }
                isPinching = false
                return true
            }
        }

        return gestureDetector.onTouchEvent(event)
    }

    /**
     * Convert screen coordinates to canvas coordinates.
     */
    private fun screenToCanvas(screenX: Float, screenY: Float): PointF {
        val inverted = Matrix()
        transformMatrix.invert(inverted)

        val points = floatArrayOf(screenX, screenY)
        inverted.mapPoints(points)

        return PointF(points[0], points[1])
    }

    /**
     * Convert canvas coordinates to screen coordinates.
     */
    private fun canvasToScreen(canvasX: Float, canvasY: Float): PointF {
        val points = floatArrayOf(canvasX, canvasY)
        transformMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    // ==================== Drawing ====================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        // Draw checkerboard background for canvas area
        drawCheckerboard(canvas)

        // Draw all layers
        compositeCanvas?.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        layerManager?.renderLayers(compositeCanvas!!)

        // Draw the composite bitmap with transform
        compositeBitmap?.let {
            canvas.drawBitmap(it, transformMatrix, null)
        }

        // Draw canvas border
        drawCanvasBorder(canvas)
    }

    /**
     * Draw the checkerboard pattern to indicate transparency.
     */
    private fun drawCheckerboard(canvas: Canvas) {
        val checker = checkerboardBitmap ?: return

        val topLeft = canvasToScreen(0f, 0f)
        val bottomRight = canvasToScreen(canvasWidth.toFloat(), canvasHeight.toFloat())

        val src = android.graphics.Rect(0, 0, checker.width, checker.height)
        val dst = android.graphics.RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)

        val shader = android.graphics.BitmapShader(
            checker,
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT
        )
        val paint = android.graphics.Paint().apply {
            this.shader = shader
        }
        canvas.drawRect(dst, paint)
    }

    /**
     * Draw a border around the canvas area.
     */
    private fun drawCanvasBorder(canvas: Canvas) {
        val topLeft = canvasToScreen(0f, 0f)
        val bottomRight = canvasToScreen(canvasWidth.toFloat(), canvasHeight.toFloat())

        val paint = android.graphics.Paint().apply {
            color = Color.parseColor("#404060")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(
            topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, paint
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            resetView()
        }
    }

    // ==================== Gesture Listeners ====================

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = zoomScale * scaleFactor

            if (newScale in MIN_ZOOM..MAX_ZOOM) {
                val focusX = detector.focusX
                val focusY = detector.focusY

                // Zoom toward the focal point
                val dx = focusX - panX
                val dy = focusY - panY
                panX = focusX - dx * scaleFactor
                panY = focusY - dy * scaleFactor

                zoomScale = newScale
                updateTransformMatrix()
                onZoomChanged?.invoke(zoomScale)
                invalidate()
            }
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetView()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Could show eyedropper on long press
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1?.pointerCount == 1 && e2.pointerCount == 1 && !isDrawing) {
                // Pan
                panX -= distanceX
                panY -= distanceY
                updateTransformMatrix()
                invalidate()
                return true
            }
            return false
        }
    }

    /**
     * Clear the active layer.
     */
    fun clearActiveLayer() {
        layerManager?.clearActiveLayer()
        invalidate()
    }

    /**
     * Refresh the canvas (e.g., after layer changes).
     */
    fun refresh() {
        invalidate()
    }
}
