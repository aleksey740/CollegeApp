package com.sakovich.collegeapp.data.models


enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY }
enum class TimeSlot { FIRST, SECOND, THIRD, FOURTH, FIFTH, SIXTH }
enum class LessonType { LECTURE, PRACTICE, LAB, SEMINAR, CONSULTATION }


data class Lesson(
    val id: String,
    val subject: String,
    val teacherName: String,
    val groupName: String,
    val dayOfWeek: DayOfWeek,
    val timeSlot: TimeSlot,
    val classroom: String,
    val type: LessonType
)
