package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.Grade
import kotlinx.coroutines.tasks.await

class GradeRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val gradesCollection = db.collection("grades")

    suspend fun addGrade(grade: Grade): String {
        val document = gradesCollection.add(grade.toMap()).await()
        return document.id
    }

    suspend fun updateGrade(gradeId: String, grade: Grade): Boolean {
        return try {
            gradesCollection.document(gradeId).set(grade.toMap()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteGrade(gradeId: String): Boolean {
        return try {
            gradesCollection.document(gradeId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getStudentGrades(studentId: String): List<Grade> {
        return try {
            val snapshot = gradesCollection
                .whereEqualTo("studentId", studentId)
                .get()
                .await()

            snapshot.documents.map { doc ->
                docToGrade(doc.id, doc)
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTeacherGrades(teacherId: String): List<Grade> {
        return try {
            val snapshot = gradesCollection
                .whereEqualTo("teacherId", teacherId)
                .get()
                .await()

            snapshot.documents.map { doc ->
                docToGrade(doc.id, doc)
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getGradeById(gradeId: String): Grade? {
        return try {
            val doc = gradesCollection.document(gradeId).get().await()
            if (doc.exists()) {
                docToGrade(doc.id, doc)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun docToGrade(id: String, doc: DocumentSnapshot): Grade {
        return Grade(
            id = id,
            studentId = doc.getString("studentId") ?: "",
            studentName = doc.getString("studentName") ?: "",
            subject = doc.getString("subject") ?: "",
            value = (doc.getLong("value") ?: 0).toInt(),
            date = doc.getString("date") ?: "",
            type = doc.getString("type") ?: "",
            teacherId = doc.getString("teacherId") ?: "",
            teacherName = doc.getString("teacherName") ?: "",
            comment = doc.getString("comment") ?: "",
            semester = (doc.getLong("semester") ?: 1L).toInt().coerceAtLeast(1),
            createdAt = doc.getLong("createdAt") ?: 0
        )
    }
}
