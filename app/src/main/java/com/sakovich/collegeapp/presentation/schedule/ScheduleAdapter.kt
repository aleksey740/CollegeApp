package com.sakovich.collegeapp.presentation.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Lesson
import com.sakovich.collegeapp.data.models.LessonType
import com.sakovich.collegeapp.presentation.schedule.ScheduleFragment.ScheduleCell

class ScheduleAdapter(
    private val scheduleGrid: List<ScheduleCell>,
    private val canEdit: Boolean = false,
    private val onCellClick: (Lesson?, Int) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_header, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val cell = scheduleGrid[position]
        holder.bind(cell)

        holder.itemView.setOnClickListener {
            onCellClick(cell.lesson, position)
        }
    }

    override fun getItemCount(): Int = scheduleGrid.size

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(cell: ScheduleCell) {
            itemView.findViewById<TextView>(R.id.headerText).text = cell.title ?: ""
        }
    }
}