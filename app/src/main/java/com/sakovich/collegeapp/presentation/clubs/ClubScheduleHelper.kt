package com.sakovich.collegeapp.presentation.clubs

import com.sakovich.collegeapp.data.models.Club
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ClubScheduleHelper {

    private val DAY_TO_CALENDAR = mapOf(
        "Пн" to Calendar.MONDAY,
        "Вт" to Calendar.TUESDAY,
        "Ср" to Calendar.WEDNESDAY,
        "Чт" to Calendar.THURSDAY,
        "Пт" to Calendar.FRIDAY,
        "Сб" to Calendar.SATURDAY
    )

    fun parseScheduleDaysAndStart(schedule: String): Pair<Set<Int>, String>? {
        val trimmed = schedule.trim()
        if (trimmed.isEmpty()) return null

        val days = mutableSetOf<Int>()
        DAY_TO_CALENDAR.forEach { (abbr, calDay) ->
            if (Regex("(^|[,\\s])$abbr([,\\s]|$)").containsMatchIn(trimmed)) {
                days.add(calDay)
            }
        }
        val timeMatch = Regex("(\\d{1,2}:\\d{2})").find(trimmed) ?: return null
        val startTime = normalizeTime(timeMatch.groupValues[1]) ?: return null
        if (days.isEmpty()) return null
        return days to startTime
    }

    fun computeNextFromSchedule(
        schedule: String,
        referenceMillis: Long = System.currentTimeMillis()
    ): Pair<String, String>? {
        val (days, startTime) = parseScheduleDaysAndStart(schedule) ?: return null
        val (hour, minute) = parseTimeComponents(startTime) ?: return null
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        for (offset in 0..13) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = referenceMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (candidate.get(Calendar.DAY_OF_WEEK) !in days) continue
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            candidate.set(Calendar.MINUTE, minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
            if (candidate.timeInMillis > referenceMillis) {
                return dateFormat.format(candidate.time) to startTime
            }
        }
        return null
    }

    fun resolveNextSession(club: Club): Pair<String, String>? {
        if (club.nextSessionDate.isNotBlank() && club.nextSessionTime.isNotBlank()) {
            val millis = parseSessionMillis(club.nextSessionDate, club.nextSessionTime)
            if (millis != null && millis > System.currentTimeMillis()) {
                return club.nextSessionDate to club.nextSessionTime
            }
        }
        return computeNextFromSchedule(club.schedule)
    }

    fun parseSessionMillis(date: String, time: String): Long? {
        if (date.isBlank() || time.isBlank()) return null
        return try {
            val parser = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            parser.parse("$date ${normalizeTime(time) ?: time}")?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeTime(raw: String): String? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val minute = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
        return "%02d:%02d".format(hour, minute)
    }

    private fun parseTimeComponents(time: String): Pair<Int, Int>? {
        val normalized = normalizeTime(time) ?: return null
        val parts = normalized.split(":")
        return parts[0].toInt() to parts[1].toInt()
    }
}
