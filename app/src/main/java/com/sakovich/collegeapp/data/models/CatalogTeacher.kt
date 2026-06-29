package com.sakovich.collegeapp.data.models


data class CatalogTeacher(
    val id: String = "",
    val fullName: String = "",
    val subjectIds: List<String> = emptyList()
)
