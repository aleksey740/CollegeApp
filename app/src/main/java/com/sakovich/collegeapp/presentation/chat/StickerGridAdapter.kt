package com.sakovich.collegeapp.presentation.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import com.sakovich.collegeapp.R

class StickerGridAdapter(
    private val entries: List<LocalStickerCatalog.StaticEntry>,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<StickerGridAdapter.Holder>() {

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_picker_cell, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = entries[position]
        val uri = "file:///android_asset/${e.assetPath}"
        ImageViewCompat.setImageTintList(holder.image, null)
        holder.image.colorFilter = null
        holder.image.load(uri) {
            crossfade(260)
            size(Size.ORIGINAL)
        }
        holder.itemView.setOnClickListener { onPick(e.id) }
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.stickerPreview)
    }
}
