package com.venpaint.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.venpaint.app.R
import com.venpaint.app.io.ArtworkStorage
import com.venpaint.app.model.Artwork
import com.venpaint.app.ui.adapters.GalleryAdapter
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_IMPORT = 1001
        private const val DEFAULT_WIDTH = 1080
        private const val DEFAULT_HEIGHT = 1920
    }

    private lateinit var storage: ArtworkStorage
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var adapter: GalleryAdapter
    private val artworks: MutableList<Artwork> = mutableListOf()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val intent = Intent(this, DrawingActivity::class.java)
                intent.putExtra("import_uri", uri.toString())
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        storage = ArtworkStorage(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "VenPaint"
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = GalleryAdapter(
            artworks,
            onArtworkClick = { artwork -> openArtwork(artwork) },
            onArtworkLongClick = { artwork -> showArtworkOptions(artwork) }
        )
        recyclerView.adapter = adapter

        emptyView = findViewById(R.id.emptyView)

        val fabNew = findViewById<FloatingActionButton>(R.id.fabNewCanvas)
        fabNew.setOnClickListener { showNewCanvasDialog() }

        val btnImport = findViewById<ImageButton>(R.id.btnImport)
        btnImport.setOnClickListener { importArtwork() }

        val btnLibrary = findViewById<ImageButton>(R.id.btnLibrary)
        btnLibrary.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        loadArtworks()
    }

    override fun onResume() {
        super.onResume()
        loadArtworks()
    }

    private fun loadArtworks() {
        lifecycleScope.launch {
            artworks.clear()
            artworks.addAll(storage.loadArtworks())
            adapter.notifyDataSetChanged()
            emptyView.visibility = if (artworks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openArtwork(artwork: Artwork) {
        val intent = Intent(this, DrawingActivity::class.java)
        intent.putExtra("artwork_id", artwork.id)
        intent.putExtra("project_path", artwork.projectPath)
        intent.putExtra("width", artwork.width)
        intent.putExtra("height", artwork.height)
        intent.putExtra("name", artwork.name)
        startActivity(intent)
    }

    private fun showArtworkOptions(artwork: Artwork) {
        MaterialAlertDialogBuilder(this)
            .setTitle(artwork.name)
            .setItems(arrayOf("Open", "Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> openArtwork(artwork)
                    1 -> showRenameDialog(artwork)
                    2 -> confirmDelete(artwork)
                }
            }
            .show()
    }

    private fun showRenameDialog(artwork: Artwork) {
        val input = EditText(this)
        input.setText(artwork.name)
        input.setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
        input.setTextColor(getColor(R.color.venpaint_text_primary))
        input.setHintTextColor(getColor(R.color.venpaint_text_hint))

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Artwork")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().ifBlank { "Untitled" }
                lifecycleScope.launch {
                    val renamed = artwork.copy(
                        name = newName,
                        modifiedAt = System.currentTimeMillis()
                    )
                    val thumb = if (artwork.thumbnailPath != null) {
                        BitmapFactory.decodeFile(artwork.thumbnailPath)
                    } else {
                        null
                    }
                    storage.saveArtwork(renamed, thumb)
                    loadArtworks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(artwork: Artwork) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Artwork")
            .setMessage("Are you sure you want to delete \"${artwork.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    storage.deleteArtwork(artwork.id)
                    loadArtworks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewCanvasDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_canvas, null)
        val editWidth = dialogView.findViewById<EditText>(R.id.editWidth)
        val editHeight = dialogView.findViewById<EditText>(R.id.editHeight)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val presetsGrid = dialogView.findViewById<GridLayout>(R.id.presetsGrid)

        editWidth.setText(DEFAULT_WIDTH.toString())
        editHeight.setText(DEFAULT_HEIGHT.toString())
        editName.setText("Untitled")

        val presets = listOf(
            "1080x1920", "1920x1080", "2048x2048",
            "1080x1080", "2480x3508", "2000x2000"
        )
        val labels = listOf(
            "9:16 Portrait", "16:9 Landscape", "Square",
            "1:1 Social", "A4 Print", "Square XL"
        )
        presets.forEachIndexed { index, preset ->
            val btn = Button(dialogView.context).apply {
                text = "${labels[index]}\n$preset"
                textSize = 11f
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                setBackgroundColor(getColor(R.color.venpaint_surface_light))
                setTextColor(getColor(R.color.venpaint_text_primary))
            }
            btn.setOnClickListener {
                val parts = preset.split("x")
                editWidth.setText(parts[0])
                editHeight.setText(parts[1])
            }
            presetsGrid.addView(btn)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("New Canvas")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val w = editWidth.text.toString().toIntOrNull() ?: DEFAULT_WIDTH
                val h = editHeight.text.toString().toIntOrNull() ?: DEFAULT_HEIGHT
                val name = editName.text.toString().ifBlank { "Untitled" }
                val intent = Intent(this, DrawingActivity::class.java)
                intent.putExtra("width", w)
                intent.putExtra("height", h)
                intent.putExtra("name", name)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importArtwork() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/*", "application/octet-stream", "application/zip")
            )
        }
        importLauncher.launch(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
