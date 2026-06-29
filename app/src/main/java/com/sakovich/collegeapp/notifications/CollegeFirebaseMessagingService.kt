package com.sakovich.collegeapp.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CollegeFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.saveTokenFromService(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val title = message.notification?.title
            ?: data["title"]
            ?: "Новое уведомление"
        val body = message.notification?.body
            ?: data["message"]
            ?: ""

        if (title.isBlank() && body.isBlank()) return

        val notificationId = data["notificationId"]?.takeIf { it.isNotBlank() }
            ?: message.messageId
            ?: System.currentTimeMillis().toString()

        NotificationHelper.createNotificationChannel(applicationContext)
        NotificationHelper.showNotification(
            context = applicationContext,
            id = notificationId.hashCode(),
            title = title,
            message = body
        )
    }
}
