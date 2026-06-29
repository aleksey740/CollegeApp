package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.JournalColumnLabel
import kotlinx.coroutines.tasks.await
import java.util.Locale

class JournalColumnLabelRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val collection = db.collection("journal_column_labels")

    private fun documentId(teacherId: String, groupName: String, subject: String, date: String): String {
        val safeGroup = groupName.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "group" }
        val safeSubject = subject.trim().lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9а-яё_-]"), "_").ifBlank { "subject" }
        val safeDate = date.replace(".", "_")
        return "${teacherId}_${safeGroup}_${safeSubject}_$safeDate"
    }

    suspend fun getLabelsForWeek(
        teacherId: String,
        groupName: String,
        subject: String,
        dates: List<String>
    ): Map<String, String> {
        if (dates.isEmpty()) return emptyMap()
        return try {
            val docIds = dates.map { documentId(teacherId, groupName, subject, it) }
            val snapshot = collection.whereIn(com.google.firebase.firestore.FieldPath.documentId(), docIds).get().await()
            snapshot.documents.associate { doc ->
                val date = doc.getString("date") ?: return@associate "" to ""
                val lessonType = doc.getString("lessonType") ?: ""
                date to lessonType
            }.filter { it.value.isNotEmpty() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun setLabel(
        teacherId: String,
        groupName: String,
        subject: String,
        date: String,
        lessonType: String
    ): Boolean {
        return try {
            val id = documentId(teacherId, groupName, subject, date)
            val label = JournalColumnLabel(
                id = id,
                teacherId = teacherId,
                groupName = groupName,
                subject = subject,
                date = date,
                lessonType = lessonType
            )
            collection.document(id).set(label.toMap()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearLabel(
        teacherId: String,
        groupName: String,
        subject: String,
        date: String
    ): Boolean {
        return try {
            val id = documentId(teacherId, groupName, subject, date)
            collection.document(id).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }
}
