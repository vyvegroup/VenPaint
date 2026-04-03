package com.venpaint.app.ui

import android.content.Context
import android.graphics.Color
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
 * Bottom panel for brush settings with sliders for size, opacity, hardness, and spacing.
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

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#CC1B1B2F"))
        setPadding(12, 8, 12, 8)

        // --- Color Preview ---
        colorPreview = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(createColorCircleDrawable(Color.BLACK))
        }
        addView(colorPreview)

        // --- Sliders Column ---
        val slidersLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
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
        val brushTypesLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(12)
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
     * Create a row with label, slider, and value text.
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
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
        }

        val labelText = TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#B0B0C0"))
            textSize = 11f
            layoutParams = LayoutParams(dpToPx(44), LayoutParams.WRAP_CONTENT)
        }
        container.addView(labelText)

        val valueText = TextView(context).apply {
            text = "$initial"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 11f
            layoutParams = LayoutParams(dpToPx(36), LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(4)
            }
            gravity = Gravity.END
        }

        val slider = SeekBar(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            this.max = max
            progress = initial
            setPadding(0, 0, 0, 0)

            // Style the slider
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
        container.addView(valueText)

        return SliderRow(container, slider, valueText)
    }

    /**
     * Create a small text button.
     */
    private fun createTextButton(context: Context, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 11f
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            setOnClickListener { onClick() }
            setBackgroundResource(android.R.drawable.btn_default)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 2
                bottomMargin = 2
            }
        }
    }

    /**
     * Create a color circle drawable.
     */
    private fun createColorCircleDrawable(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setSize(dpToPx(36), dpToPx(36))
            setStroke(dpToPx(1), Color.parseColor("#404060"))
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
