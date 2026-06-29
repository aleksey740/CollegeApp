package com.sakovich.collegeapp.data.models

import java.util.Date


data class Absence(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentGroup: String = "",
    val subject: String = "",
    val date: String = "",
    val hours: Int = 2,
    val reason: AbsenceReason = AbsenceReason.WITHOUT_REASON,
    val comment: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val createdByRole: String = "",
    val createdAt: Date = Date(),
    val isExcused: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "studentId" to studentId,
            "studentName" to studentName,
            "studentGroup" to studentGroup,
            "subject" to subject,
            "date" to date,
            "hours" to hours,
            "reason" to reason.name,
            "comment" to comment,
            "createdBy" to createdBy,
            "createdByName" to createdByName,
            "createdByRole" to createdByRole,
            "createdAt" to createdAt,
            "isExcused" to isExcused
        )
    }
}

enum class AbsenceReason {
    WITHOUT_REASON,
    SICK,
    FAMILY,
    OFFICIAL,
    OTHER
}

fun getAbsenceReasonDisplayName(reason: AbsenceReason): String {
    return when (reason) {
        AbsenceReason.WITHOUT_REASON -> "Без уважительной причины"
        AbsenceReason.SICK -> "Болезнь"
        AbsenceReason.FAMILY -> "Семейные обстоятельства"
        AbsenceReason.OFFICIAL -> "По служебной записке"
        AbsenceReason.OTHER -> "Другое"
    }
}
