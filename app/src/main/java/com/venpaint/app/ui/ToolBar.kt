package com.venpaint.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageButton
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.venpaint.app.R

/**
 * Bottom toolbar with tool buttons for the drawing interface.
 */
class ToolBar @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    // Tool IDs
    enum class Tool {
        PEN, PENCIL, ERASER, COLOR_PICKER, LAYERS, UNDO, REDO,
        CLEAR, FILL, SAVE, EXPORT, IMPORT, BRUSH_QR, AIRBRUSH, WATERCOLOR
    }

    // Callbacks
    var onToolSelected: ((Tool) -> Unit)? = null

    // Buttons
    private val toolButtons = mutableMapOf<Tool, ImageButton>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.parseColor("#CC1B1B2F"))
        setPadding(4, 4, 4, 4)

        val buttonSize = dpToPx(44)

        // Left tools
        addToolButton(Tool.PEN, R.drawable.ic_tool_pen, "Pen", buttonSize)
        addToolButton(Tool.PENCIL, R.drawable.ic_tool_pencil, "Pencil", buttonSize)
        addToolButton(Tool.ERASER, R.drawable.ic_tool_eraser, "Eraser", buttonSize)
        addToolButton(Tool.AIRBRUSH, R.drawable.ic_tool_airbrush, "Airbrush", buttonSize)
        addToolButton(Tool.WATERCOLOR, R.drawable.ic_tool_watercolor, "Watercolor", buttonSize)

        // Separator
        addSeparator()

        addToolButton(Tool.COLOR_PICKER, R.drawable.ic_tool_color, "Color", buttonSize)
        addToolButton(Tool.LAYERS, R.drawable.ic_tool_layers, "Layers", buttonSize)
        addToolButton(Tool.FILL, R.drawable.ic_tool_fill, "Fill", buttonSize)

        // Separator
        addSeparator()

        addToolButton(Tool.UNDO, R.drawable.ic_tool_undo, "Undo", buttonSize)
        addToolButton(Tool.REDO, R.drawable.ic_tool_redo, "Redo", buttonSize)

        // Separator
        addSeparator()

        addToolButton(Tool.SAVE, R.drawable.ic_tool_save, "Save", buttonSize)
        addToolButton(Tool.EXPORT, R.drawable.ic_tool_export, "Export", buttonSize)
        addToolButton(Tool.IMPORT, R.drawable.ic_tool_import, "Import", buttonSize)
        addToolButton(Tool.BRUSH_QR, R.drawable.ic_tool_qr, "QR", buttonSize)
    }

    /**
     * Highlight the active tool button.
     */
    fun setActiveTool(tool: Tool?) {
        toolButtons.forEach { (t, button) ->
            if (t == tool) {
                button.setColorFilter(Color.parseColor("#E94560"), PorterDuff.Mode.SRC_IN)
                button.background = getRippleBg()
            } else {
                button.setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.SRC_IN)
                button.background = getSelectableBg()
            }
        }
    }

    /**
     * Enable or disable a tool button.
     */
    fun setToolEnabled(tool: Tool, enabled: Boolean) {
        toolButtons[tool]?.isEnabled = enabled
        toolButtons[tool]?.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun addToolButton(tool: Tool, iconRes: Int, contentDescription: String, size: Int) {
        val button = ImageButton(context).apply {
            layoutParams = LayoutParams(size, size).apply {
                marginStart = 1
                marginEnd = 1
            }
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.SRC_IN)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            this.contentDescription = contentDescription
            setOnClickListener {
                onToolSelected?.invoke(tool)
            }
            background = getSelectableBg()
        }
        toolButtons[tool] = button
        addView(button)
    }

    private fun addSeparator() {
        val separator = View(context).apply {
            layoutParams = LayoutParams(dpToPx(1), dpToPx(28)).apply {
                marginStart = dpToPx(4)
                marginEnd = dpToPx(4)
                gravity = Gravity.CENTER_VERTICAL
            }
            setBackgroundColor(Color.parseColor("#404060"))
        }
        addView(separator)
    }

    private fun getSelectableBg(): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
        return ContextCompat.getDrawable(context, typedValue.resourceId)
    }

    private fun getRippleBg(): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        return ContextCompat.getDrawable(context, typedValue.resourceId)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
