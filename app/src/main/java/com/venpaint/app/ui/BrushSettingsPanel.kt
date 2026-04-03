package com.venpaint.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.venpaint.app.brush.Brush
import com.venpaint.app.brush.BrushType
import com.venpaint.app.util.ColorUtils

/**
 * Right-side brush settings panel with vertical sliders for size, opacity, hardness, and spacing.
 * ibisPaint-style panel on the right side of the canvas.
 */
class BrushSettingsPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    // Callbacks
    var onBrushChanged: ((Brush) -> Unit)? = null
    var onBrushTypeSelected: ((BrushType) -> Unit)? = null

    // Current brush
    private var brush: Brush = Brush.default()

    // UI elements
    private val sizeSlider: SeekBar
    private val opacitySlider: SeekBar
    private val hardnessSlider: SeekBar
    private val sizeValue: TextView
    private val opacityValue: TextView
    private val hardnessValue: TextView
    private val colorPreview: ImageView

    // Color constants matching ibisPaint dark theme
    private val bgColor = "#0A0A1A"
    private val labelColor = "#A0A0B0"
    private val valueColor = "#FFFFFF"
    private val accentColor = "#E94560"
    private val borderColor = "#2A2A3D"

    init {
        orientation = VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.parseColor(bgColor))
        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))

        // Title
        val title = TextView(context).apply {
            text = "Brush"
            setTextColor(Color.parseColor(valueColor))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(8))
        }
        addView(title)

        // --- Color Preview ---
        colorPreview = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(48), dpToPx(48)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(createColorCircleDrawable(Color.BLACK))
        }
        addView(colorPreview)

        // --- Sliders Column ---
        val slidersLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Size slider
        val sizeRow = createSliderRow(context, "Size", 1, 100, 10) { progress, textView ->
            brush.size = progress.toFloat()
            textView.text = "$progress"
            notifyBrushChanged()
        }
        sizeSlider = sizeRow.slider
        sizeValue = sizeRow.valueText
        slidersLayout.addView(sizeRow.container)

        // Opacity slider
        val opacityRow = createSliderRow(context, "Opacity", 1, 100, 100) { progress, textView ->
            brush.opacity = progress / 100f
            textView.text = "$progress%"
            notifyBrushChanged()
        }
        opacitySlider = opacityRow.slider
        opacityValue = opacityRow.valueText
        slidersLayout.addView(opacityRow.container)

        // Hardness slider
        val hardnessRow = createSliderRow(context, "Hard", 0, 100, 80) { progress, textView ->
            brush.hardness = progress / 100f
            textView.text = "$progress%"
            notifyBrushChanged()
        }
        hardnessSlider = hardnessRow.slider
        hardnessValue = hardnessRow.valueText
        slidersLayout.addView(hardnessRow.container)

        addView(slidersLayout)

        // --- Brush Type Buttons ---
        val brushTypesLabel = TextView(context).apply {
            text = "Type"
            setTextColor(Color.parseColor(labelColor))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(4))
        }
        addView(brushTypesLabel)

        val brushTypesLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
            }
        }

        val typeNames = listOf("Pen", "Pencil", "Eraser")
        for (name in typeNames) {
            val btn = createTextButton(context, name) {
                val type = BrushType.entries.find { it.displayName == name } ?: BrushType.PEN
                selectBrushType(type)
            }
            brushTypesLayout.addView(btn)
        }

        addView(brushTypesLayout)
    }

    /**
     * Update the brush and sync UI.
     */
    fun setBrush(newBrush: Brush) {
        brush = newBrush.clone()

        // Update sliders without triggering callbacks
        sizeSlider.progress = brush.size.toInt()
        opacitySlider.progress = (brush.opacity * 100).toInt()
        hardnessSlider.progress = (brush.hardness * 100).toInt()

        // Update values
        sizeValue.text = "${brush.size.toInt()}"
        opacityValue.text = "${(brush.opacity * 100).toInt()}%"
        hardnessValue.text = "${(brush.hardness * 100).toInt()}%"

        // Update color preview
        updateColorPreview()
    }

    /**
     * Get the current brush.
     */
    fun getBrush(): Brush = brush.clone()

    /**
     * Set the brush color.
     */
    fun setColor(color: Int) {
        brush.color = color
        updateColorPreview()
        notifyBrushChanged()
    }

    /**
     * Get the current color.
     */
    fun getColor(): Int = brush.color

    /**
     * Select a brush type.
     */
    fun selectBrushType(type: BrushType) {
        val typeBrush = Brush.forType(type)
        brush.type = typeBrush.type
        brush.size = typeBrush.size
        brush.opacity = typeBrush.opacity
        brush.hardness = typeBrush.hardness
        brush.spacing = typeBrush.spacing
        if (type != BrushType.ERASER) {
            brush.color = typeBrush.color
        }

        // Update UI
        setBrush(brush)
        onBrushTypeSelected?.invoke(type)
    }

    private fun updateColorPreview() {
        colorPreview.setImageDrawable(createColorCircleDrawable(brush.color))
    }

    private fun notifyBrushChanged() {
        onBrushChanged?.invoke(brush.clone())
    }

    /**
     * Create a row with label, slider, and value text (vertical orientation).
     */
    private fun createSliderRow(
        context: Context,
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        onProgress: (Int, TextView) -> Unit
    ): SliderRow {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.START
            setPadding(0, dpToPx(4), 0, dpToPx(2))
        }

        // Label + Value row
        val labelRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(2))
        }

        val labelText = TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(labelColor))
            textSize = 11f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        labelRow.addView(labelText)

        val valueText = TextView(context).apply {
            text = "$initial"
            setTextColor(Color.parseColor(valueColor))
            textSize = 11f
            gravity = Gravity.END
        }
        labelRow.addView(valueText)
        container.addView(labelRow)

        val slider = SeekBar(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            this.max = max
            progress = initial
            setPadding(0, 0, 0, 0)

            // Style slider progress color
            progressDrawable?.setColorFilter(Color.parseColor(accentColor), PorterDuff.Mode.SRC_IN)
            thumb?.setColorFilter(Color.parseColor(accentColor), PorterDuff.Mode.SRC_IN)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        onProgress(progress, valueText)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        container.addView(slider)

        return SliderRow(container, slider, valueText)
    }

    /**
     * Create a small text button for brush type selection.
     */
    private fun createTextButton(context: Context, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor(valueColor))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
            setOnClickListener { onClick() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16213E"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(1, Color.parseColor(borderColor))
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(2)
            }
        }
    }

    /**
     * Create a color circle drawable.
     */
    private fun createColorCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dpToPx(48), dpToPx(48))
            setStroke(dpToPx(2), Color.parseColor(accentColor))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    data class SliderRow(
        val container: LinearLayout,
        val slider: SeekBar,
        val valueText: TextView
    )
}
