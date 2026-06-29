package com.sakovich.collegeapp.data.models

data class AdminSemesterTemplate(
    val id: String = "",
    val name: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val groupId: String = "",
    val groupName: String = ""
)

data class SubjectForGroup(
    val name: String,
    val groupId: String,
    val groupName: String,
    val semesterIds: List<String> = emptyList(),
    val semesterNames: List<String> = emptyList()
) {
    fun hasSemester(semesterId: String): Boolean = semesterIds.contains(semesterId)

    fun semestersDisplayText(): String =
        if (semesterNames.isEmpty()) "Семестры: не назначены"
        else "Семестры: ${semesterNames.joinToString(", ")}"
}
