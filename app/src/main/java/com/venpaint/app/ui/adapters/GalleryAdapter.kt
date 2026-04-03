package com.venpaint.app.ui.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.venpaint.app.R
import com.venpaint.app.model.Artwork

class GalleryAdapter(
    private val artworks: List<Artwork>,
    private val onArtworkClick: (Artwork) -> Unit,
    private val onArtworkLongClick: (Artwork) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val thumbnailContainer: ViewGroup = view.findViewById(R.id.thumbnailContainer)
        val nameText: TextView = view.findViewById(R.id.artworkName)
        val sizeText: TextView = view.findViewById(R.id.artworkSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_artwork, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artwork = artworks[position]
        holder.nameText.text = artwork.name
        holder.sizeText.text = "${artwork.width} × ${artwork.height}"

        // Make thumbnail square - set height equal to width dynamically
        holder.itemView.post {
            val w = holder.itemView.width
            if (w > 0) {
                val params = holder.thumbnailContainer.layoutParams
                params.height = w
                holder.thumbnailContainer.layoutParams = params
            }
        }

        if (artwork.thumbnailPath != null) {
            val bmp = BitmapFactory.decodeFile(artwork.thumbnailPath)
            if (bmp != null) {
                holder.thumbnail.setImageBitmap(bmp)
            } else {
                holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onArtworkClick(artwork) }
        holder.itemView.setOnLongClickListener {
            onArtworkLongClick(artwork)
            true
        }
    }

    override fun getItemCount(): Int = artworks.size
}
