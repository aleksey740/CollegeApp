package com.sakovich.collegeapp.data.models

data class User(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "student",
    val groupId: String? = null,
    val groupName: String? = null,
    val phone: String = "",
    val avatar: String? = null,
    val student: Boolean = false,
    val teacher: Boolean = false,
    val headman: Boolean = false
) {
    fun getDisplayName(): String {
        return if (fullName.isNotEmpty()) fullName else "Имя не указано"
    }

    fun getDisplayGroup(): String {
        return groupName ?: groupId ?: "Не назначена"
    }

    fun canEditEvents(): Boolean {
        return teacher || headman || role == "teacher" || role == "headman"
    }

    fun canAddEvents(): Boolean {
        return teacher || headman || role == "teacher" || role == "headman"
    }
}