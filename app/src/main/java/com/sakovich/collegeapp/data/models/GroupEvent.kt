package com.sakovich.collegeapp.data.models

import com.google.firebase.firestore.Exclude
import java.util.Date

data class GroupEvent(
    @get:Exclude
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val place: String = "",
    val description: String = "",
    val groupName: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val createdByRole: String = "",
    val createdAt: Date = Date()
)
