package com.sakovich.collegeapp.presentation.schedule

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.utils.PersonNameFormat

class ScheduleAdapter(
    private var schedule: List<ScheduleItem>,
    private val canManage: Boolean = false,
    private val canModifyItem: (ScheduleItem) -> Boolean = { true },
    private val onScheduleClick: (ScheduleItem) -> Unit,
    private val onScheduleLongClick: ((ScheduleItem) -> Unit)? = null
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val layout = R.layout.item_schedule
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(schedule[position])
    }

    override fun getItemCount(): Int = schedule.size

    fun updateSchedule(newSchedule: List<ScheduleItem>) {
        this.schedule = newSchedule
        notifyDataSetChanged()
    }

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val subjectText: TextView = itemView.findViewById(R.id.subjectTextView)
        private val teacherText: TextView = itemView.findViewById(R.id.teacherTextView)
        private val roomText: TextView = itemView.findViewById(R.id.roomTextView)
        private val roomChip: View = itemView.findViewById(R.id.roomChipLayout)
        private val dayText: TextView = itemView.findViewById(R.id.dayTextView)
        private val dateText: TextView = itemView.findViewById(R.id.dateTextView)
        private val typeText: TextView = itemView.findViewById(R.id.lessonTypeText)
        private val groupText: TextView = itemView.findViewById(R.id.groupTextView)
        private val typeIndicator: View = itemView.findViewById(R.id.typeIndicator)
        private val editHintText: TextView = itemView.findViewById(R.id.editHintText)

        fun bind(schedule: ScheduleItem) {

            subjectText.text = schedule.subject
            teacherText.text = formatTeachersLine(schedule)
            timeText.text = schedule.time
            roomText.text = if (schedule.isSubgroup && schedule.room2.isNotBlank()) {
                "🏫 ${schedule.room} / ${schedule.room2}"
            } else {
                "🏫 ${schedule.room}"
            }
            dayText.text = "📅 ${schedule.day}"
            groupText.text = schedule.group

            if (schedule.date.isNotEmpty()) {
                dateText.visibility = View.VISIBLE
                dateText.text = "• ${schedule.date}"
            } else {
                dateText.visibility = View.GONE
            }

            val (typeName, colorRes) = when (schedule.type) {
                ScheduleType.LECTURE -> "Лекция" to R.color.lecture_color
                ScheduleType.PRACTICE -> "Практическая" to R.color.practice_color
                ScheduleType.LAB -> "Лабораторная" to R.color.lab_color
                ScheduleType.CONSULTATION -> "Консультация" to R.color.consultation_color
                ScheduleType.EXAM -> "Экзамен" to R.color.exam_color
                ScheduleType.CONTROL_WORK -> "ОКР" to R.color.control_work_color
                ScheduleType.LUNCH -> "Обед" to R.color.lunch_color
                ScheduleType.CURATOR_HOUR -> "Кураторский час" to R.color.consultation_color
                ScheduleType.INFO_HOUR -> "Информационный час" to R.color.seminar_color
            }

            if (schedule.type == ScheduleType.LUNCH) {
                subjectText.text = "Обед"
                teacherText.visibility = View.GONE
                roomChip.visibility = View.GONE
            } else {
                teacherText.visibility = View.VISIBLE
                roomChip.visibility = View.VISIBLE
            }

            val color = ContextCompat.getColor(itemView.context, colorRes)

            typeText.text = typeName
            val badgeBackground = typeText.background as? GradientDrawable
            if (badgeBackground != null) {
                badgeBackground.setColor(color)
            } else {
                typeText.setBackgroundColor(color)
            }

            typeIndicator.setBackgroundColor(color)

            val canModify = canManage && canModifyItem(schedule)
            editHintText.visibility = if (canModify) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                if (canModify && onScheduleLongClick != null) {
                    onScheduleLongClick(schedule)
                } else {
                    onScheduleClick(schedule)
                }
            }
        }

        private fun formatTeachersLine(schedule: ScheduleItem): String {
            val first = PersonNameFormat.shortFio(schedule.teacherName)
            if (schedule.isSubgroup && schedule.teacherName2.isNotBlank()) {
                val second = PersonNameFormat.shortFio(schedule.teacherName2)
                return "👨‍🏫 $first / $second"
            }
            return "👨‍🏫 $first"
        }
    }
}
