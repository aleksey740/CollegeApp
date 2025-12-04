package com.sakovich.collegeapp.presentation.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Event
import com.sakovich.collegeapp.data.models.EventType

class EventsAdapter(
    private var events: List<Event>,
    private val canEdit: Boolean = false,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view, canEdit)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<Event>) {
        this.events = newEvents
        notifyDataSetChanged()
    }

    inner class EventViewHolder(itemView: View, private val canEdit: Boolean) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.eventTitleText)
        private val dateText: TextView = itemView.findViewById(R.id.eventDateText)
        private val timeText: TextView = itemView.findViewById(R.id.eventTimeText)
        private val typeText: TextView = itemView.findViewById(R.id.eventTypeText)
        private val locationText: TextView = itemView.findViewById(R.id.eventLocationText)
        private val groupText: TextView = itemView.findViewById(R.id.eventGroupText)
        private val authorText: TextView = itemView.findViewById(R.id.eventAuthorText)
        private val editHintText: TextView = itemView.findViewById(R.id.editHintText)

        init {
            // Показываем подсказку редактирования если есть права
            editHintText.visibility = if (canEdit) View.VISIBLE else View.GONE
        }

        fun bind(event: Event) {
            titleText.text = event.title
            dateText.text = event.getFormattedDate()
            timeText.text = "${event.startTime} - ${event.endTime}"
            locationText.text = event.location

            // Отображаем группу
            groupText.text = event.groupName ?: "Общее"
            groupText.visibility = View.VISIBLE

            // Отображаем автора события
            val authorName = when {
                !event.teacherName.isNullOrEmpty() -> event.teacherName
                event.teacherId == "headman" -> "Староста"
                else -> "Система"
            }
            authorText.text = "Добавил: $authorName"
            authorText.visibility = View.VISIBLE

            // Устанавливаем тип события и цвет
            val (typeTextRes, colorRes) = when (event.type) {
                EventType.LECTURE -> "Лекция" to R.color.lecture_color
                EventType.PRACTICE -> "Практика" to R.color.practice_color
                EventType.EXAM -> "Экзамен" to R.color.exam_color
                EventType.MEETING -> "Собрание" to R.color.meeting_color
                EventType.HOLIDAY -> "Выходной" to R.color.holiday_color
                EventType.OTHER -> "Другое" to R.color.other_color
            }
            typeText.text = typeTextRes
            typeText.setBackgroundColor(ContextCompat.getColor(itemView.context, colorRes))

            // Подсвечиваем прошедшие события
            if (event.isPastEvent()) {
                itemView.alpha = 0.7f
            } else {
                itemView.alpha = 1.0f
            }

            itemView.setOnClickListener {
                onEventClick(event)
            }

            // Долгое нажатие для редактирования (только для тех, у кого есть права)
            if (canEdit) {
                itemView.setOnLongClickListener {
                    onEventClick(event)
                    true
                }
            }
        }
    }
}