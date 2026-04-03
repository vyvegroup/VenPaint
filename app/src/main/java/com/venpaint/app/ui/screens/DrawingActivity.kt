package com.venpaint.app.ui.screens

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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.venpaint.app.R
import com.venpaint.app.brush.Brush
import com.venpaint.app.brush.BrushManager
import com.venpaint.app.brush.BrushQRGenerator
import com.venpaint.app.brush.BrushQRScanner
import com.venpaint.app.brush.BrushType
import com.venpaint.app.brush.ScanResult
import com.venpaint.app.brush.ScanType
import com.venpaint.app.engine.DrawingHistory
import com.venpaint.app.engine.LayerManager
import com.venpaint.app.io.ArtworkStorage
import com.venpaint.app.io.IpvImporter
import com.venpaint.app.io.ProjectExporter
import com.venpaint.app.io.ProjectSaver
import com.venpaint.app.model.Artwork
import com.venpaint.app.ui.BrushSettingsPanel
import com.venpaint.app.ui.ColorPickerDialog
import com.venpaint.app.ui.DrawingCanvas
import com.venpaint.app.ui.LayerPanel
import com.venpaint.app.ui.ToolBar
import com.venpaint.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DrawingActivity"
        private const val DEFAULT_WIDTH = 1080
        private const val DEFAULT_HEIGHT = 1920
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
    private lateinit var artworkStorage: ArtworkStorage

    // State
    private var activeTool: ToolBar.Tool = ToolBar.Tool.PEN
    private var currentBrush: Brush = Brush.forType(BrushType.PEN)
    private var isLayerPanelVisible = false
    private var hasUnsavedChanges = false

    // Artwork metadata
    private var artworkId: Long = 0
    private var artworkName: String = "Untitled"
    private var canvasWidth: Int = DEFAULT_WIDTH
    private var canvasHeight: Int = DEFAULT_HEIGHT
    private var projectPath: String? = null

    // Activity result launchers
    private lateinit var importFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var qrScanLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        // Read extras
        artworkId = intent.getLongExtra("artwork_id", System.currentTimeMillis())
        artworkName = intent.getStringExtra("name") ?: "Untitled"
        canvasWidth = intent.getIntExtra("width", DEFAULT_WIDTH)
        canvasHeight = intent.getIntExtra("height", DEFAULT_HEIGHT)
        projectPath = intent.getStringExtra("project_path")
        val importUri = intent.getStringExtra("import_uri")

        // Setup toolbar
        val topToolbar = findViewById<MaterialToolbar>(R.id.topToolbar)
        topToolbar.title = artworkName
        topToolbar.setNavigationOnClickListener { handleBackPress() }

        // Initialize managers
        layerManager = LayerManager(canvasWidth, canvasHeight)
        layerManager.initialize()
        drawingHistory = DrawingHistory(layerManager)
        brushManager = BrushManager(this)

        // Initialize IO
        projectSaver = ProjectSaver(this)
        projectExporter = ProjectExporter(this)
        ipvImporter = IpvImporter(this)
        brushQRGenerator = BrushQRGenerator(this)
        brushQRScanner = BrushQRScanner(this)
        artworkStorage = ArtworkStorage(this)

        // Setup views
        setupViews()
        setupActivityResultLaunchers()

        // Load existing project or import
        lifecycleScope.launch {
            if (projectPath != null) {
                loadExistingProject(projectPath!!)
            } else if (importUri != null) {
                importFromUri(Uri.parse(importUri))
            } else if (artworkId != 0L && intent.hasExtra("artwork_id")) {
                // Opening a new artwork from gallery with no saved project yet
                drawingCanvas.initialize(canvasWidth, canvasHeight)
            } else {
                drawingCanvas.initialize(canvasWidth, canvasHeight)
            }
        }
    }

    private suspend fun loadExistingProject(path: String) {
        withContext(Dispatchers.IO) {
            val file = java.io.File(path)
            if (file.exists()) {
                val success = projectSaver.loadProject(file, layerManager)
                if (success) {
                    withContext(Dispatchers.Main) {
                        drawingHistory.clear()
                        drawingCanvas.initialize(canvasWidth, canvasHeight)
                        drawingCanvas.refresh()
                        layerPanel?.refreshLayers()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    drawingCanvas.initialize(canvasWidth, canvasHeight)
                }
            }
        }
    }

    private suspend fun importFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            val bitmap = FileUtils.loadBitmapFromUri(this@DrawingActivity, uri)
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    canvasWidth = bitmap.width
                    canvasHeight = bitmap.height
                    layerManager.resize(canvasWidth, canvasHeight)
                    drawingCanvas.initialize(canvasWidth, canvasHeight)

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
                    hasUnsavedChanges = true
                }
            } else {
                // Try .ipv import
                val result = ipvImporter.importFromUri(uri)
                if (result.success) {
                    withContext(Dispatchers.Main) {
                        drawingHistory.clear()
                        ipvImporter.applyToLayerManager(result, layerManager)
                        drawingCanvas.initialize(canvasWidth, canvasHeight)
                        drawingCanvas.refresh()
                        layerPanel?.refreshLayers()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        drawingCanvas.initialize(canvasWidth, canvasHeight)
                        Toast.makeText(
                            this@DrawingActivity,
                            "Failed to import file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupViews() {
        // Drawing canvas
        drawingCanvas = findViewById(R.id.drawingCanvas)
        drawingCanvas.layerManager = layerManager
        drawingCanvas.currentBrush = currentBrush
        drawingCanvas.initialize(canvasWidth, canvasHeight)

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
        importFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> importFile(uri) }
            }
        }

        qrScanLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val contents = result.data?.getStringExtra("SCAN_RESULT")
                handleQRScanResult(contents)
            }
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchQRScanner()
            } else {
                Toast.makeText(this, R.string.permission_camera, Toast.LENGTH_SHORT).show()
            }
        }

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
            ToolBar.Tool.SAVE -> saveAndExit()
            ToolBar.Tool.EXPORT -> showExportMenu()
            ToolBar.Tool.IMPORT -> showImportDialog()
            ToolBar.Tool.BRUSH_QR -> showBrushQRMenu()
        }
    }

    private fun selectBrushTool(type: BrushType, tool: ToolBar.Tool) {
        activeTool = tool
        toolbar.setActiveTool(tool)

        val brushForType = Brush.forType(type)
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
                        Toast.makeText(
                            this@DrawingActivity,
                            R.string.max_layers_reached,
                            Toast.LENGTH_SHORT
                        ).show()
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
        val bounds = RectF(
            0f, 0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
        com.venpaint.app.engine.BrushEngine().fillArea(canvas, currentBrush.color, bounds)
        drawingCanvas.refresh()
        layerPanel?.refreshLayers()
        hasUnsavedChanges = true
    }

    // ==================== Save ====================

    private fun saveAndExit() {
        val input = EditText(this).apply {
            setText(artworkName)
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
        }

        AlertDialog.Builder(this)
            .setTitle("Save Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                artworkName = input.text.toString().ifBlank { "Untitled" }
                lifecycleScope.launch {
                    val savedFile = withContext(Dispatchers.IO) {
                        projectSaver.saveProject(layerManager, artworkName)
                    }
                    if (savedFile != null) {
                        // Generate thumbnail
                        val thumbnail = withContext(Dispatchers.IO) {
                            generateThumbnail()
                        }

                        // Save artwork metadata
                        withContext(Dispatchers.IO) {
                            val artwork = Artwork(
                                id = artworkId,
                                name = artworkName,
                                width = canvasWidth,
                                height = canvasHeight,
                                projectPath = savedFile.absolutePath,
                                modifiedAt = System.currentTimeMillis()
                            )
                            artworkStorage.saveArtwork(artwork, thumbnail)
                        }

                        hasUnsavedChanges = false
                        Toast.makeText(
                            this@DrawingActivity,
                            "Project saved: ${savedFile.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@DrawingActivity,
                            R.string.msg_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateThumbnail(): Bitmap? {
        val flattened = layerManager.getFlattenedBitmap() ?: return null
        val maxSize = 256
        val ratio = maxSize.toFloat() / maxOf(flattened.width, flattened.height).toFloat()
        val width = (flattened.width * ratio).toInt().coerceAtLeast(1)
        val height = (flattened.height * ratio).toInt().coerceAtLeast(1)
        val thumbnail = Bitmap.createScaledBitmap(flattened, width, height, true)
        if (thumbnail != flattened) {
            flattened.recycle()
        }
        return thumbnail
    }

    // ==================== Export ====================

    private fun showExportMenu() {
        val formats = arrayOf("PNG (Lossless)", "JPG (Lossy)")
        val formatEnums = listOf(
            ProjectExporter.ExportFormat.PNG,
            ProjectExporter.ExportFormat.JPG
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
        val options = arrayOf("Import .ipv (ibisPaint)", "Import Image")

        AlertDialog.Builder(this)
            .setTitle("Import")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFilePicker("application/octet-stream")
                    1 -> openImagePicker()
                }
            }
            .show()
    }

    private fun openFilePicker(mimeType: String) {
        requestStoragePermission {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, "Select file")
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
            extension in listOf("png", "jpg", "jpeg", "webp") -> {
                lifecycleScope.launch { importFromUri(uri) }
            }
            extension == "ipv" -> {
                Toast.makeText(this, "Importing .ipv file...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val result = ipvImporter.importFromUri(uri)
                        withContext(Dispatchers.Main) {
                            if (result.success) {
                                drawingHistory.clear()
                                ipvImporter.applyToLayerManager(result, layerManager)
                                drawingCanvas.refresh()
                                layerPanel?.refreshLayers()
                                Toast.makeText(
                                    this@DrawingActivity,
                                    "Imported ${result.layers.size} layers from .ipv",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@DrawingActivity,
                                    result.error ?: "Failed to import .ipv",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
            else -> {
                Toast.makeText(this, "Unsupported file format", Toast.LENGTH_SHORT).show()
            }
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

        val imageView = ImageView(this).apply {
            setImageBitmap(qrBitmap)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        AlertDialog.Builder(this)
            .setTitle("Brush QR Code")
            .setView(imageView)
            .setPositiveButton("Save") { _, _ ->
                val file = FileUtils.saveBitmap(
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

    // ==================== Permissions ====================

    private fun requestStoragePermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    private fun handleBackPress() {
        if (isLayerPanelVisible) {
            toggleLayerPanel()
            return
        }

        if (hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Save before exiting?")
                .setMessage("You have unsaved changes. Save now?")
                .setPositiveButton("Save & Exit") { _, _ -> saveAndExit() }
                .setNegativeButton("Discard") { _, _ -> finish() }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        handleBackPress()
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
