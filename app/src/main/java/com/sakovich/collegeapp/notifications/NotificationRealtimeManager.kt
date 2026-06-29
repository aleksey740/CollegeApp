package com.sakovich.collegeapp.notifications

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

object NotificationRealtimeManager {
    private const val PREFS_NAME = "notified_ids_prefs"
    private const val KEY_IDS = "ids"
    private const val INITIAL_NOTIFY_WINDOW_MS = 15 * 60 * 1000L

    private var registration: ListenerRegistration? = null
    private var currentUserId: String? = null
    private var isInitialized = false

    fun start(context: Context, userId: String) {
        if (currentUserId == userId && registration != null) return

        stop()
        currentUserId = userId
        isInitialized = false

        registration = Firebase.firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: return@addSnapshotListener
                val now = System.currentTimeMillis()

                if (!isInitialized) {
                    docs.forEach { doc ->
                        val id = doc.id
                        if (isAlreadyNotified(context, id)) return@forEach

                        val isRead = doc.getBoolean("isRead") ?: false
                        val createdAt = (doc.get("createdAt") as? Timestamp)?.toDate() ?: Date(0)
                        if (!isRead && now - createdAt.time <= INITIAL_NOTIFY_WINDOW_MS) {
                            val title = doc.getString("title") ?: "Новое уведомление"
                            val message = doc.getString("message") ?: ""
                            NotificationHelper.showNotification(context, id.hashCode(), title, message)
                            markNotified(context, id)
                        }
                    }
                    isInitialized = true
                    return@addSnapshotListener
                }

                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { change ->
                        val doc = change.document
                        val id = doc.id
                        if (isAlreadyNotified(context, id)) return@forEach

                        val title = doc.getString("title") ?: "Новое уведомление"
                        val message = doc.getString("message") ?: ""
                        NotificationHelper.showNotification(context, id.hashCode(), title, message)
                        markNotified(context, id)
                    }
            }
    }

    fun stop() {
        registration?.remove()
        registration = null
        currentUserId = null
        isInitialized = false
    }

    private fun isAlreadyNotified(context: Context, id: String): Boolean {
        val set = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return set.contains(id)
    }

    private fun markNotified(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (existing.size > 500) {
            existing.clear()
        }
        existing.add(id)
        prefs.edit().putStringSet(KEY_IDS, existing).apply()
    }
}
