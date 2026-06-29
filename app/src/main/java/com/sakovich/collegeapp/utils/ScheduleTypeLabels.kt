package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.ScheduleType

object ScheduleTypeLabels {

    val regularScheduleTypes: List<ScheduleType> = listOf(
        ScheduleType.LECTURE,
        ScheduleType.PRACTICE,
        ScheduleType.LAB,
        ScheduleType.CONSULTATION,
        ScheduleType.EXAM,
        ScheduleType.CONTROL_WORK,
        ScheduleType.LUNCH
    )

    fun displayName(type: ScheduleType): String = when (type) {
        ScheduleType.LECTURE -> "Лекция"
        ScheduleType.PRACTICE -> "Практическая"
        ScheduleType.LAB -> "Лабораторная"
        ScheduleType.CONSULTATION -> "Консультация"
        ScheduleType.EXAM -> "Экзамен"
        ScheduleType.CONTROL_WORK -> "ОКР"
        ScheduleType.LUNCH -> "Обед"
        ScheduleType.CURATOR_HOUR -> "Кураторский час"
        ScheduleType.INFO_HOUR -> "Информационный час"
    }

    fun fromDisplayName(displayName: String): ScheduleType = when (displayName) {
        "Лекция" -> ScheduleType.LECTURE
        "Практическая" -> ScheduleType.PRACTICE
        "Лабораторная" -> ScheduleType.LAB
        "Консультация" -> ScheduleType.CONSULTATION
        "Экзамен" -> ScheduleType.EXAM
        "ОКР", "Контрольная", "К/Р" -> ScheduleType.CONTROL_WORK
        "Обед" -> ScheduleType.LUNCH
        "Кураторский час" -> ScheduleType.CURATOR_HOUR
        "Информационный час" -> ScheduleType.INFO_HOUR
        else -> ScheduleType.LECTURE
    }

    fun displayNamesForAddDialog(excludeLunchOnDate: Boolean): List<String> {
        val names = regularScheduleTypes.map { displayName(it) }.toMutableList()
        if (excludeLunchOnDate) {
            names.remove("Обед")
        }
        return names
    }
}
