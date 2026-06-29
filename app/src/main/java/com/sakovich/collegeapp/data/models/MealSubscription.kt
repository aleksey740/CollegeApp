package com.sakovich.collegeapp.data.models

import com.google.firebase.firestore.Exclude
import java.util.Date

data class MealSubscription(
    @get:Exclude
    val id: String = "",
    val date: String = "",
    val userId: String = "",
    val userName: String = "",
    val groupName: String = "",
    val isSubscribed: Boolean = false,
    val updatedById: String = "",
    val updatedAt: Date = Date()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "date" to date,
            "userId" to userId,
            "userName" to userName,
            "groupName" to groupName,
            "isSubscribed" to isSubscribed,
            "updatedById" to updatedById,
            "updatedAt" to com.google.firebase.Timestamp(updatedAt)
        )
    }
}
