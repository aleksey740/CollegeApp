package com.sakovich.collegeapp.data.models

fun DayOfWeek.getShortDayName(): String = when (this) {
    DayOfWeek.MONDAY -> "ПН"
    DayOfWeek.TUESDAY -> "ВТ"
    DayOfWeek.WEDNESDAY -> "СР"
    DayOfWeek.THURSDAY -> "ЧТ"
    DayOfWeek.FRIDAY -> "ПТ"
    DayOfWeek.SATURDAY -> "СБ"
}

fun DayOfWeek.getFullDayName(): String = when (this) {
    DayOfWeek.MONDAY -> "Понедельник"
    DayOfWeek.TUESDAY -> "Вторник"
    DayOfWeek.WEDNESDAY -> "Среда"
    DayOfWeek.THURSDAY -> "Четверг"
    DayOfWeek.FRIDAY -> "Пятница"
    DayOfWeek.SATURDAY -> "Суббота"
}

fun TimeSlot.getTimeRange(): String = when (this) {
    TimeSlot.FIRST -> "08:30-10:00"
    TimeSlot.SECOND -> "10:10-11:40"
    TimeSlot.THIRD -> "12:10-13:40"
    TimeSlot.FOURTH -> "14:00-15:30"
    TimeSlot.FIFTH -> "15:40-17:10"
    TimeSlot.SIXTH -> "17:20-18:50"
}

fun TimeSlot.getTimeRangeDisplay(): String = when (this) {
    TimeSlot.FIRST -> "08:30\n10:00"
    TimeSlot.SECOND -> "10:10\n11:40"
    TimeSlot.THIRD -> "12:10\n13:40"
    TimeSlot.FOURTH -> "14:00\n15:30"
    TimeSlot.FIFTH -> "15:40\n17:10"
    TimeSlot.SIXTH -> "17:20\n18:50"
}

fun LessonType.getDisplayName(): String = when (this) {
    LessonType.LECTURE -> "Лекция"
    LessonType.PRACTICE -> "Практика"
    LessonType.LAB -> "Лабораторная"
    LessonType.SEMINAR -> "Семинар"
    LessonType.CONSULTATION -> "Консультация"
}

fun Lesson.getTimeRange(): String = this.timeSlot.getTimeRange()
fun Lesson.getFullDayName(): String = this.dayOfWeek.getFullDayName()