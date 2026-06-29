package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.AbsenceReason
import kotlinx.coroutines.tasks.await
import java.util.Date

class AbsenceRepository {
    private val firestore = FirebaseFirestore.getInstance()
    val absenceCollection = firestore.collection("absences")


    suspend fun addAbsence(absence: Absence): String {
        return try {
            val docRef = absenceCollection.add(absence.toMap()).await()
            docRef.id
        } catch (e: Exception) {
            throw e
        }
    }


    suspend fun updateAbsence(absenceId: String, absence: Absence): Boolean {
        return try {
            absenceCollection.document(absenceId).set(absence.toMap()).await()
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun deleteAbsence(absenceId: String): Boolean {
        return try {
            absenceCollection.document(absenceId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun getStudentAbsences(studentId: String): List<Absence> {
        return try {
            val snapshot = absenceCollection
                .whereEqualTo("studentId", studentId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                convertToAbsence(doc.id, doc.data)
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }


    suspend fun getGroupAbsences(groupName: String): List<Absence> {
        return try {
            val snapshot = absenceCollection
                .whereEqualTo("studentGroup", groupName)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                convertToAbsence(doc.id, doc.data)
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }


    suspend fun getAllAbsences(): List<Absence> {
        return try {
            val snapshot = absenceCollection
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                convertToAbsence(doc.id, doc.data)
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }


    suspend fun getAbsenceById(absenceId: String): Absence? {
        return try {
            val doc = absenceCollection.document(absenceId).get().await()
            if (doc.exists()) {
                convertToAbsence(doc.id, doc.data)
            } else null
        } catch (e: Exception) {
            null
        }
    }


    suspend fun getStudentAbsenceStats(studentId: String): AbsenceStats {
        val absences = getStudentAbsences(studentId)
        val totalHours = absences.sumOf { it.hours }
        val excusedHours = absences.filter { it.isExcused }.sumOf { it.hours }
        val unexcusedHours = totalHours - excusedHours
        
        return AbsenceStats(
            totalAbsences = absences.size,
            totalHours = totalHours,
            excusedHours = excusedHours,
            unexcusedHours = unexcusedHours
        )
    }

    private fun convertToAbsence(id: String, data: Map<String, Any>?): Absence? {
        if (data == null) return null
        
        return try {
            val reasonStr = data["reason"] as? String ?: "WITHOUT_REASON"
            val reason = try {
                AbsenceReason.valueOf(reasonStr)
            } catch (e: Exception) {
                AbsenceReason.WITHOUT_REASON
            }

            Absence(
                id = id,
                studentId = data["studentId"] as? String ?: "",
                studentName = data["studentName"] as? String ?: "",
                studentGroup = data["studentGroup"] as? String ?: "",
                subject = data["subject"] as? String ?: "",
                date = data["date"] as? String ?: "",
                hours = (data["hours"] as? Long)?.toInt() ?: 2,
                reason = reason,
                comment = data["comment"] as? String ?: "",
                createdBy = data["createdBy"] as? String ?: "",
                createdByName = data["createdByName"] as? String ?: "",
                createdByRole = data["createdByRole"] as? String ?: "",
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                isExcused = data["isExcused"] as? Boolean ?: false
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class AbsenceStats(
    val totalAbsences: Int = 0,
    val totalHours: Int = 0,
    val excusedHours: Int = 0,
    val unexcusedHours: Int = 0
)
