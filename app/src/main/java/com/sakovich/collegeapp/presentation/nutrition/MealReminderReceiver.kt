package com.sakovich.collegeapp.presentation.nutrition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.notifications.NotificationHelper
import java.util.Date

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra("userId").orEmpty()
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        if (currentUid != userId) return
        val date = intent.getStringExtra("date").orEmpty()
        val reminderLabel = intent.getStringExtra("reminderLabel").orEmpty()

        val title = "🍽️ Напоминание о питании"
        val prefix = if (reminderLabel.isBlank()) "" else "$reminderLabel: "
        val message = "${prefix}Вы записаны на питание на $date"

        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.showNotification(context, "meal:$userId:$date".hashCode(), title, message)

        val data = mapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "type" to NotificationType.SYSTEM.name,
            "isRead" to false,
            "createdAt" to com.google.firebase.Timestamp(Date()),
            "relatedId" to date,
            "relatedType" to "meal",
            "localOnly" to true
        )
        Firebase.firestore.collection("notifications").add(data)
    }
}
