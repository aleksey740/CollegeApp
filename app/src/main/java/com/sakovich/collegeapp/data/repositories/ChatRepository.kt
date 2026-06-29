package com.sakovich.collegeapp.data.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.ChatMessage
import com.sakovich.collegeapp.data.models.ChatRoom
import com.sakovich.collegeapp.data.models.User
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale

class ChatRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val roomsCollection = db.collection("chat_rooms")
    private val messagesCollection = db.collection("chat_messages")

    suspend fun getRoomsForUser(user: User): List<ChatRoom> {
        val groupRoom = buildGroupRoom(user) ?: return emptyList()
        ensureRoomExists(groupRoom)
        return listOf(groupRoom)
    }

    fun observeMessages(
        roomId: String,
        onChanged: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return messagesCollection
            .whereEqualTo("roomId", roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents
                    ?.mapNotNull { doc -> toChatMessage(doc.id, doc.data) }
                    ?.sortedBy { it.createdAt.time }
                    ?: emptyList()

                onChanged(messages)
            }
    }

    fun observeRoom(
        roomId: String,
        onChanged: (ChatRoom) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return roomsCollection.document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val room = toChatRoom(roomId, snapshot?.data)
                if (room != null) {
                    onChanged(room)
                }
            }
    }

    suspend fun sendMessage(room: ChatRoom, sender: User, rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isBlank()) return false

        return try {
            val links = extractLinks(text)
            val message = ChatMessage(
                roomId = room.id,
                senderId = sender.id,
                senderName = sender.fullName.ifBlank { sender.email.substringBefore("@") },
                senderRole = sender.role,
                text = text,
                stickerId = null,
                links = links,
                createdAt = Date()
            )
            messagesCollection.add(message.toMap()).await()
            roomsCollection.document(room.id).set(
                mapOf(
                    "lastMessage" to text.take(250),
                    "lastSenderName" to message.senderName,
                    "lastMessageAt" to Timestamp.now()
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendSticker(room: ChatRoom, sender: User, stickerId: String): Boolean {
        val sid = stickerId.trim()
        if (sid.isBlank()) return false

        return try {
            val message = ChatMessage(
                roomId = room.id,
                senderId = sender.id,
                senderName = sender.fullName.ifBlank { sender.email.substringBefore("@") },
                senderRole = sender.role,
                text = "",
                stickerId = sid,
                links = emptyList(),
                createdAt = Date()
            )
            messagesCollection.add(message.toMap()).await()
            roomsCollection.document(room.id).set(
                mapOf(
                    "lastMessage" to "Стикер",
                    "lastSenderName" to message.senderName,
                    "lastMessageAt" to Timestamp.now()
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateOwnMessage(messageId: String, userId: String, newTextRaw: String): Boolean {
        val newText = newTextRaw.trim()
        if (newText.isBlank()) return false
        return try {
            val doc = messagesCollection.document(messageId).get().await()
            val senderId = doc.getString("senderId").orEmpty()
            if (senderId != userId) return false
            if (!doc.getString("stickerId").isNullOrBlank()) return false
            val links = extractLinks(newText)
            messagesCollection.document(messageId).update(
                mapOf(
                    "text" to newText,
                    "links" to links,
                    "editedAt" to Timestamp.now()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteMessage(messageId: String): Boolean {
        return try {
            messagesCollection.document(messageId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun pinMessage(roomId: String, message: ChatMessage, moderatorName: String): Boolean {
        return try {
            val preview = if (!message.stickerId.isNullOrBlank()) {
                "Стикер"
            } else {
                message.text.take(250)
            }
            roomsCollection.document(roomId).set(
                mapOf(
                    "pinnedMessageId" to message.id,
                    "pinnedMessageText" to preview,
                    "pinnedByName" to moderatorName,
                    "pinnedAt" to Timestamp.now()
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearPinnedMessage(roomId: String): Boolean {
        return try {
            roomsCollection.document(roomId).set(
                mapOf(
                    "pinnedMessageId" to "",
                    "pinnedMessageText" to "",
                    "pinnedByName" to "",
                    "pinnedAt" to Timestamp(Date(0))
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearRoomMessages(roomId: String): Boolean {
        return try {
            while (true) {
                val snapshot = messagesCollection
                    .whereEqualTo("roomId", roomId)
                    .limit(400)
                    .get()
                    .await()
                if (snapshot.isEmpty) break

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            clearPinnedMessage(roomId)
            roomsCollection.document(roomId).set(
                mapOf(
                    "lastMessage" to "",
                    "lastSenderName" to "",
                    "lastMessageAt" to Timestamp(Date(0))
                ),
                SetOptions.merge()
            ).await()

            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun ensureRoomExists(room: ChatRoom) {
        val existing = roomsCollection.document(room.id).get().await()
        if (!existing.exists()) {
            roomsCollection.document(room.id).set(room.toMap()).await()
        }
    }

    private fun buildGroupRoom(user: User): ChatRoom? {
        val groupId = user.groupId.ifBlank {
            user.groupName.ifBlank { user.group }.trim()
        }.trim()
        if (groupId.isBlank()) return null

        val safeGroupId = groupId
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .trim('_')
            .ifBlank { "group" }

        val groupName = user.groupName.ifBlank { user.group.ifBlank { "Группа" } }
        return ChatRoom(
            id = "group_$safeGroupId",
            title = "Чат группы $groupName",
            description = "Сообщения внутри вашей группы",
            isGroupRoom = true,
            groupId = groupId,
            groupName = groupName
        )
    }

    private fun toChatMessage(id: String, data: Map<String, Any>?): ChatMessage? {
        if (data == null) return null
        return try {
            val createdAt = when (val value = data["createdAt"]) {
                is Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> Date()
            }
            ChatMessage(
                id = id,
                roomId = data["roomId"] as? String ?: "",
                senderId = data["senderId"] as? String ?: "",
                senderName = data["senderName"] as? String ?: "Пользователь",
                senderRole = data["senderRole"] as? String ?: "",
                text = data["text"] as? String ?: "",
                stickerId = (data["stickerId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                links = (data["links"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                createdAt = createdAt,
                editedAt = when (val edited = data["editedAt"]) {
                    is Timestamp -> edited.toDate()
                    is Date -> edited
                    is Long -> Date(edited)
                    else -> null
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun toChatRoom(id: String, data: Map<String, Any>?): ChatRoom? {
        if (data == null) return null
        return try {
            ChatRoom(
                id = id,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                isGroupRoom = data["isGroupRoom"] as? Boolean ?: false,
                groupId = data["groupId"] as? String ?: "",
                groupName = data["groupName"] as? String ?: "",
                lastMessage = data["lastMessage"] as? String ?: "",
                lastSenderName = data["lastSenderName"] as? String ?: "",
                lastMessageAt = when (val value = data["lastMessageAt"]) {
                    is Timestamp -> value.toDate()
                    is Date -> value
                    is Long -> Date(value)
                    else -> Date(0)
                },
                pinnedMessageId = data["pinnedMessageId"] as? String ?: "",
                pinnedMessageText = data["pinnedMessageText"] as? String ?: "",
                pinnedByName = data["pinnedByName"] as? String ?: "",
                pinnedAt = when (val value = data["pinnedAt"]) {
                    is Timestamp -> value.toDate()
                    is Date -> value
                    is Long -> Date(value)
                    else -> Date(0)
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractLinks(text: String): List<String> {
        val regex = Regex("""((https?://|www\.)[^\s]+)""")
        return regex.findAll(text)
            .map { it.value.trim().trimEnd('.', ',', ';', ':', '!', '?') }
            .distinct()
            .toList()
    }

}
