package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.AdminRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SemesterStatsHelper {

    fun parseDate(value: String): Date? {
        if (value.isBlank()) return null
        return try {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(value)
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(value)
        } catch (_: Exception) {
            null
        }
    }

    fun isDateInRange(dateString: String, start: Date, end: Date): Boolean {
        val date = parseDate(dateString) ?: return false
        return !date.before(start) && !date.after(end)
    }

    fun isDateWithinAnySemester(dateString: String, semesters: List<AdminSemesterTemplate>): Boolean {
        if (semesters.isEmpty()) return true
        return semesters.any { semester ->
            val start = parseDate(semester.startDate) ?: return@any false
            val end = parseDate(semester.endDate) ?: return@any false
            isDateInRange(dateString, start, end)
        }
    }

    suspend fun resolveCurrentSemesterNumber(user: User, adminRepository: AdminRepository): Int? {
        val groupId = user.groupId.ifBlank { user.groupName.ifBlank { user.group } }
        if (groupId.isBlank()) return null

        val semesters = adminRepository.getSemestersForGroup(groupId)
        if (semesters.isEmpty()) return null
        val today = Calendar.getInstance().time

        val currentTemplate = semesters.firstOrNull { sem ->
            val start = parseDate(sem.startDate) ?: return@firstOrNull false
            val end = parseDate(sem.endDate) ?: return@firstOrNull false
            !today.before(start) && !today.after(end)
        } ?: return null

        return Regex("(\\d+)").find(currentTemplate.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    suspend fun resolveCurrentSemesterDateRange(user: User, adminRepository: AdminRepository): Pair<Date, Date>? {
        val groupId = user.groupId.ifBlank { user.groupName.ifBlank { user.group } }
        if (groupId.isBlank()) return null

        val semesters = adminRepository.getSemestersForGroup(groupId)
        if (semesters.isEmpty()) return null
        val today = Calendar.getInstance().time

        val currentTemplate = semesters.firstOrNull { sem ->
            val start = parseDate(sem.startDate) ?: return@firstOrNull false
            val end = parseDate(sem.endDate) ?: return@firstOrNull false
            !today.before(start) && !today.after(end)
        } ?: return null

        val start = parseDate(currentTemplate.startDate) ?: return null
        val end = parseDate(currentTemplate.endDate) ?: return null
        return start to end
    }

    fun isVisibleGrade(grade: Grade): Boolean {
        if (grade.isAbsence()) return false
        val typeRaw = grade.type.trim().lowercase(Locale.getDefault())
        if (typeRaw.contains("неяв")) return false
        if (typeRaw == "н") return false
        return grade.value in 1..10
    }

    suspend fun filterGradesForCurrentSemester(
        grades: List<Grade>,
        user: User,
        adminRepository: AdminRepository
    ): List<Grade> {
        val currentSemesterNumber = resolveCurrentSemesterNumber(user, adminRepository)
        val currentSemesterRange = resolveCurrentSemesterDateRange(user, adminRepository)

        return if (currentSemesterRange != null) {
            val (start, end) = currentSemesterRange
            val filteredByDate = grades.filter { isDateInRange(it.date, start, end) }
            when {
                filteredByDate.isNotEmpty() -> filteredByDate
                currentSemesterNumber != null -> grades.filter { it.semester == currentSemesterNumber }
                else -> grades
            }
        } else {
            if (currentSemesterNumber != null) {
                grades.filter { it.semester == currentSemesterNumber }
            } else {
                grades
            }
        }
    }

    suspend fun filterAbsencesForCurrentSemester(
        absences: List<Absence>,
        user: User,
        adminRepository: AdminRepository
    ): List<Absence> {
        val currentSemesterRange = resolveCurrentSemesterDateRange(user, adminRepository)
        return if (currentSemesterRange != null) {
            val (start, end) = currentSemesterRange
            absences.filter { isDateInRange(it.date, start, end) }
        } else {
            absences
        }
    }
}
