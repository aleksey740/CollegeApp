package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType

/**
 * Хронологическая сортировка карточек расписания с учётом пар, отдельных уроков и обеда.
 * Поддерживает разные схемы дня (обед на 5-м, 6-м или 7-м уроке и т.д.).
 */
object ScheduleSortHelper {

    fun sortedForDay(items: List<ScheduleItem>): List<ScheduleItem> {
        if (items.size <= 1) return items
        return items.sortedWith(
            compareBy<ScheduleItem> { sortKey(it, items) }
                .thenBy { typeTieBreak(it.type) }
                .thenBy { it.time }
        )
    }

    fun sortedAll(
        items: List<ScheduleItem>,
        dayOrder: (String) -> Int
    ): List<ScheduleItem> {
        if (items.isEmpty()) return items
        val peersByDateGroup = items.groupBy { "${it.date}|${it.group}" }
        return items.sortedWith(
            compareBy(
                { dayOrder(it.day) },
                { it.date },
                { item ->
                    val peers = peersByDateGroup["${item.date}|${item.group}"] ?: listOf(item)
                    sortKey(item, peers)
                },
                { typeTieBreak(it.type) },
                { it.time }
            )
        )
    }

    fun sortKey(item: ScheduleItem, dayItems: List<ScheduleItem>): Float {
        if (item.type == ScheduleType.LUNCH) {
            val lesson = lessonNumber(item.time)
            return (if (lesson > 0) lesson else 5) + 0.5f
        }

        val time = item.time.trim()
        if (isPair(time)) {
            val pairNum = pairNumber(time) ?: return 999f
            return pairSortPosition(pairNum, dayItems)
        }

        val lesson = lessonNumber(time)
        if (lesson > 0) return lesson.toFloat()

        return 999f
    }

    private fun pairSortPosition(pairNum: Int, dayItems: List<ScheduleItem>): Float {
        var lessons = lessonsForPair(pairNum).toMutableList()
        if (lessons.isEmpty()) return 999f

        val lunchLessons = dayItems
            .filter { it.type == ScheduleType.LUNCH }
            .mapNotNull { lessonNumber(it.time).takeIf { n -> n > 0 } }

        for (lunchAt in lunchLessons) {
            if (lunchAt in lessons) {
                lessons = lessons.filter { it > lunchAt }.toMutableList()
                if (lessons.isEmpty()) {
                    return (lunchAt + 1).toFloat()
                }
            }
        }

        val individualLessons = dayItems.mapNotNull { entry ->
            val n = lessonNumber(entry.time)
            if (n > 0 && !isPair(entry.time) && entry.type != ScheduleType.LUNCH) n else null
        }.toSet()

        var position = lessons.min().toFloat()

        if (pairNum == 3) {
            val lunchAt6 = 6 in lunchLessons
            val hasSevenPlus = individualLessons.any { it >= 7 }
            if (lunchAt6 || hasSevenPlus) {
                position = maxOf(7f, individualLessons.filter { it >= 7 }.minOrNull()?.toFloat() ?: 7f)
            }
        }

        if (pairNum == 4) {
            val hasNinePlus = individualLessons.any { it >= 9 }
            val pair3StartsLate = 6 in lunchLessons || individualLessons.any { it >= 7 }
            if (hasNinePlus || pair3StartsLate) {
                position = maxOf(
                    position,
                    individualLessons.filter { it >= 9 }.minOrNull()?.toFloat() ?: 9f
                )
            }
        }

        if (pairNum == 5) {
            val hasTenPlus = individualLessons.filter { it >= 10 }
            if (hasTenPlus.isNotEmpty()) {
                position = maxOf(position, hasTenPlus.min().toFloat())
            }
        }

        return position
    }

    private fun lessonsForPair(pairNum: Int): List<Int> {
        val label = when (pairNum) {
            1 -> "1-я пара"
            2 -> "2-я пара"
            3 -> "3-я пара"
            4 -> "4-я пара"
            5 -> "5-я пара"
            else -> return emptyList()
        }
        return ScheduleTimeSlotRules.slotsConflictingWith(label)
            .mapNotNull { lessonNumber(it).takeIf { n -> n > 0 } }
            .sorted()
    }

    private fun typeTieBreak(type: ScheduleType): Int = when (type) {
        ScheduleType.LUNCH -> 2
        else -> 1
    }

    private fun isPair(time: String): Boolean =
        time.contains("пара", ignoreCase = true)

    private fun pairNumber(time: String): Int? {
        if (!isPair(time)) return null
        return Regex("(\\d+)").find(time)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun lessonNumber(time: String): Int {
        if (!time.contains("урок", ignoreCase = true)) return 0
        return Regex("(\\d+)").find(time)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}
