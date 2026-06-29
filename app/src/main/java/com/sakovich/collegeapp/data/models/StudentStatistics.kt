package com.sakovich.collegeapp.data.models

data class StudentStatistics(
    val studentId: String = "",
    val studentName: String = "",
    val groupName: String = "",
    val gradesCount: Int = 0,
    val averageGrade: Double = 0.0,
    val grades: List<Grade> = emptyList()
)

data class GroupStatistics(
    val groupName: String = "",
    val studentsCount: Int = 0,
    val totalGrades: Int = 0,
    val averageGrade: Double = 0.0,
    val students: List<StudentStatistics> = emptyList()
)
