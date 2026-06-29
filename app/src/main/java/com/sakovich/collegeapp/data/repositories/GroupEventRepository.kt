package com.sakovich.collegeapp.data.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.sakovich.collegeapp.data.models.GroupEvent
import kotlinx.coroutines.tasks.await
import java.util.Date

class GroupEventRepository {
    private val db = FirebaseFirestore.getInstance()
    val eventsCollection = db.collection("group_events")

    suspend fun addEvent(event: GroupEvent): String {
        val ref = eventsCollection.add(
            mapOf(
                "title" to event.title,
                "date" to event.date,
                "time" to event.time,
                "place" to event.place,
                "description" to event.description,
                "groupName" to event.groupName,
                "createdBy" to event.createdBy,
                "createdByName" to event.createdByName,
                "createdByRole" to event.createdByRole,
                "createdAt" to event.createdAt
            )
        ).await()
        return ref.id
    }

    suspend fun getEventsForGroup(groupName: String): List<GroupEvent> {
        if (groupName.isBlank()) return emptyList()
        val snap = eventsCollection.whereEqualTo("groupName", groupName).get().await()
        return snap.documents.mapNotNull { doc ->
            val title = doc.getString("title").orEmpty()
            val date = doc.getString("date").orEmpty()
            val time = doc.getString("time").orEmpty()
            if (title.isBlank() || date.isBlank() || time.isBlank()) return@mapNotNull null
            GroupEvent(
                id = doc.id,
                title = title,
                date = date,
                time = time,
                place = doc.getString("place").orEmpty(),
                description = doc.getString("description").orEmpty(),
                groupName = doc.getString("groupName").orEmpty(),
                createdBy = doc.getString("createdBy").orEmpty(),
                createdByName = doc.getString("createdByName").orEmpty(),
                createdByRole = doc.getString("createdByRole").orEmpty(),
                createdAt = (doc.get("createdAt") as? Timestamp)?.toDate() ?: Date()
            )
        }.sortedWith(compareBy({ it.date }, { it.time }))
    }

    suspend fun updateEvent(event: GroupEvent): Boolean {
        if (event.id.isBlank()) return false
        return try {
            eventsCollection.document(event.id).update(
                mapOf(
                    "title" to event.title,
                    "date" to event.date,
                    "time" to event.time,
                    "place" to event.place,
                    "description" to event.description
                )
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteEvent(eventId: String): Boolean {
        return try {
            eventsCollection.document(eventId).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getCreatedByUser(userId: String): List<GroupEvent> {
        if (userId.isBlank()) return emptyList()
        val snap = eventsCollection.whereEqualTo("createdBy", userId).get().await()
        return snap.documents.mapNotNull { doc ->
            GroupEvent(
                id = doc.id,
                title = doc.getString("title").orEmpty(),
                date = doc.getString("date").orEmpty(),
                time = doc.getString("time").orEmpty(),
                place = doc.getString("place").orEmpty(),
                description = doc.getString("description").orEmpty(),
                groupName = doc.getString("groupName").orEmpty(),
                createdBy = doc.getString("createdBy").orEmpty(),
                createdByName = doc.getString("createdByName").orEmpty(),
                createdByRole = doc.getString("createdByRole").orEmpty(),
                createdAt = (doc.get("createdAt") as? Timestamp)?.toDate() ?: Date()
            )
        }
    }
}
