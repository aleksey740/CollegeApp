package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.Club
import com.sakovich.collegeapp.data.models.ClubType
import kotlinx.coroutines.tasks.await
import java.util.Date

class ClubRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val collection = db.collection("clubs")

    companion object {
        fun clubGroupId(club: Club): String {
            return club.groupId.ifBlank { GroupRepository.groupNameToDocumentId(club.groupName) }
        }

        fun belongsToGroup(club: Club, groupId: String, groupName: String = ""): Boolean {
            if (groupId.isBlank()) return false
            val clubGid = clubGroupId(club)
            if (clubGid.isNotBlank()) return clubGid == groupId
            return groupName.isNotBlank() && club.groupName.equals(groupName, ignoreCase = true)
        }
    }

    suspend fun getAllActive(): List<Club> {
        return try {
            val snapshot = collection
                .whereEqualTo("isActive", true)
                .get()
                .await()
            snapshot.documents
                .mapNotNull { convert(it.id, it.data) }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getActiveForGroup(groupId: String, groupName: String = ""): List<Club> {
        if (groupId.isBlank()) return emptyList()
        return getAllActive().filter { belongsToGroup(it, groupId, groupName) }
    }

    suspend fun getByTeacherId(teacherId: String): List<Club> {
        if (teacherId.isBlank()) return emptyList()
        return try {
            val snapshot = collection
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("isActive", true)
                .get()
                .await()
            snapshot.documents
                .mapNotNull { convert(it.id, it.data) }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getById(id: String): Club? {
        return try {
            val doc = collection.document(id).get().await()
            convert(doc.id, doc.data)
        } catch (_: Exception) {
            null
        }
    }

    /** Уникальность названия в пределах группы и типа (кружок / секция / факультатив). */
    suspend fun existsByName(
        name: String,
        groupId: String,
        type: ClubType,
        excludeId: String? = null
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank() || groupId.isBlank()) return false
        return try {
            getAllActive().any { club ->
                clubGroupId(club) == groupId &&
                    club.type == type &&
                    club.name.trim().equals(trimmed, ignoreCase = true) &&
                    (excludeId == null || club.id != excludeId)
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun create(club: Club): String {
        val ref = collection.add(club.toMap()).await()
        return ref.id
    }

    suspend fun update(club: Club): Boolean {
        if (club.id.isBlank()) return false
        return try {
            collection.document(club.id).set(club.toMap()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(clubId: String): Boolean {
        return try {
            collection.document(clubId).update("isActive", false).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun join(clubId: String, userId: String, userName: String, userGroupId: String? = null): Boolean {
        val club = getById(clubId) ?: return false
        if (!club.canJoin(userId, userGroupId)) return false

        val ids = (club.participantIds + userId).distinct()
        val names = (club.participantNames + userName).distinct()
        return update(club.copy(participantIds = ids, participantNames = names))
    }

    suspend fun removeParticipant(clubId: String, userId: String, userName: String? = null): Boolean {
        val club = getById(clubId) ?: return false
        if (!club.participantIds.contains(userId)) return false

        val index = club.participantIds.indexOf(userId)
        val ids = club.participantIds.toMutableList().apply { removeAt(index) }
        val names = club.participantNames.toMutableList().apply {
            if (index in indices) removeAt(index) else userName?.let { n -> remove(n) }
        }
        return update(club.copy(participantIds = ids, participantNames = names))
    }

    private fun convert(id: String, data: Map<String, Any>?): Club? {
        if (data == null) return null
        return try {
            val type = try {
                ClubType.valueOf(data["type"] as? String ?: ClubType.CLUB.name)
            } catch (_: Exception) {
                ClubType.CLUB
            }
            Club(
                id = id,
                name = data["name"] as? String ?: "",
                description = data["description"] as? String ?: "",
                type = type,
                teacherId = data["teacherId"] as? String ?: "",
                teacherName = data["teacherName"] as? String ?: "",
                groupId = data["groupId"] as? String ?: "",
                groupName = data["groupName"] as? String ?: "",
                schedule = data["schedule"] as? String ?: "",
                nextSessionDate = data["nextSessionDate"] as? String ?: "",
                nextSessionTime = data["nextSessionTime"] as? String ?: "",
                location = data["location"] as? String ?: "",
                maxParticipants = (data["maxParticipants"] as? Long)?.toInt() ?: 30,
                participantIds = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                participantNames = (data["participantNames"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                isActive = data["isActive"] as? Boolean ?: true,
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
            )
        } catch (_: Exception) {
            null
        }
    }
}
