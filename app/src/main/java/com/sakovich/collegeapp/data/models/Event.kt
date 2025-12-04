package com.sakovich.collegeapp.data.models

import java.util.Date

data class Event(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Date = Date(),
    val startTime: String = "",
    val endTime: String = "",
    val type: EventType = EventType.LECTURE,
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val location: String = "",
    val createdAt: Date = Date(),
    val createdBy: String = ""
) {
    fun isPastEvent(): Boolean {
        return date.before(Date())
    }

    fun getFormattedDate(): String {
        return android.text.format.DateFormat.format("dd.MM.yyyy", date).toString()
    }

    fun getFormattedDateTime(): String {
        return android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", date).toString()
    }
}

enum class EventType {
    LECTURE,
    PRACTICE,
    EXAM,
    MEETING,
    HOLIDAY,
    OTHER
}