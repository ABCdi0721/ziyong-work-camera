package com.workcamera.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoAdapter(
    private val loader: ThumbLoader,
    private val onClick: (WorkPhoto) -> Unit,
    private val onLongClick: (WorkPhoto) -> Unit,
    private val isSelected: (WorkPhoto) -> Boolean
) : RecyclerView.Adapter<PhotoAdapter.PhotoHolder>() {

    private val items = ArrayList<WorkPhoto>()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    /** 选择模式下每张图右上角显示勾选圈，点击切换选中而不是打开大图 */
    var selectionMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submit(list: List<WorkPhoto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun notifySelectionChanged() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
        val photo = items[position]
        holder.time.text = timeFormat.format(Date(photo.takenAt))
        val location = photo.location.trim()
        if (location.isEmpty()) {
            holder.location.visibility = View.GONE
        } else {
            holder.location.visibility = View.VISIBLE
            holder.location.text = location
        }
        loader.loadThumb(photo, 480, holder.image)

        val selected = selectionMode && isSelected(photo)
        holder.badge.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.check.visibility = if (selected) View.VISIBLE else View.GONE
        holder.check.tint = Color.WHITE
        holder.badge.setBackgroundResource(
            if (selected) R.drawable.bg_check_badge else R.drawable.bg_select_ring
        )
        holder.mask.visibility = if (selected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(photo) }
        holder.itemView.setOnLongClickListener {
            onLongClick(photo)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class PhotoHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ShapeableImageView = view.findViewById(R.id.thumbImage)
        val time: TextView = view.findViewById(R.id.itemTime)
        val location: TextView = view.findViewById(R.id.itemLocation)
        val mask: View = view.findViewById(R.id.selectMask)
        val badge: View = view.findViewById(R.id.selectBadge)
        val check: GlyphView = view.findViewById(R.id.selectCheckGlyph)
    }
}
