package com.sakovich.collegeapp.data.models

import com.google.firebase.Timestamp
import java.util.Date

data class ChatRoom(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val isGroupRoom: Boolean = false,
    val groupId: String = "",
    val groupName: String = "",
    val lastMessage: String = "",
    val lastSenderName: String = "",
    val lastMessageAt: Date = Date(0),
    val pinnedMessageId: String = "",
    val pinnedMessageText: String = "",
    val pinnedByName: String = "",
    val pinnedAt: Date = Date(0)
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "title" to title,
            "description" to description,
            "isGroupRoom" to isGroupRoom,
            "groupId" to groupId,
            "groupName" to groupName,
            "lastMessage" to lastMessage,
            "lastSenderName" to lastSenderName,
            "lastMessageAt" to Timestamp(lastMessageAt),
            "pinnedMessageId" to pinnedMessageId,
            "pinnedMessageText" to pinnedMessageText,
            "pinnedByName" to pinnedByName,
            "pinnedAt" to Timestamp(pinnedAt)
        )
    }
}
