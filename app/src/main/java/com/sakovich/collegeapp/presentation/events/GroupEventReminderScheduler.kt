package com.sakovich.collegeapp.presentation.events

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sakovich.collegeapp.data.models.GroupEvent
import com.sakovich.collegeapp.data.repositories.GroupEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

object GroupEventReminderScheduler {
    private const val ACTION = "com.sakovich.collegeapp.action.GROUP_EVENT_REMINDER"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val TWO_HOURS_MS = 2L * 60L * 60L * 1000L

    fun scheduleForEvent(context: Context, event: GroupEvent) {
        val eventTime = parseMillis(event.date, event.time) ?: return
        scheduleOne(context, event, eventTime - DAY_MS, "за день")
        scheduleOne(context, event, eventTime - TWO_HOURS_MS, "за 2 часа")
    }

    fun scheduleForCreator(context: Context, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val events = GroupEventRepository().getCreatedByUser(userId)
            events.forEach { scheduleForEvent(context, it) }
        }
    }

    private fun scheduleOne(context: Context, event: GroupEvent, triggerAt: Long, label: String) {
        if (triggerAt <= System.currentTimeMillis()) return
        val code = "${event.id}:$label".hashCode()
        val intent = Intent(ACTION).apply {
            setPackage(context.packageName)
            component = android.content.ComponentName(context, GroupEventReminderReceiver::class.java)
            putExtra("eventId", event.id)
            putExtra("groupName", event.groupName)
            putExtra("eventTitle", event.title)
            putExtra("eventDate", event.date)
            putExtra("eventTime", event.time)
            putExtra("eventPlace", event.place)
            putExtra("reminderLabel", label)
            putExtra("curatorId", event.createdBy)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun parseMillis(date: String, time: String): Long? {
        return try {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).parse("$date $time")?.time
        } catch (_: Exception) {
            null
        }
    }
}
