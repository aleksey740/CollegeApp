package com.sakovich.collegeapp.presentation.clubs

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sakovich.collegeapp.data.repositories.ClubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ClubReminderScheduler {
    private const val PREFS = "club_reminder_prefs"
    private const val KEY_CODES = "request_codes"
    private const val ACTION_CLUB_REMINDER = "com.sakovich.collegeapp.action.CLUB_REMINDER"
    private const val OPTIONS_PREFS = "club_reminder_options"
    private const val KEY_24H = "interval_24h"
    private const val KEY_2H = "interval_2h"
    private const val KEY_30M = "interval_30m"

    private const val MILLIS_24H = 24L * 60L * 60L * 1000L
    private const val MILLIS_2H = 2L * 60L * 60L * 1000L
    private const val MILLIS_30M = 30L * 60L * 1000L

    private const val LABEL_24H = "За 24 часа"
    private const val LABEL_2H = "За 2 часа"
    private const val LABEL_30M = "За 30 минут"

    fun scheduleForUser(context: Context, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = ClubRepository()
            val clubs = repository.getAllActive().filter { it.participantIds.contains(userId) }

            clearScheduled(context)
            val opts = getUserOptions(context, userId)
            clubs.forEach { club ->
                val nextSession = ClubScheduleHelper.resolveNextSession(club) ?: return@forEach
                val (sessionDate, sessionTimeStr) = nextSession
                val sessionTime = ClubScheduleHelper.parseSessionMillis(sessionDate, sessionTimeStr) ?: return@forEach
                if (opts.interval24hEnabled) {
                    scheduleOne(
                        context,
                        userId,
                        club.id,
                        club.name,
                        club.type.name,
                        sessionDate,
                        sessionTimeStr,
                        sessionTime - MILLIS_24H,
                        LABEL_24H
                    )
                }
                if (opts.interval2hEnabled) {
                    scheduleOne(
                        context,
                        userId,
                        club.id,
                        club.name,
                        club.type.name,
                        sessionDate,
                        sessionTimeStr,
                        sessionTime - MILLIS_2H,
                        LABEL_2H
                    )
                }
                if (opts.interval30mEnabled) {
                    scheduleOne(
                        context,
                        userId,
                        club.id,
                        club.name,
                        club.type.name,
                        sessionDate,
                        sessionTimeStr,
                        sessionTime - MILLIS_30M,
                        LABEL_30M
                    )
                }
            }
        }
    }

    fun cancelAll(context: Context) {
        clearScheduled(context)
    }

    fun setUserOptions(
        context: Context,
        userId: String,
        interval24hEnabled: Boolean,
        interval2hEnabled: Boolean,
        interval30mEnabled: Boolean
    ) {
        val prefs = context.getSharedPreferences("$OPTIONS_PREFS:$userId", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_24H, interval24hEnabled)
            .putBoolean(KEY_2H, interval2hEnabled)
            .putBoolean(KEY_30M, interval30mEnabled)
            .apply()
    }

    private fun getUserOptions(context: Context, userId: String): ReminderOptions {
        val prefs = context.getSharedPreferences("$OPTIONS_PREFS:$userId", Context.MODE_PRIVATE)

        val i24 = prefs.getBoolean(KEY_24H, true)
        val i2 = prefs.getBoolean(KEY_2H, true)
        val i30 = prefs.getBoolean(KEY_30M, true)
        return ReminderOptions(i24, i2, i30)
    }

    fun getUserOptionsForUi(context: Context, userId: String): ReminderOptions {
        return getUserOptions(context, userId)
    }

    data class ReminderOptions(
        val interval24hEnabled: Boolean,
        val interval2hEnabled: Boolean,
        val interval30mEnabled: Boolean
    )

    private fun scheduleOne(
        context: Context,
        userId: String,
        clubId: String,
        clubName: String,
        clubType: String,
        date: String,
        time: String,
        triggerAt: Long,
        reminderLabel: String
    ) {
        if (triggerAt <= System.currentTimeMillis()) return

        val requestCode = "$userId:$clubId:$reminderLabel".hashCode()
        val intent = Intent(ACTION_CLUB_REMINDER, null).apply {
            setPackage(context.packageName)
            putExtra("userId", userId)
            putExtra("clubId", clubId)
            putExtra("clubName", clubName)
            putExtra("clubType", clubType)
            putExtra("date", date)
            putExtra("time", time)
            putExtra("reminderLabel", reminderLabel)
            component = android.content.ComponentName(context, ClubReminderReceiver::class.java)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        rememberCode(context, requestCode)
    }

    private fun clearScheduled(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet())?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        codes.forEach { requestCode ->
            val intent = Intent(ACTION_CLUB_REMINDER, null).apply {
                setPackage(context.packageName)
                component = android.content.ComponentName(context, ClubReminderReceiver::class.java)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
        prefs.edit().putStringSet(KEY_CODES, emptySet()).apply()
    }

    private fun rememberCode(context: Context, requestCode: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_CODES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(requestCode.toString())
        prefs.edit().putStringSet(KEY_CODES, set).apply()
    }
}
