package com.venpaint.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.venpaint.app.brush.Brush
import com.venpaint.app.brush.BrushManager
import com.venpaint.app.brush.BrushQRGenerator
import com.venpaint.app.brush.BrushQRScanner
import com.venpaint.app.brush.BrushType
import com.venpaint.app.brush.ScanResult
import com.venpaint.app.brush.ScanType
import com.venpaint.app.engine.BlendMode
import com.venpaint.app.engine.DrawingHistory
import com.venpaint.app.engine.LayerManager
import com.venpaint.app.io.IpvImporter
import com.venpaint.app.io.ProjectExporter
import com.venpaint.app.io.ProjectSaver
import com.venpaint.app.ui.BrushSettingsPanel
import com.venpaint.app.ui.ColorPickerDialog
import com.venpaint.app.ui.DrawingCanvas
import com.venpaint.app.ui.LayerPanel
import com.venpaint.app.ui.ToolBar

/**
 * Main activity for VenPaint - the Android painting app.
 * Manages all UI components, user interactions, and coordinates
 * between the drawing engine, layer system, and IO operations.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VenPaint"
        private const val DEFAULT_CANVAS_WIDTH = 1080
        private const val DEFAULT_CANVAS_HEIGHT = 1920

        // Request codes
        private const val REQUEST_IMPORT_FILE = 1001
        private const val REQUEST_CAMERA_PERMISSION = 1002
    }

    // Canvas and drawing
    private lateinit var drawingCanvas: DrawingCanvas
    private lateinit var layerManager: LayerManager
    private lateinit var drawingHistory: DrawingHistory
    private lateinit var brushManager: BrushManager

    // UI
    private lateinit var toolbar: ToolBar
    private lateinit var brushSettingsPanel: BrushSettingsPanel
    private var layerPanel: LayerPanel? = null

    // IO
    private lateinit var projectSaver: ProjectSaver
    private lateinit var projectExporter: ProjectExporter
    private lateinit var ipvImporter: IpvImporter
    private lateinit var brushQRGenerator: BrushQRGenerator
    private lateinit var brushQRScanner: BrushQRScanner

    // State
    private var activeTool: ToolBar.Tool = ToolBar.Tool.PEN
    private var currentBrush: Brush = Brush.forType(BrushType.PEN)
    private var isLayerPanelVisible = false
    private var hasUnsavedChanges = false

    // Activity result launchers
    private lateinit var importFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var qrScanLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup fullscreen immersive mode
        setupImmersiveMode()

        // Initialize managers
        layerManager = LayerManager(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)
        layerManager.initialize()
        drawingHistory = DrawingHistory(layerManager)
        brushManager = BrushManager(this)

        // Initialize IO
        projectSaver = ProjectSaver(this)
        projectExporter = ProjectExporter(this)
        ipvImporter = IpvImporter(this)
        brushQRGenerator = BrushQRGenerator(this)
        brushQRScanner = BrushQRScanner(this)

        // Setup views
        setupViews()
        setupActivityResultLaunchers()

        // Handle incoming intent (e.g., opening .ipv file)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    // ==================== Setup ====================

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
        supportActionBar?.hide()
    }

    private fun setupViews() {
        // Drawing canvas
        drawingCanvas = findViewById(R.id.drawingCanvas)
        drawingCanvas.layerManager = layerManager
        drawingCanvas.currentBrush = currentBrush
        drawingCanvas.initialize(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)

        drawingCanvas.onDrawStart = {
            drawingHistory.saveState("Stroke")
            hasUnsavedChanges = true
        }

        drawingCanvas.onDrawEnd = {
            updateUndoRedoButtons()
            layerPanel?.refreshLayers()
        }

        // Toolbar
        toolbar = findViewById(R.id.toolbar)
        toolbar.onToolSelected = { tool -> handleToolSelected(tool) }
        toolbar.setActiveTool(activeTool)

        // Brush settings panel
        brushSettingsPanel = findViewById(R.id.brushSettingsPanel)
        brushSettingsPanel.setBrush(currentBrush)

        brushSettingsPanel.onBrushChanged = { brush ->
            currentBrush = brush
            drawingCanvas.currentBrush = brush
        }

        brushSettingsPanel.onBrushTypeSelected = { type ->
            currentBrush = brushSettingsPanel.getBrush()
            drawingCanvas.currentBrush = currentBrush
            activeTool = when (type) {
                BrushType.ERASER -> ToolBar.Tool.ERASER
                BrushType.AIRBRUSH -> ToolBar.Tool.AIRBRUSH
                BrushType.WATERCOLOR -> ToolBar.Tool.WATERCOLOR
                BrushType.PENCIL -> ToolBar.Tool.PENCIL
                else -> ToolBar.Tool.PEN
            }
            toolbar.setActiveTool(activeTool)
        }

        // Layer panel container
        val layerPanelContainer = findViewById<FrameLayout>(R.id.layerPanelContainer)
        layerPanelContainer.visibility = View.GONE

        // Setup initial undo/redo state
        updateUndoRedoButtons()
    }

    private fun setupActivityResultLaunchers() {
        // File import
        importFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importFile(uri)
                }
            }
        }

        // QR scan
        qrScanLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val contents = result.data?.getStringExtra("SCAN_RESULT")
                handleQRScanResult(contents)
            }
        }

        // Camera permission
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchQRScanner()
            } else {
                Toast.makeText(this, R.string.permission_camera, Toast.LENGTH_SHORT).show()
            }
        }

        // Storage permission
        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                Toast.makeText(this, R.string.permission_storage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== Tool Handling ====================

    private fun handleToolSelected(tool: ToolBar.Tool) {
        when (tool) {
            ToolBar.Tool.PEN -> selectBrushTool(BrushType.PEN, tool)
            ToolBar.Tool.PENCIL -> selectBrushTool(BrushType.PENCIL, tool)
            ToolBar.Tool.ERASER -> selectBrushTool(BrushType.ERASER, tool)
            ToolBar.Tool.AIRBRUSH -> selectBrushTool(BrushType.AIRBRUSH, tool)
            ToolBar.Tool.WATERCOLOR -> selectBrushTool(BrushType.WATERCOLOR, tool)
            ToolBar.Tool.COLOR_PICKER -> showColorPicker()
            ToolBar.Tool.LAYERS -> toggleLayerPanel()
            ToolBar.Tool.UNDO -> performUndo()
            ToolBar.Tool.REDO -> performRedo()
            ToolBar.Tool.CLEAR -> clearActiveLayer()
            ToolBar.Tool.FILL -> fillActiveLayer()
            ToolBar.Tool.SAVE -> saveProject()
            ToolBar.Tool.EXPORT -> showExportMenu()
            ToolBar.Tool.IMPORT -> showImportDialog()
            ToolBar.Tool.BRUSH_QR -> showBrushQRMenu()
        }
    }

    private fun selectBrushTool(type: BrushType, tool: ToolBar.Tool) {
        activeTool = tool
        toolbar.setActiveTool(tool)

        val brushForType = Brush.forType(type)
        // Preserve current color unless it's eraser
        if (type != BrushType.ERASER) {
            brushForType.color = currentBrush.color
        }
        currentBrush = brushForType
        drawingCanvas.currentBrush = currentBrush
        brushSettingsPanel.setBrush(currentBrush)
    }

    // ==================== Color Picker ====================

    private fun showColorPicker() {
        ColorPickerDialog(this, currentBrush.color) { color ->
            currentBrush.color = color
            brushSettingsPanel.setColor(color)
            drawingCanvas.currentBrush = currentBrush
        }.show()
    }

    // ==================== Layer Panel ====================

    private fun toggleLayerPanel() {
        val container = findViewById<FrameLayout>(R.id.layerPanelContainer)

        if (isLayerPanelVisible) {
            container.visibility = View.GONE
            container.removeAllViews()
            layerPanel = null
            isLayerPanelVisible = false
        } else {
            // Dismiss brush settings side panel if open
            val brushContainer = findViewById<FrameLayout>(R.id.brushSettingsContainer)
            brushContainer.visibility = View.GONE
            brushContainer.removeAllViews()

            container.visibility = View.VISIBLE
            layerPanel = LayerPanel(this).apply {
                setLayerManager(layerManager)
                setSelectedIndex(layerManager.activeLayerIndex)

                onLayerSelected = { index ->
                    layerManager.setActiveLayer(index)
                    refreshLayerPanel()
                }
                onLayerVisibilityChanged = { index ->
                    layerManager.toggleVisibility(index)
                    drawingCanvas.refresh()
                }
                onLayerOpacityChanged = { index, opacity ->
                    layerManager.setLayerOpacity(index, opacity)
                    drawingCanvas.refresh()
                    refreshLayerPanel()
                }
                onLayerBlendModeChanged = { index, blendMode ->
                    layerManager.setLayerBlendMode(index, blendMode)
                    drawingCanvas.refresh()
                }
                onLayerAdded = {
                    if (layerManager.canAddLayer()) {
                        drawingHistory.saveState("Add Layer")
                        layerManager.addLayer()
                        drawingCanvas.refresh()
                        refreshLayerPanel()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.max_layers_reached, Toast.LENGTH_SHORT).show()
                    }
                }
                onLayerDeleted = { index ->
                    drawingHistory.saveState("Delete Layer")
                    layerManager.removeLayer(index)
                    drawingCanvas.refresh()
                    refreshLayerPanel()
                }
                onLayerDuplicated = {
                    drawingHistory.saveState("Duplicate Layer")
                    layerManager.duplicateActiveLayer()
                    drawingCanvas.refresh()
                    refreshLayerPanel()
                }
                onLayerMergedDown = {
                    drawingHistory.saveState("Merge Down")
                    if (layerManager.mergeDown()) {
                        drawingCanvas.refresh()
                        refreshLayerPanel()
                    }
                }
            }
            container.addView(layerPanel)
            isLayerPanelVisible = true
        }
    }

    private fun refreshLayerPanel() {
        layerPanel?.let {
            it.setLayerManager(layerManager)
            it.setSelectedIndex(layerManager.activeLayerIndex)
        }
    }

    // ==================== Undo/Redo ====================

    private fun performUndo() {
        if (drawingHistory.undo()) {
            drawingCanvas.refresh()
            layerPanel?.refreshLayers()
            updateUndoRedoButtons()
            hasUnsavedChanges = true
        }
    }

    private fun performRedo() {
        if (drawingHistory.redo()) {
            drawingCanvas.refresh()
            layerPanel?.refreshLayers()
            updateUndoRedoButtons()
            hasUnsavedChanges = true
        }
    }

    private fun updateUndoRedoButtons() {
        toolbar.setToolEnabled(ToolBar.Tool.UNDO, drawingHistory.canUndo)
        toolbar.setToolEnabled(ToolBar.Tool.REDO, drawingHistory.canRedo)
    }

    // ==================== Clear & Fill ====================

    private fun clearActiveLayer() {
        AlertDialog.Builder(this)
            .setTitle("Clear Layer")
            .setMessage("Clear the active layer? This can be undone.")
            .setPositiveButton("Clear") { _, _ ->
                drawingHistory.saveState("Clear Layer")
                drawingCanvas.clearActiveLayer()
                layerPanel?.refreshLayers()
                hasUnsavedChanges = true
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fillActiveLayer() {
        drawingHistory.saveState("Fill Layer")
        val layer = layerManager.activeLayer ?: return
        val canvas = layer.getCanvas() ?: return
        val bounds = RectF(0f, 0f, layerManager.getLayer(0)?.bitmap?.width?.toFloat() ?: DEFAULT_CANVAS_WIDTH.toFloat(),
            layerManager.getLayer(0)?.bitmap?.height?.toFloat() ?: DEFAULT_CANVAS_HEIGHT.toFloat())
        com.venpaint.app.engine.BrushEngine().fillArea(canvas, currentBrush.color, bounds)
        drawingCanvas.refresh()
        layerPanel?.refreshLayers()
        hasUnsavedChanges = true
    }

    // ==================== Save ====================

    private fun saveProject() {
        val input = android.widget.EditText(this).apply {
            setText("Untitled")
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        AlertDialog.Builder(this)
            .setTitle("Save Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().ifBlank { "Untitled" }
                val file = projectSaver.saveProject(layerManager, name)
                if (file != null) {
                    Toast.makeText(this, "Project saved: ${file.name}", Toast.LENGTH_SHORT).show()
                    hasUnsavedChanges = false
                } else {
                    Toast.makeText(this, R.string.msg_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================== Export ====================

    private fun showExportMenu() {
        val formats = arrayOf("PNG (Lossless)", "JPG (Lossy)", "PSD (Photoshop)")
        val formatEnums = listOf(
            ProjectExporter.ExportFormat.PNG,
            ProjectExporter.ExportFormat.JPG,
            ProjectExporter.ExportFormat.PSD
        )

        AlertDialog.Builder(this)
            .setTitle("Export Image")
            .setItems(formats) { _, which ->
                requestStoragePermission {
                    exportAs(formatEnums[which])
                }
            }
            .show()
    }

    private fun exportAs(format: ProjectExporter.ExportFormat) {
        val result = projectExporter.exportImage(layerManager, format)
        if (result.success) {
            val location = result.uri?.toString() ?: result.file?.absolutePath ?: "Unknown"
            Toast.makeText(this, "Exported as ${format.extension}\n$location", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, result.error ?: "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Import ====================

    private fun showImportDialog() {
        val options = arrayOf("Import .ipv (ibisPaint)", "Import Image", "Open VenPaint Project (.vpp)")

        AlertDialog.Builder(this)
            .setTitle("Import")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFilePicker("application/octet-stream", "ipv")
                    1 -> openImagePicker()
                    2 -> openFilePicker("application/octet-stream", "vpp")
                }
            }
            .show()
    }

    private fun openFilePicker(mimeType: String, extension: String) {
        requestStoragePermission {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, "Select .$extension file")
            }
            importFileLauncher.launch(intent)
        }
    }

    private fun openImagePicker() {
        requestStoragePermission {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            importFileLauncher.launch(intent)
        }
    }

    private fun importFile(uri: Uri) {
        val filename = uri.lastPathSegment ?: ""
        val extension = filename.substringAfterLast('.', "").lowercase()

        when {
            extension == "vpp" -> importVenPaintProject(uri)
            extension == "ipv" -> importIpvFile(uri)
            extension in listOf("png", "jpg", "jpeg", "webp") -> importImage(uri)
            else -> {
                // Try .ipv first, then image
                val result = ipvImporter.importFromUri(uri)
                if (result.success) {
                    drawingHistory.clear()
                    ipvImporter.applyToLayerManager(result, layerManager)
                    drawingCanvas.refresh()
                    layerPanel?.refreshLayers()
                    Toast.makeText(this, "File imported", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Unsupported file format", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importVenPaintProject(uri: Uri) {
        val bytes = com.venpaint.app.util.FileUtils.readBytesFromUri(this, uri)
        if (bytes == null) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
            return
        }

        val success = projectSaver.loadProjectFromBytes(bytes, layerManager)
        if (success) {
            drawingHistory.clear()
            drawingCanvas.refresh()
            layerPanel?.refreshLayers()
            Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to load project", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importIpvFile(uri: Uri) {
        Toast.makeText(this, "Importing .ipv file...", Toast.LENGTH_SHORT).show()

        Thread {
            val result = ipvImporter.importFromUri(uri)
            runOnUiThread {
                if (result.success) {
                    drawingHistory.clear()
                    ipvImporter.applyToLayerManager(result, layerManager)
                    drawingCanvas.refresh()
                    layerPanel?.refreshLayers()
                    Toast.makeText(this, "Imported ${result.layers.size} layers from .ipv", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, result.error ?: "Failed to import .ipv", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun importImage(uri: Uri) {
        val bitmap = com.venpaint.app.util.FileUtils.loadBitmapFromUri(this, uri)
        if (bitmap != null) {
            drawingHistory.saveState("Import Image")

            // Resize canvas to match imported image
            layerManager.resize(bitmap.width, bitmap.height)
            drawingCanvas.initialize(bitmap.width, bitmap.height)

            // Draw on active layer
            val layer = layerManager.activeLayer
            if (layer != null) {
                val canvas = layer.getCanvas()
                if (canvas != null) {
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                }
            }

            drawingCanvas.refresh()
            layerPanel?.refreshLayers()
            bitmap.recycle()
            Toast.makeText(this, "Image imported", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Brush QR ====================

    private fun showBrushQRMenu() {
        val options = arrayOf("Generate Brush QR Code", "Scan Brush QR Code")

        AlertDialog.Builder(this)
            .setTitle("Brush QR")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> generateBrushQR()
                    1 -> scanBrushQR()
                }
            }
            .show()
    }

    private fun generateBrushQR() {
        val qrBitmap = brushQRGenerator.generateQR(currentBrush)
        if (qrBitmap == null) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            return
        }

        // Show QR in a dialog
        val imageView = ImageView(this).apply {
            setImageBitmap(qrBitmap)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        AlertDialog.Builder(this)
            .setTitle("Brush QR Code")
            .setView(imageView)
            .setPositiveButton("Save") { _, _ ->
                val file = com.venpaint.app.util.FileUtils.saveBitmap(
                    this, qrBitmap,
                    Bitmap.CompressFormat.PNG, "png"
                )
                if (file != null) {
                    Toast.makeText(this, "QR saved: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun scanBrushQR() {
        // Check camera permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchQRScanner()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            launchQRScanner()
        }
    }

    private fun launchQRScanner() {
        try {
            val intent = Intent(this, com.journeyapps.barcodescanner.CaptureActivity::class.java).apply {
                putExtra("SCAN_FRAME", true)
            }
            qrScanLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "QR scanner not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleQRScanResult(contents: String?) {
        val scanResult = brushQRScanner.parseScanResult(contents)

        when (scanResult.type) {
            ScanType.VENPAINT_BRUSH -> {
                scanResult.brush?.let { brush ->
                    currentBrush = brush
                    brushSettingsPanel.setBrush(brush)
                    drawingCanvas.currentBrush = brush
                    Toast.makeText(this, "Brush imported: ${brush.name}", Toast.LENGTH_SHORT).show()
                }
            }
            ScanType.IBISPAINT_BRUSH -> {
                AlertDialog.Builder(this)
                    .setTitle("ibisPaint Brush")
                    .setMessage("This is an ibisPaint brush QR code.\nOpen in browser to view details?")
                    .setPositiveButton("Open") { _, _ ->
                        scanResult.metadata?.let { brushQRScanner.openIbisPaintUrl(it) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            ScanType.IBISPAINT_LINK -> {
                scanResult.metadata?.let { brushQRScanner.openIbisPaintUrl(it) }
            }
            ScanType.UNKNOWN -> {
                Toast.makeText(this, "Unknown QR format", Toast.LENGTH_SHORT).show()
            }
            ScanType.INVALID -> {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
            }
            ScanType.ERROR -> {
                Toast.makeText(this, scanResult.metadata ?: "Error scanning QR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== Intent Handling ====================

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    importFile(uri)
                }
            }
        }
    }

    // ==================== Permissions ====================

    private fun requestStoragePermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No storage permission needed on Android 10+
            onGranted()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasPermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    onGranted()
                } else {
                    storagePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    )
                }
            } else {
                onGranted()
            }
        }
    }

    // ==================== Back Press ====================

    override fun onBackPressed() {
        if (isLayerPanelVisible) {
            toggleLayerPanel()
            return
        }

        if (hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Exit VenPaint")
                .setMessage("You have unsaved changes. Exit anyway?")
                .setPositiveButton("Exit") { _, _ ->
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        layerManager.recycleAll()
        drawingHistory.clear()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
