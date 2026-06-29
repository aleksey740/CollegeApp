package com.sakovich.collegeapp.presentation.teacher.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.TeacherHourRecord
import com.sakovich.collegeapp.data.models.TeacherHourType

class TeacherHoursAdapter(
    private var items: List<TeacherHourRecord>,
    private val onItemClick: (TeacherHourRecord) -> Unit
) : RecyclerView.Adapter<TeacherHoursAdapter.RecordViewHolder>() {

    class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeBadgeText: TextView = view.findViewById(R.id.typeBadgeText)
        val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        val topicText: TextView = view.findViewById(R.id.topicText)
        val detailsText: TextView = view.findViewById(R.id.detailsText)
        val notesText: TextView = view.findViewById(R.id.notesText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_hour, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val item = items[position]
        val isTeaching = item.type == TeacherHourType.TEACHING
        holder.typeBadgeText.text = if (isTeaching) "Учебные" else "Кураторские"
        holder.typeBadgeText.setBackgroundColor(
            if (isTeaching) Color.parseColor("#8B5CF6") else Color.parseColor("#8B5CF6")
        )

        holder.dateTimeText.text = "${item.date} ${item.time}"
        holder.topicText.text = item.topic
        holder.detailsText.text = "Группа: ${item.groupName} • Часы: ${item.hoursCount} • Присутствовали: ${item.attendanceCount}"

        if (item.notes.isNotBlank()) {
            holder.notesText.text = item.notes
            holder.notesText.visibility = View.VISIBLE
        } else {
            holder.notesText.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<TeacherHourRecord>) {
        items = newItems
        notifyDataSetChanged()
    }
}
