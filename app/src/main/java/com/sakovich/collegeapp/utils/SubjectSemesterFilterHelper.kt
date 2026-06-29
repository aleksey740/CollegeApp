package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.SubjectForGroup
import java.util.Date

object SubjectSemesterFilterHelper {

    /**
     * Возвращает предметы, привязанные хотя бы к одному семестру,
     * пересекающемуся с диапазоном [rangeStart, rangeEnd].
     * Если диапазон не задан — все предметы группы.
     * Предметы без привязки к семестрам показываются всегда.
     */
    fun filterSubjectNames(
        subjects: List<SubjectForGroup>,
        semesters: List<AdminSemesterTemplate>,
        rangeStart: Date?,
        rangeEnd: Date?
    ): List<String> {
        if (rangeStart == null && rangeEnd == null) {
            return subjects.map { it.name }.distinct().sorted()
        }
        val start = rangeStart ?: rangeEnd!!
        val end = rangeEnd ?: rangeStart!!
        val semesterById = semesters.associateBy { it.id }

        return subjects
            .filter { subject -> subjectMatchesRange(subject, semesterById, start, end) }
            .map { it.name }
            .distinct()
            .sorted()
    }

    fun filterForDate(
        subjects: List<SubjectForGroup>,
        semesters: List<AdminSemesterTemplate>,
        dateStr: String
    ): List<String> {
        val date = SemesterStatsHelper.parseDate(dateStr) ?: return subjects.map { it.name }.distinct().sorted()
        return filterSubjectNames(subjects, semesters, date, date)
    }

    private fun subjectMatchesRange(
        subject: SubjectForGroup,
        semesterById: Map<String, AdminSemesterTemplate>,
        rangeStart: Date,
        rangeEnd: Date
    ): Boolean {
        if (subject.semesterIds.isEmpty()) return true
        return subject.semesterIds.any { semesterId ->
            val semester = semesterById[semesterId] ?: return@any false
            val semStart = SemesterStatsHelper.parseDate(semester.startDate) ?: return@any false
            val semEnd = SemesterStatsHelper.parseDate(semester.endDate) ?: return@any false
            rangesOverlap(rangeStart, rangeEnd, semStart, semEnd)
        }
    }

    private fun rangesOverlap(aStart: Date, aEnd: Date, bStart: Date, bEnd: Date): Boolean =
        !aStart.after(bEnd) && !aEnd.before(bStart)
}
