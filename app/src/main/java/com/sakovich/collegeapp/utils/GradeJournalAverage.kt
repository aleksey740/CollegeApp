package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.JournalColumnLabel
import kotlin.math.round

object GradeJournalAverage {

    /**
     * Итоговая за семестр: среднее обычных отметок и среднее ОКР;
     * если один вид отметок — только его среднее (как в журнале на экране).
     */
    fun calculateFinalAverage(grades: List<Grade>): Double? {
        val valid = grades.filter { it.value != Grade.VALUE_ABSENCE }
        if (valid.isEmpty()) return null

        val krGrades = valid.filter {
            it.type.trim().equals(JournalColumnLabel.TYPE_CONTROL, ignoreCase = true)
        }
        val commonGrades = valid.filter {
            !it.type.trim().equals(JournalColumnLabel.TYPE_CONTROL, ignoreCase = true)
        }

        if (commonGrades.isEmpty()) {
            return krGrades.map { it.value }.average()
        }
        if (krGrades.isEmpty()) {
            return commonGrades.map { it.value }.average()
        }

        val commonAvg = commonGrades.map { it.value }.average()
        val krAvg = krGrades.map { it.value }.average()
        return (commonAvg + krAvg) / 2.0
    }

    fun formatFinalGradeDisplay(avg: Double?): String =
        avg?.let { round(it).toInt().toString() } ?: ""
}
