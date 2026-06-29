package com.sakovich.collegeapp.data.models

import com.google.firebase.firestore.Exclude

data class ScheduleItem(
    @get:Exclude
    val id: String = "",
    val day: String = "",
    val date: String = "",
    val time: String = "",
    val subject: String = "",
    val description: String = "",
    val room: String = "",
    val teacherId: String = "",
    val teacherName: String = "",

    val isSubgroup: Boolean = false,
    val teacherName2: String = "",
    val room2: String = "",
    val type: ScheduleType = ScheduleType.LECTURE,
    val group: String = "",
    val createdAt: java.util.Date = java.util.Date(),
    val createdBy: String = "",
    val createdByRole: String = "",

    val assignedStudentIds: List<String> = emptyList()
) {
    fun getTimeStart(): String {
        return time.split("-").firstOrNull()?.trim() ?: ""
    }

    fun getTimeEnd(): String {
        return time.split("-").lastOrNull()?.trim() ?: ""
    }

    companion object {
        fun fromMap(map: Map<String, Any>, id: String = ""): ScheduleItem? {
            return try {
                val day = map["day"] as? String ?: ""
                val date = map["date"] as? String ?: ""
                val time = map["time"] as? String ?: ""
                val subject = map["subject"] as? String ?: ""
                val teacherName = map["teacherName"] as? String ?: ""
                val room = map["room"] as? String ?: ""
                val isSubgroup = map["isSubgroup"] as? Boolean ?: false
                val teacherName2 = map["teacherName2"] as? String ?: ""
                val room2 = map["room2"] as? String ?: ""
                val typeString = map["type"] as? String ?: "LECTURE"
                val group = map["group"] as? String ?: ""
                val createdAt = map["createdAt"] as? java.util.Date ?: java.util.Date()
                val createdBy = map["createdBy"] as? String ?: ""
                val createdByRole = map["createdByRole"] as? String ?: ""
                val assignedStudentIds = (map["assignedStudentIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                val type = try {
                    ScheduleType.valueOf(typeString)
                } catch (e: IllegalArgumentException) {
                    ScheduleType.LECTURE
                }

                ScheduleItem(
                    id = id,
                    day = day,
                    date = date,
                    time = time,
                    subject = subject,
                    teacherName = teacherName,
                    room = room,
                    isSubgroup = isSubgroup,
                    teacherName2 = teacherName2,
                    room2 = room2,
                    type = type,
                    group = group,
                    createdAt = createdAt,
                    createdBy = createdBy,
                    createdByRole = createdByRole,
                    assignedStudentIds = assignedStudentIds
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
