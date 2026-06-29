package com.sakovich.collegeapp.data.models

data class Group(
    val id: String = "",
    val name: String = "",
    val curatorId: String = "",
    val studentCount: Int = 0,
    val headmanName: String = ""
) {
    constructor() : this("", "", "", 0, "")
}
