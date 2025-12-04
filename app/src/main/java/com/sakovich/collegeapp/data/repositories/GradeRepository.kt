package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.Grade
import kotlinx.coroutines.tasks.await

class GradeRepository {
    private val db: FirebaseFirestore = Firebase.firestore

    suspend fun addGrade(grade: Grade): String {
        val document = db.collection("grades").add(grade.toMap()).await()
        return document.id
    }

    suspend fun getStudentGrades(studentId: String): List<Grade> {
        val snapshot = db.collection("grades")
            .whereEqualTo("studentId", studentId)
            .get()
            .await()

        return snapshot.toObjects(Grade::class.java)
    }

    suspend fun getTeacherGrades(teacherId: String): List<Grade> {
        val snapshot = db.collection("grades")
            .whereEqualTo("teacherId", teacherId)
            .get()
            .await()

        return snapshot.toObjects(Grade::class.java)
    }
}