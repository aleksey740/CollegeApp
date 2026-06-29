package com.sakovich.collegeapp.data.models

import com.google.firebase.firestore.Exclude
import java.util.Date

data class Club(
    @get:Exclude
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: ClubType = ClubType.CLUB,
    val teacherId: String = "",
    val teacherName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val schedule: String = "",
    val nextSessionDate: String = "",
    val nextSessionTime: String = "",
    val location: String = "",
    val maxParticipants: Int = 30,
    val participantIds: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Date = Date()
) {
    fun canJoin(userId: String, userGroupId: String? = null): Boolean {
        if (!isActive || participantIds.contains(userId) || participantIds.size >= maxParticipants) return false
        if (groupId.isNotBlank() && !userGroupId.isNullOrBlank() && groupId != userGroupId) return false
        return true
    }

    fun isParticipant(userId: String): Boolean = participantIds.contains(userId)

    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "type" to type.name,
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "groupId" to groupId,
            "groupName" to groupName,
            "schedule" to schedule,
            "nextSessionDate" to nextSessionDate,
            "nextSessionTime" to nextSessionTime,
            "location" to location,
            "maxParticipants" to maxParticipants,
            "participantIds" to participantIds,
            "participantNames" to participantNames,
            "isActive" to isActive,
            "createdAt" to com.google.firebase.Timestamp(createdAt)
        )
    }
}

enum class ClubType {
    CLUB,
    SECTION,
    ELECTIVE
}
