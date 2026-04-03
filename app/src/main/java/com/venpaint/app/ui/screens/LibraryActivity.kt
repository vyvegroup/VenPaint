package com.venpaint.app.ui.screens

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.venpaint.app.R
import com.venpaint.app.brush.Brush
import com.venpaint.app.brush.BrushManager
import com.venpaint.app.brush.BrushType
import com.venpaint.app.io.ArtworkStorage
import com.venpaint.app.model.Folder
import com.venpaint.app.model.FxCategory
import com.venpaint.app.model.FxEffect
import com.venpaint.app.model.FxParameter
import com.venpaint.app.model.FxPresets
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Library"
        toolbar.setNavigationOnClickListener { finish() }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        val adapter = LibraryPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Brushes"
                1 -> "FX"
                else -> "My Presets"
            }
        }.attach()
    }
}

class LibraryPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BrushLibraryFragment()
            1 -> FxLibraryFragment()
            2 -> MyPresetsFragment()
            else -> BrushLibraryFragment()
        }
    }
}

// ==================== Brush Library Fragment ====================

class BrushLibraryFragment : Fragment() {

    private lateinit var brushManager: BrushManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BrushListAdapter

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_library_list, container, false) as FrameLayout
        recyclerView = RecyclerView(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setPadding(8, 8, 8, 8)

        brushManager = BrushManager(requireContext())
        val allBrushes = brushManager.getAllBrushes()
        adapter = BrushListAdapter(allBrushes) { brush ->
            showBrushDetailDialog(brush)
        }
        recyclerView.adapter = adapter

        root.addView(recyclerView)
        return root
    }
}

class BrushListAdapter(
    private val brushes: List<Brush>,
    private val onBrushClick: (Brush) -> Unit
) : RecyclerView.Adapter<BrushListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView
        val typeText: TextView
        val sizeText: TextView

        init {
            nameText = view.findViewById(android.R.id.text1)
            typeText = view.findViewById(android.R.id.text2)
            sizeText = TextView(view.context).apply {
                setPadding(8, 0, 8, 0)
                textSize = 12f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val dp = context.resources.displayMetrics.density

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            minimumHeight = (64 * dp).toInt()
            setBackgroundColor(0xFF2A2A3D.toInt())
            id = android.R.id.text1
        }

        val nameTv = TextView(context).apply {
            id = android.R.id.text1
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val typeTv = TextView(context).apply {
            id = android.R.id.text2
            textSize = 12f
            setTextColor(0xFF808090.toInt())
        }
        item.addView(nameTv)
        item.addView(typeTv)
        item.tag = typeTv

        return ViewHolder(item)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val brush = brushes[position]
        val parent = holder.itemView as LinearLayout

        val nameTv = parent.getChildAt(0) as TextView
        val typeTv = parent.getChildAt(1) as TextView

        nameTv.text = brush.name
        typeTv.text = "${brush.type.displayName} | Size: ${brush.size.toInt()} | Opacity: ${(brush.opacity * 100).toInt()}%"

        holder.itemView.setOnClickListener { onBrushClick(brush) }
    }

    override fun getItemCount(): Int = brushes.size
}

private fun BrushLibraryFragment.showBrushDetailDialog(brush: Brush) {
    val context = context ?: return
    val dp = context.resources.displayMetrics.density

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
    }

    val fields = listOf(
        "Name" to brush.name,
        "Type" to brush.type.displayName,
        "Size" to brush.size.toString(),
        "Opacity" to (brush.opacity * 100).toString() + "%",
        "Hardness" to (brush.hardness * 100).toString() + "%",
        "Spacing" to brush.spacing.toString(),
        "Color" to String.format("#%08X", brush.color)
    )

    for ((label, value) in fields) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val labelTv = TextView(context).apply {
            text = "$label: "
            textSize = 14f
            setTextColor(0xFFB0B0C0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        }
        val valueTv = TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }
        row.addView(labelTv)
        row.addView(valueTv)
        layout.addView(row)
    }

    // Color preview
    val colorPreview = View(context).apply {
        setBackgroundColor(brush.color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * dp).toInt()).apply {
            topMargin = (8 * dp).toInt()
        }
    }
    layout.addView(colorPreview)

    AlertDialog.Builder(context)
        .setTitle(brush.name)
        .setView(layout)
        .setPositiveButton("Close", null)
        .show()
}

// ==================== FX Library Fragment ====================

class FxLibraryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FxListAdapter

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_library_list, container, false) as FrameLayout
        recyclerView = RecyclerView(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setPadding(8, 8, 8, 8)

        adapter = FxListAdapter(FxPresets.effects) { effect ->
            showFxDetailDialog(effect)
        }
        recyclerView.adapter = adapter

        root.addView(recyclerView)
        return root
    }
}

