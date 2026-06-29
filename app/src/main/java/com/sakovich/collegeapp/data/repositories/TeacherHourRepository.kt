package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.TeacherHourRecord
import com.sakovich.collegeapp.data.models.TeacherHourType
import kotlinx.coroutines.tasks.await
import java.util.Date

class TeacherHourRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val collection = db.collection("teacher_hours")

    suspend fun getTeacherHours(teacherId: String): List<TeacherHourRecord> {
        return try {
            val snapshot = collection
                .whereEqualTo("teacherId", teacherId)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { doc -> convertToRecord(doc.id, doc.data) }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addTeacherHour(record: TeacherHourRecord): String {
        val docRef = collection.add(record.toMap()).await()
        return docRef.id
    }

    suspend fun updateTeacherHour(record: TeacherHourRecord): Boolean {
        if (record.id.isBlank()) return false
        return try {
            collection.document(record.id).set(record.toMap()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteTeacherHour(recordId: String): Boolean {
        return try {
            collection.document(recordId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun convertToRecord(id: String, data: Map<String, Any>?): TeacherHourRecord? {
        if (data == null) return null

        return try {
            val type = try {
                TeacherHourType.valueOf(data["type"] as? String ?: TeacherHourType.TEACHING.name)
            } catch (_: Exception) {
                TeacherHourType.TEACHING
            }

            val createdAt = when (val value = data["createdAt"]) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> Date()
            }

            TeacherHourRecord(
                id = id,
                teacherId = data["teacherId"] as? String ?: "",
                teacherName = data["teacherName"] as? String ?: "",
                type = type,
                topic = data["topic"] as? String ?: "",
                groupName = data["groupName"] as? String ?: "",
                date = data["date"] as? String ?: "",
                time = data["time"] as? String ?: "",
                hoursCount = (data["hoursCount"] as? Number)?.toInt() ?: 2,
                attendanceCount = (data["attendanceCount"] as? Number)?.toInt() ?: 0,
                notes = data["notes"] as? String ?: "",
                createdAt = createdAt
            )
        } catch (_: Exception) {
            null
        }
    }
}
