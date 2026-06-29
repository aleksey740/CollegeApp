package com.sakovich.collegeapp.presentation.clubs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.notifications.NotificationHelper
import java.util.Date
import com.google.firebase.ktx.Firebase

class ClubReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra("userId").orEmpty()
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        if (currentUid != userId) return
        val clubId = intent.getStringExtra("clubId").orEmpty()
        val clubName = intent.getStringExtra("clubName").orEmpty()
        val clubType = intent.getStringExtra("clubType").orEmpty()
        val date = intent.getStringExtra("date").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val reminderLabel = intent.getStringExtra("reminderLabel").orEmpty()

        val typeText = when (clubType) {
            "SECTION" -> "секции"
            "ELECTIVE" -> "факультатива"
            else -> "кружка"
        }
        val title = "⏰ Напоминание о $typeText"
        val message = "$reminderLabel: \"$clubName\" состоится $date в $time"

        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.showNotification(context, "$clubId:$reminderLabel".hashCode(), title, message)

        val data = mapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "type" to NotificationType.SCHEDULE.name,
            "isRead" to false,
            "createdAt" to com.google.firebase.Timestamp(Date()),
            "relatedId" to clubId,
            "relatedType" to "club",
            "localOnly" to true
        )
        Firebase.firestore.collection("notifications").add(data)
    }
}