class FxListAdapter(
    private val effects: List<FxEffect>,
    private val onFxClick: (FxEffect) -> Unit
) : RecyclerView.Adapter<FxListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val dp = context.resources.displayMetrics.density

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            minimumHeight = (64 * dp).toInt()
            setBackgroundColor(0xFF2A2A3D.toInt())
        }

        val nameTv = TextView(context).apply {
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            tag = "name"
        }
        val descTv = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF808090.toInt())
            tag = "desc"
        }
        item.addView(nameTv)
        item.addView(descTv)

        return ViewHolder(item)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val effect = effects[position]
        val parent = holder.itemView as LinearLayout

        val nameTv = parent.getChildAt(0) as TextView
        val descTv = parent.getChildAt(1) as TextView

        nameTv.text = effect.name
        descTv.text = "${effect.category.name} - ${effect.parameters.size} params"

        holder.itemView.setOnClickListener { onFxClick(effect) }
    }

    override fun getItemCount(): Int = effects.size
}

private fun FxLibraryFragment.showFxDetailDialog(effect: FxEffect) {
    val context = context ?: return
    val dp = context.resources.displayMetrics.density

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
    }

    val categoryTv = TextView(context).apply {
        text = "Category: ${effect.category.name}"
        textSize = 14f
        setTextColor(0xFFB0B0C0.toInt())
    }
    layout.addView(categoryTv)

    if (effect.description.isNotBlank()) {
        val descTv = TextView(context).apply {
            text = effect.description
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        layout.addView(descTv)
    }

    if (effect.parameters.isNotEmpty()) {
        val paramsTitle = TextView(context).apply {
            text = "Parameters:"
            textSize = 14f
            setTextColor(0xFFB0B0C0.toInt())
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }
        layout.addView(paramsTitle)

        for (param in effect.parameters) {
            val paramTv = TextView(context).apply {
                text = "  ${param.name}: ${param.default} (range: ${param.min} - ${param.max})"
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
            }
            layout.addView(paramTv)
        }
    }

    AlertDialog.Builder(context)
        .setTitle(effect.name)
        .setView(layout)
        .setPositiveButton("Close", null)
        .show()
}

// ==================== My Presets Fragment ====================

class MyPresetsFragment : Fragment() {

    private lateinit var artworkStorage: ArtworkStorage
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FolderListAdapter

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_library_list, container, false) as FrameLayout

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        val header = TextView(requireContext()).apply {
            text = "Custom Folders"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 16)
        }
        layout.addView(header)

        val addFolderBtn = TextView(requireContext()).apply {
            text = "+ Create New Folder"
            textSize = 16f
            setTextColor(0xFFE94560.toInt())
            setPadding(0, 8, 0, 16)
            setOnClickListener { showCreateFolderDialog() }
        }
        layout.addView(addFolderBtn)

        recyclerView = RecyclerView(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        layout.addView(recyclerView)

        artworkStorage = ArtworkStorage(requireContext())
        adapter = FolderListAdapter(emptyList()) { folder ->
            showFolderOptions(folder)
        }
        recyclerView.adapter = adapter

        loadFolders()

        root.addView(layout)
        return root
    }

    private fun loadFolders() {
        lifecycleScope.launch {
            val folders = artworkStorage.loadAllFolders()
            adapter.folders = folders
            adapter.notifyDataSetChanged()
        }
    }

    private fun showCreateFolderDialog() {
        val context = context ?: return
        val input = EditText(context)
        input.hint = "Folder name"

        MaterialAlertDialogBuilder(context)
            .setTitle("Create Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().ifBlank { "New Folder" }
                lifecycleScope.launch {
                    val folder = Folder(name = name)
                    artworkStorage.saveFolder(folder)
                    loadFolders()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFolderOptions(folder: Folder) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(folder.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameFolderDialog(folder)
                    1 -> {
                        lifecycleScope.launch {
                            artworkStorage.deleteFolder(folder.id)
                            loadFolders()
                        }
                    }
                }
            }
            .show()
    }

    private fun showRenameFolderDialog(folder: Folder) {
        val context = context ?: return
        val input = EditText(context)
        input.setText(folder.name)

        MaterialAlertDialogBuilder(context)
            .setTitle("Rename Folder")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().ifBlank { folder.name }
                lifecycleScope.launch {
                    artworkStorage.saveFolder(folder.copy(name = newName))
                    loadFolders()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class FolderListAdapter(
    var folders: List<Folder>,
    private val onFolderClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val dp = context.resources.displayMetrics.density

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            minimumHeight = (56 * dp).toInt()
            setBackgroundColor(0xFF2A2A3D.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(0xFFB0B0C0.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
        }
        item.addView(icon)

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameTv = TextView(context).apply {
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            tag = "name"
        }
        val typeTv = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF808090.toInt())
            tag = "type"
        }
        textLayout.addView(nameTv)
        textLayout.addView(typeTv)
        item.addView(textLayout)

        return ViewHolder(item)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        val parent = holder.itemView as LinearLayout
        val textLayout = parent.getChildAt(1) as LinearLayout

        val nameTv = textLayout.getChildAt(0) as TextView
        val typeTv = textLayout.getChildAt(1) as TextView

        nameTv.text = folder.name
        typeTv.text = "Type: ${folder.type.name}"

        holder.itemView.setOnClickListener { onFolderClick(folder) }
    }

    override fun getItemCount(): Int = folders.size
}
