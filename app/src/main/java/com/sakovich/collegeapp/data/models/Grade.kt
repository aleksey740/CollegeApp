package com.sakovich.collegeapp.data.models

data class Grade(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subject: String = "",
    val value: Int = 0,
    val date: String = "",
    val type: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val comment: String = "",
    val semester: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", 0, "", "", "", "", "", 1, 0)

    companion object {

        const val VALUE_ABSENCE = -1
    }

    fun isAbsence(): Boolean = value == VALUE_ABSENCE

    fun typeDisplayLabel(): String {
        val t = type.trim()
        return when {
            t.isEmpty() || t.equals("Журнал", ignoreCase = true) -> "Обычная"
            else -> t
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "studentId" to studentId,
            "studentName" to studentName,
            "subject" to subject,
            "value" to value,
            "date" to date,
            "type" to type,
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "comment" to comment,
            "semester" to semester,
            "createdAt" to createdAt
        )
    }
}
