package com.sakovich.collegeapp.data.models


data class JournalColumnLabel(
    val id: String = "",
    val teacherId: String = "",
    val groupName: String = "",
    val subject: String = "",
    val date: String = "",
    val lessonType: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "teacherId" to teacherId,
        "groupName" to groupName,
        "subject" to subject,
        "date" to date,
        "lessonType" to lessonType
    )

    companion object {
        const val TYPE_CONTROL = "ОКР"
        const val TYPE_LAB = "ЛР"
        const val TYPE_PRACTICE = "ПР"

        val COLUMN_TYPES = listOf(TYPE_CONTROL, TYPE_LAB, TYPE_PRACTICE)

        fun isLessonType(type: String?): Boolean =
            type == TYPE_CONTROL || type == TYPE_LAB || type == TYPE_PRACTICE
    }
}
