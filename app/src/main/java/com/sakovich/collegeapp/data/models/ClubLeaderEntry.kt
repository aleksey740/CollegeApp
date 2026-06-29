package com.sakovich.collegeapp.data.models

import com.google.firebase.firestore.Exclude

data class ClubLeaderEntry(
    @get:Exclude
    val id: String = "",
    val type: ClubType = ClubType.CLUB,
    val teacherId: String = "",
    val teacherName: String = "",
    val groupId: String = "",
    val groupName: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.name,
        "teacherId" to teacherId,
        "teacherName" to teacherName,
        "groupId" to groupId,
        "groupName" to groupName
    )
}
