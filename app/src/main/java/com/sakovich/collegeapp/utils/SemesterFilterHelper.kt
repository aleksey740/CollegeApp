package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.repositories.AdminRepository
import java.util.Date

object SemesterFilterHelper {

    val dropdownOptions: List<String> = listOf(
        "Все семестры",
        "1 семестр", "2 семестр", "3 семестр", "4 семестр",
        "5 семестр", "6 семестр", "7 семестр", "8 семестр"
    )

    fun semesterNumberFromPosition(position: Int): Int? =
        if (position <= 0) null else position

    suspend fun dateRangeForSemester(
        adminRepository: AdminRepository,
        groupId: String,
        semesterNumber: Int
    ): Pair<Date, Date>? {
        if (groupId.isBlank()) return null
        val semesters = adminRepository.getSemestersForGroup(groupId)
        val template = semesters.firstOrNull { sem ->
            Regex("(\\d+)").find(sem.name)?.groupValues?.getOrNull(1)?.toIntOrNull() == semesterNumber
        } ?: return null
        val start = SemesterStatsHelper.parseDate(template.startDate) ?: return null
        val end = SemesterStatsHelper.parseDate(template.endDate) ?: return null
        return start to end
    }

    fun matchesSemesterFilter(dateStr: String, range: Pair<Date, Date>?): Boolean {
        if (range == null) return true
        return SemesterStatsHelper.isDateInRange(dateStr, range.first, range.second)
    }
}
