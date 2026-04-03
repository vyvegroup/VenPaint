package com.venpaint.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.venpaint.app.engine.BlendMode
import com.venpaint.app.engine.Layer
import com.venpaint.app.engine.LayerManager

/**
 * Panel showing the layer stack with controls for visibility, opacity, blend modes, etc.
 */
class LayerPanel @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    // Callbacks
    var onLayerSelected: ((Int) -> Unit)? = null
    var onLayerVisibilityChanged: ((Int) -> Unit)? = null
    var onLayerOpacityChanged: ((Int, Float) -> Unit)? = null
    var onLayerBlendModeChanged: ((Int, BlendMode) -> Unit)? = null
    var onLayerAdded: (() -> Unit)? = null
    var onLayerDeleted: ((Int) -> Unit)? = null
    var onLayerDuplicated: (() -> Unit)? = null
    var onLayerMergedDown: (() -> Unit)? = null

    // Layer manager reference
    private var layerManager: LayerManager? = null

    // Selected layer index
    private var selectedIndex: Int = 0

    // UI elements
    private val layerList: LinearLayout
    private val addBtn: Button
    private val deleteBtn: Button
    private val duplicateBtn: Button
    private val mergeBtn: Button
    private val opacitySlider: SeekBar
    private val opacityValue: TextView
    private val blendModeButton: Button

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#E61B1B2F"))
        setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))

        // Title
        val title = TextView(context).apply {
            text = "Layers"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 16f
            setPadding(0, 0, 0, dpToPx(8))
        }
        addView(title)

        // Scrollable layer list
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        layerList = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, 0)
        }
        scrollView.addView(layerList)
        addView(scrollView)

        // Opacity control
        val opacityRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }

        val opacityLabel = TextView(context).apply {
            text = "Opacity:"
            setTextColor(Color.parseColor("#B0B0C0"))
            textSize = 12f
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpToPx(8)
            }
        }
        opacityRow.addView(opacityLabel)

        opacityValue = TextView(context).apply {
            text = "100%"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 12f
            layoutParams = LayoutParams(dpToPx(36), LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(4)
            }
        }

        opacitySlider = SeekBar(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        opacityValue.text = "$progress%"
                        onLayerOpacityChanged?.invoke(selectedIndex, progress / 100f)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        opacityRow.addView(opacitySlider)
        opacityRow.addView(opacityValue)

        addView(opacityRow)

        // Blend mode button
        blendModeButton = Button(context).apply {
            text = "Normal"
            textSize = 12f
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(4)
                bottomMargin = dpToPx(4)
            }
            setOnClickListener { cycleBlendMode() }
        }
        addView(blendModeButton)

        // Action buttons
        val buttonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }

        addBtn = createButton(context, "+ Add") {
            onLayerAdded?.invoke()
        }
        buttonsRow.addView(addBtn)

        duplicateBtn = createButton(context, "Dup") {
            onLayerDuplicated?.invoke()
        }
        buttonsRow.addView(duplicateBtn)

        mergeBtn = createButton(context, "Merge") {
            onLayerMergedDown?.invoke()
        }
        buttonsRow.addView(mergeBtn)

        deleteBtn = createButton(context, "Del") {
            onLayerDeleted?.invoke(selectedIndex)
        }
        buttonsRow.addView(deleteBtn)

        addView(buttonsRow)
    }

    /**
     * Set the layer manager and refresh the panel.
     */
    fun setLayerManager(manager: LayerManager?) {
        layerManager = manager
        refreshLayers()
    }

    /**
     * Set the selected layer index.
     */
    fun setSelectedIndex(index: Int) {
        selectedIndex = index
        refreshLayers()

        // Update opacity slider
        val layer = layerManager?.getLayer(index)
        if (layer != null) {
            opacitySlider.progress = (layer.opacity * 100).toInt()
            opacityValue.text = "${(layer.opacity * 100).toInt()}%"
            blendModeButton.text = layer.blendMode.name
        }
    }

    /**
     * Refresh the layer list display.
     */
    fun refreshLayers() {
        layerList.removeAllViews()

        val layers = layerManager?.getLayers() ?: return

        // Show layers in reverse order (top layer first)
        for (i in layers.indices.reversed()) {
            val layer = layers[i]
            val layerIndex = i

            val layerRow = createLayerRow(context, layer, layerIndex == selectedIndex) {
                // Visibility toggle
                onLayerVisibilityChanged?.invoke(layerIndex)
                refreshLayers()
            }

            layerRow.setOnClickListener {
                setSelectedIndex(layerIndex)
                onLayerSelected?.invoke(layerIndex)
            }

            layerList.addView(layerRow)
        }
    }

    /**
     * Create a row for a single layer.
     */
    private fun createLayerRow(
        context: Context,
        layer: Layer,
        isSelected: Boolean,
        onVisibilityClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            minimumHeight = dpToPx(48)

            if (isSelected) {
                setBackgroundColor(Color.parseColor("#55E94560"))
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }

            // Reset background on touch
            val bg = if (isSelected) Color.parseColor("#55E94560") else Color.TRANSPARENT
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        // Visibility icon
        val visibilityIcon = TextView(context).apply {
            text = if (layer.isVisible) "👁" else "🚫"
            textSize = 14f
            layoutParams = LayoutParams(dpToPx(32), LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener { onVisibilityClick() }
        }
        row.addView(visibilityIcon)

        // Thumbnail
        val thumbnail = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginEnd = dpToPx(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val thumb = layer.getThumbnail(80)
            if (thumb != null) {
                setImageBitmap(thumb)
            } else {
                setImageDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            }
            setBackgroundResource(android.R.drawable.spinner_background)
            setPadding(1, 1, 1, 1)
        }
        row.addView(thumbnail)

        // Layer name
        val nameText = TextView(context).apply {
            text = layer.name
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }
        row.addView(nameText)

        // Layer number
        val indexText = TextView(context).apply {
            text = "#${layerManager?.getLayers()?.indexOf(layer)?.plus(1) ?: "?"}"
            setTextColor(Color.parseColor("#808090"))
            textSize = 11f
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        row.addView(indexText)

        return row
    }

    /**
     * Cycle through blend modes.
     */
    private fun cycleBlendMode() {
        val layer = layerManager?.getLayer(selectedIndex) ?: return
        val currentMode = layer.blendMode
        val nextMode = BlendMode.entries[(currentMode.ordinal + 1) % BlendMode.entries.size]
        blendModeButton.text = nextMode.name
        onLayerBlendModeChanged?.invoke(selectedIndex, nextMode)
    }

    private fun createButton(context: Context, text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 11f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                margin = dpToPx(2)
            }
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            setOnClickListener { onClick() }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
