package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.ScheduleItem

object ScheduleTimeSlotRules {

    val allTimeOptions: List<String> = listOf(
        "1-я пара", "2-я пара", "3-я пара", "4-я пара", "5-я пара",
        "1-й урок", "2-й урок", "3-й урок", "4-й урок", "5-й урок",
        "6-й урок", "7-й урок", "8-й урок", "9-й урок", "10-й урок", "11-й урок"
    )

    fun slotsConflictingWith(slot: String): Set<String> = when (slot) {
        "1-я пара" -> setOf("1-я пара", "1-й урок", "2-й урок")
        "2-я пара" -> setOf("2-я пара", "3-й урок", "4-й урок")
        "3-я пара" -> setOf("3-я пара", "5-й урок", "6-й урок")
        "4-я пара" -> setOf("4-я пара", "7-й урок", "8-й урок")
        "5-я пара" -> setOf("5-я пара", "9-й урок", "10-й урок", "11-й урок")
        "1-й урок" -> setOf("1-я пара")
        "2-й урок" -> setOf("1-я пара")
        "3-й урок" -> setOf("2-я пара")
        "4-й урок" -> setOf("2-я пара")
        "5-й урок" -> setOf("3-я пара")
        "6-й урок" -> setOf("3-я пара")
        "7-й урок" -> setOf("4-я пара")
        "8-й урок" -> setOf("4-я пара")
        "9-й урок" -> setOf("5-я пара")
        "10-й урок" -> setOf("5-я пара")
        "11-й урок" -> setOf("5-я пара", "11-й урок")
        else -> setOf(slot)
    }

    fun blockedSlots(usedTimeSlots: Collection<String>): Set<String> =
        usedTimeSlots.toSet() + usedTimeSlots.flatMap { slotsConflictingWith(it) }

    fun availableTimeOptions(
        usedTimeSlots: Collection<String>,
        allOptions: List<String> = allTimeOptions
    ): List<String> {
        val blocked = blockedSlots(usedTimeSlots)
        return allOptions.filter { it !in blocked }
    }

    fun usedSlotsOnDate(
        schedules: List<ScheduleItem>,
        date: String,
        group: String,
        excludeScheduleId: String? = null
    ): List<String> = schedules
        .asSequence()
        .filter { item ->
            item.date == date &&
                item.group.equals(group, ignoreCase = true) &&
                (excludeScheduleId.isNullOrBlank() || item.id != excludeScheduleId)
        }
        .map { it.time.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    fun isSlotAvailable(time: String, usedTimeSlots: Collection<String>): Boolean =
        time.trim() !in blockedSlots(usedTimeSlots)
}
