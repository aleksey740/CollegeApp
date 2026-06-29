package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.MealSubscription
import com.sakovich.collegeapp.data.models.User
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MealRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val collection = db.collection("meal_subscriptions")

    suspend fun setSubscription(
        date: String,
        user: User,
        isSubscribed: Boolean,
        updatedById: String
    ): Boolean {

        if (isSubscribed && isWeekend(date)) return false

        val docId = "${date}_${user.id}"
        val item = MealSubscription(
            id = docId,
            date = date,
            userId = user.id,
            userName = user.fullName.ifBlank { user.email },
            groupName = user.groupName,
            isSubscribed = isSubscribed,
            updatedById = updatedById,
            updatedAt = Date()
        )
        return try {
            collection.document(docId).set(item.toMap()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getUserSubscription(date: String, userId: String): MealSubscription? {
        return try {
            val docId = "${date}_${userId}"
            val doc = collection.document(docId).get().await()
            convert(doc.id, doc.data)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getSubscribedByDate(date: String, groupName: String? = null): List<MealSubscription> {
        return try {
            val snapshot = if (groupName.isNullOrBlank()) {
                collection
                    .whereEqualTo("date", date)
                    .whereEqualTo("isSubscribed", true)
                    .get()
                    .await()
            } else {
                collection
                    .whereEqualTo("date", date)
                    .whereEqualTo("isSubscribed", true)
                    .whereEqualTo("groupName", groupName)
                    .get()
                    .await()
            }
            snapshot.documents.mapNotNull { convert(it.id, it.data) }.sortedBy { it.userName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUserSubscriptions(userId: String, onlySubscribed: Boolean = false): List<MealSubscription> {
        return try {
            val snapshot = if (onlySubscribed) {
                collection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isSubscribed", true)
                    .get()
                    .await()
            } else {
                collection
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
            }
            snapshot.documents.mapNotNull { convert(it.id, it.data) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun convert(id: String, data: Map<String, Any>?): MealSubscription? {
        if (data == null) return null
        return try {
            val updatedAt = when (val value = data["updatedAt"]) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> Date()
            }

            MealSubscription(
                id = id,
                date = data["date"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                userName = data["userName"] as? String ?: "",
                groupName = data["groupName"] as? String ?: "",
                isSubscribed = data["isSubscribed"] as? Boolean ?: false,
                updatedById = data["updatedById"] as? String ?: "",
                updatedAt = updatedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isWeekend(date: String): Boolean {
        return try {
            val parser = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val parsed = parser.parse(date) ?: return false
            val cal = Calendar.getInstance().apply { time = parsed }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            day == Calendar.SATURDAY || day == Calendar.SUNDAY
        } catch (_: Exception) {
            false
        }
    }
}
