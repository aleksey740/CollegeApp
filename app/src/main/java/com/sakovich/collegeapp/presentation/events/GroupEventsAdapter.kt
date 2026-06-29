package com.sakovich.collegeapp.presentation.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.GroupEvent

class GroupEventsAdapter(
    private var items: List<GroupEvent>,
    private val canManage: Boolean,
    private val onClick: (GroupEvent) -> Unit
) : RecyclerView.Adapter<GroupEventsAdapter.VH>() {

    fun submit(newItems: List<GroupEvent>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group_event, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val title = v.findViewById<TextView>(R.id.eventTitleText)
        private val date = v.findViewById<TextView>(R.id.eventDateText)
        private val place = v.findViewById<TextView>(R.id.eventPlaceText)
        private val desc = v.findViewById<TextView>(R.id.eventDescriptionText)
        private val hint = v.findViewById<TextView>(R.id.eventEditHint)

        fun bind(item: GroupEvent) {
            title.text = item.title
            date.text = "📅 ${item.date} • ${item.time}"
            place.text = "📍 ${item.place}"
            desc.text = item.description
            hint.text = "✏️ Нажмите для редактирования"
            hint.visibility = if (canManage) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
