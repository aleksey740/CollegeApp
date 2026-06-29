package com.sakovich.collegeapp.presentation.curatorial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType

class CuratorialHoursAdapter(
    private var items: List<ScheduleItem> = emptyList(),
    private val onItemClick: (ScheduleItem) -> Unit
) : RecyclerView.Adapter<CuratorialHoursAdapter.ViewHolder>() {

    fun setItems(newItems: List<ScheduleItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_curatorial_hour, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIndicator: View = itemView.findViewById(R.id.typeIndicator)
        private val typeBadge: TextView = itemView.findViewById(R.id.typeBadge)
        private val subjectText: TextView = itemView.findViewById(R.id.subjectText)
        private val dateTimeText: TextView = itemView.findViewById(R.id.dateTimeText)
        private val roomText: TextView = itemView.findViewById(R.id.roomText)

        fun bind(item: ScheduleItem, onItemClick: (ScheduleItem) -> Unit) {
            val isCuratorial = item.type == ScheduleType.CURATOR_HOUR
            typeBadge.text = if (isCuratorial) "Кураторский час" else "Информационный час"

            val colorRes = if (isCuratorial) R.color.consultation_color else R.color.seminar_color
            val color = ContextCompat.getColor(itemView.context, colorRes)
            typeIndicator.setBackgroundColor(color)

            subjectText.text = item.subject.ifBlank { "Без темы" }
            val timeTextValue = if (item.time.isNotBlank()) item.time else "время не указано"
            dateTimeText.text = "📅 ${item.date} • $timeTextValue"
            roomText.text = if (item.room.isNotBlank()) "🏫 ${item.room}" else "🏫 аудитория не указана"

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
