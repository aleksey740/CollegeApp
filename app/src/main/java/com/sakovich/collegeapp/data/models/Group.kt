package com.sakovich.collegeapp.data.models

data class Group(
    val id: String = "",
    val name: String = "", // "ИТ-21", "ФИЗ-22"
    val curatorId: String = "", // ID преподавателя-куратора
    val studentCount: Int = 0
) {
    constructor() : this("", "", "", 0)
}