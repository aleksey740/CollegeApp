package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.ClubLeaderEntry
import com.sakovich.collegeapp.data.models.ClubType
import kotlinx.coroutines.tasks.await

class ClubLeaderRepository {
    private val db = Firebase.firestore
    private val collection = db.collection("club_leaders")

    companion object {
        fun leaderGroupId(entry: ClubLeaderEntry): String {
            return entry.groupId.ifBlank { GroupRepository.groupNameToDocumentId(entry.groupName) }
        }

        fun belongsToGroup(entry: ClubLeaderEntry, groupId: String, groupName: String = ""): Boolean {
            if (groupId.isBlank()) return false
            val entryGid = leaderGroupId(entry)
            if (entryGid.isNotBlank()) return entryGid == groupId
            return groupName.isNotBlank() && entry.groupName.equals(groupName, ignoreCase = true)
        }
    }

    suspend fun getByType(type: ClubType, groupId: String, groupName: String = ""): List<ClubLeaderEntry> {
        if (groupId.isBlank()) return emptyList()
        return try {
            val snapshot = collection.whereEqualTo("type", type.name).get().await()
            snapshot.documents
                .mapNotNull { doc -> fromDoc(doc.id, doc.data) }
                .filter { belongsToGroup(it, groupId, groupName) }
                .sortedBy { it.teacherName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAllForGroup(groupId: String, groupName: String = ""): List<ClubLeaderEntry> {
        if (groupId.isBlank()) return emptyList()
        return try {
            val snapshot = collection.get().await()
            snapshot.documents
                .mapNotNull { doc -> fromDoc(doc.id, doc.data) }
                .filter { belongsToGroup(it, groupId, groupName) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun add(entry: ClubLeaderEntry): Boolean {
        return try {
            collection.add(entry.toMap()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun remove(id: String): Boolean {
        if (id.isBlank()) return false
        return try {
            collection.document(id).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateName(id: String, newName: String): Boolean {
        if (id.isBlank() || newName.isBlank()) return false
        return try {
            collection.document(id).update("teacherName", newName.trim()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun fromDoc(id: String, data: Map<String, Any>?): ClubLeaderEntry? {
        if (data == null) return null
        val type = try {
            ClubType.valueOf(data["type"] as? String ?: ClubType.CLUB.name)
        } catch (_: Exception) {
            ClubType.CLUB
        }
        return ClubLeaderEntry(
            id = id,
            type = type,
            teacherId = data["teacherId"] as? String ?: "",
            teacherName = data["teacherName"] as? String ?: "",
            groupId = data["groupId"] as? String ?: "",
            groupName = data["groupName"] as? String ?: ""
        )
    }
}
