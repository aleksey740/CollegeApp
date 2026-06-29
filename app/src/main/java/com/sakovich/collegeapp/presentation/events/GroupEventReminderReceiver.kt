package com.sakovich.collegeapp.presentation.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupEventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("eventId").orEmpty()
        val groupName = intent.getStringExtra("groupName").orEmpty()
        val title = intent.getStringExtra("eventTitle").orEmpty()
        val date = intent.getStringExtra("eventDate").orEmpty()
        val time = intent.getStringExtra("eventTime").orEmpty()
        val place = intent.getStringExtra("eventPlace").orEmpty()
        val label = intent.getStringExtra("reminderLabel").orEmpty()
        val curatorId = intent.getStringExtra("curatorId").orEmpty()
        if (groupName.isBlank() || title.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            val users = UserRepository().getStudentsByGroupName(groupName)
            val ids = (users.map { it.id } + curatorId).filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) return@launch
            val msg = "«$title»\n$date $time\n$place"
            NotificationRepository().createGroupNotification(
                studentIds = ids,
                title = "⏰ Напоминание ($label): мероприятие",
                message = msg,
                type = NotificationType.EVENT,
                relatedId = eventId,
                relatedType = "group_event"
            )
        }
    }
}
