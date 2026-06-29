package com.sakovich.collegeapp.data.models

import com.google.firebase.firestore.Exclude
import java.util.Date

data class TeacherHourRecord(
    @get:Exclude
    val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val type: TeacherHourType = TeacherHourType.TEACHING,
    val topic: String = "",
    val groupName: String = "",
    val date: String = "",
    val time: String = "",
    val hoursCount: Int = 2,
    val attendanceCount: Int = 0,
    val notes: String = "",
    val createdAt: Date = Date()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "type" to type.name,
            "topic" to topic,
            "groupName" to groupName,
            "date" to date,
            "time" to time,
            "hoursCount" to hoursCount,
            "attendanceCount" to attendanceCount,
            "notes" to notes,
            "createdAt" to com.google.firebase.Timestamp(createdAt)
        )
    }
}

enum class TeacherHourType {
    TEACHING,
    CURATOR
}
