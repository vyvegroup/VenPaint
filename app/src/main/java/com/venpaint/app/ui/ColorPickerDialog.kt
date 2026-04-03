package com.venpaint.app.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import com.venpaint.app.util.ColorUtils

/**
 * Color picker dialog with HSV sliders and a preset palette.
 * Styled to match ibisPaint dark theme.
 */
class ColorPickerDialog(
    private val context: Context,
    initialColor: Int = Color.BLACK,
    private val onColorSelected: (Int) -> Unit
) {
    private var currentColor = initialColor
    private var hue = 0f
    private var saturation = 1f
    private var value = 1f

    private lateinit var colorPreview: View
    private lateinit var hueSlider: SeekBar
    private lateinit var saturationSlider: SeekBar
    private lateinit var valueSlider: SeekBar
    private lateinit var hueValue: TextView
    private lateinit var saturationValue: TextView
    private lateinit var valueValue: TextView
    private lateinit var hexInput: TextView

    // ibisPaint dark theme colors
    private val surfaceColor = "#16213E"
    private val accentColor = "#E94560"
    private val textColor = "#FFFFFF"
    private val secondaryTextColor = "#A0A0B0"
    private val borderColor = "#2A2A3D"

    init {
        val hsv = ColorUtils.colorToHsv(initialColor)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    /**
     * Show the color picker dialog.
     */
    fun show() {
        val dialogView = buildDialogView()

        AlertDialog.Builder(context)
            .setTitle("Pick Color")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                onColorSelected(currentColor)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Build the dialog content view.
     */
    private fun buildDialogView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        // Color preview
        colorPreview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                bottomMargin = dpToPx(12)
            }
            background = GradientDrawable().apply { setColor(currentColor); cornerRadius = dpToPx(8).toFloat() }
        }
        container.addView(colorPreview)

        // Hex display
        hexInput = TextView(context).apply {
            text = ColorUtils.colorToHex(currentColor)
            setTextColor(Color.parseColor(secondaryTextColor))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }
        container.addView(hexInput)

        // HSV Sliders
        // Hue slider (0-360)
        val hueRow = createSliderRow("Hue", 0, 360, hue.toInt()) { progress ->
            hue = progress.toFloat()
            updateColor()
        }
        hueSlider = hueRow.slider
        hueValue = hueRow.valueText
        container.addView(hueRow.container)

        // Saturation slider (0-100)
        val satRow = createSliderRow("Sat", 0, 100, (saturation * 100).toInt()) { progress ->
            saturation = progress / 100f
            updateColor()
        }
        saturationSlider = satRow.slider
        saturationValue = satRow.valueText
        container.addView(satRow.container)

        // Value slider (0-100)
        val valRow = createSliderRow("Val", 0, 100, (value * 100).toInt()) { progress ->
            value = progress / 100f
            updateColor()
        }
        valueSlider = valRow.slider
        valueValue = valRow.valueText
        container.addView(valRow.container)

        // Preset palette
        val paletteLabel = TextView(context).apply {
            text = "Presets"
            setTextColor(Color.parseColor(secondaryTextColor))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(12)
                bottomMargin = dpToPx(4)
            }
        }
        container.addView(paletteLabel)

        val palette = createPalette()
        container.addView(palette)

        return container
    }

    /**
     * Update the current color from HSV values and refresh the UI.
     */
    private fun updateColor() {
        currentColor = ColorUtils.hsvToColor(hue, saturation, value)

        // Update preview
        colorPreview.background = GradientDrawable().apply {
            setColor(currentColor)
            cornerRadius = dpToPx(8).toFloat()
        }

        // Update hex
        hexInput.text = ColorUtils.colorToHex(currentColor)
    }

    /**
     * Create a palette grid with preset colors.
     */
    private fun createPalette(): GridLayout {
        val grid = GridLayout(context).apply {
            columnCount = 10
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val paletteColors = ColorUtils.defaultPalette

        for (color in paletteColors) {
            val swatch = View(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(28)
                    height = dpToPx(28)
                    setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                }
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), Color.parseColor(borderColor))
                }
                setOnClickListener {
                    val hsv = ColorUtils.colorToHsv(color)
                    hue = hsv[0]
                    saturation = hsv[1]
                    value = hsv[2]
                    hueSlider.progress = hue.toInt()
                    saturationSlider.progress = (saturation * 100).toInt()
                    valueSlider.progress = (value * 100).toInt()
                    updateColor()
                }
            }
            grid.addView(swatch)
        }

        return grid
    }

    private data class SliderRow(
        val container: LinearLayout,
        val slider: SeekBar,
        val valueText: TextView
    )

    private fun createSliderRow(
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        onProgress: (Int) -> Unit
    ): SliderRow {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        val labelText = TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(secondaryTextColor))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(labelText)

        val valueText = TextView(context).apply {
            text = "$initial"
            setTextColor(Color.parseColor(textColor))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(4)
            }
        }

        val slider = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            this.max = max
            progress = initial
            progressDrawable?.setColorFilter(Color.parseColor(accentColor), PorterDuff.Mode.SRC_IN)
            thumb?.setColorFilter(Color.parseColor(accentColor), PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        valueText.text = "$progress"
                        onProgress(progress)
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

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
