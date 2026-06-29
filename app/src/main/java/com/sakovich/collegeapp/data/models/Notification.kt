package com.sakovich.collegeapp.data.models

import java.util.Date

data class Notification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.GRADE,
    val isRead: Boolean = false,
    val createdAt: Date = Date(),
    val relatedId: String = "",
    val relatedType: String = "",
    /** true — только запись в ленте, без FCM (локальные напоминания). */
    val localOnly: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "type" to type.name,
            "isRead" to isRead,
            "createdAt" to com.google.firebase.Timestamp(createdAt),
            "relatedId" to relatedId,
            "relatedType" to relatedType
        )
        if (localOnly) {
            map["localOnly"] = true
        }
        return map
    }
}

enum class NotificationType {
    GRADE,
    ABSENCE,
    EVENT,
    SCHEDULE,
    CHAT,
    SYSTEM
}
