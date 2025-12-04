package com.sakovich.collegeapp.data.models

data class Grade(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subject: String = "",
    val value: Int = 0,
    val date: String = "",
    val type: String = "", // "экзамен", "зачет", "лабораторная", "тест"
    val teacherId: String = "",
    val teacherName: String = "",
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", 0, "", "", "", "", "", 0)

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
            "createdAt" to createdAt
        )
    }
}