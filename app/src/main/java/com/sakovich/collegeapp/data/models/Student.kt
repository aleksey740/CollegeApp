package com.sakovich.collegeapp.data.models

data class Student(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val avatar: String = "",
    val isHeadman: Boolean = false
) {
    constructor() : this("", "", "", "", "", "", false)
}
