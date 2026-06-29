package com.sakovich.collegeapp.data.models

import com.google.firebase.Timestamp
import java.util.Date

data class ChatMessage(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val text: String = "",

    val stickerId: String? = null,
    val links: List<String> = emptyList(),
    val createdAt: Date = Date(),
    val editedAt: Date? = null
) {
    val isSticker: Boolean get() = !stickerId.isNullOrBlank()

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "roomId" to roomId,
            "senderId" to senderId,
            "senderName" to senderName,
            "senderRole" to senderRole,
            "text" to text,
            "links" to links,
            "createdAt" to Timestamp(createdAt)
        )
        val sid = stickerId?.trim().orEmpty()
        if (sid.isNotEmpty()) {
            map["stickerId"] = sid
        }
        if (editedAt != null) {
            map["editedAt"] = Timestamp(editedAt)
        }
        return map
    }
}
