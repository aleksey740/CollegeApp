package com.sakovich.collegeapp.presentation.nutrition

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sakovich.collegeapp.data.repositories.MealRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MealReminderScheduler {
    private const val PREFS = "meal_reminder_prefs"
    private const val KEY_CODES = "codes"

    private const val OPTIONS_PREFS = "meal_reminder_options"
    private const val KEY_MORNING = "morning_8_00"
    private const val KEY_EVE = "evening_before"

    private const val LABEL_EVE = "Накануне"
    private const val LABEL_MORNING = "Утром 8:00"
    private const val ACTION_MEAL_REMINDER = "com.sakovich.collegeapp.action.MEAL_REMINDER"

    fun scheduleForUser(context: Context, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = MealRepository()
            val subscriptions = repository.getUserSubscriptions(userId, onlySubscribed = true)

            clearScheduled(context)

            val opts = getUserOptions(context, userId)
            subscriptions.forEach { item ->
                if (opts.morningEnabled) {
                    val reminderMorning = parseMorningMillis(item.date) ?: return@forEach
                    scheduleOne(context, userId, item.date, reminderMorning, LABEL_MORNING)
                }
                if (opts.eveEnabled) {
                    val reminderEve = parseEveBeforeMillis(item.date) ?: return@forEach
                    scheduleOne(context, userId, item.date, reminderEve, LABEL_EVE)
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
        morningEnabled: Boolean,
        eveEnabled: Boolean
    ) {
        val prefs = context.getSharedPreferences("$OPTIONS_PREFS:$userId", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_MORNING, morningEnabled)
            .putBoolean(KEY_EVE, eveEnabled)
            .apply()
    }

    private fun getUserOptions(context: Context, userId: String): ReminderOptions {
        val prefs = context.getSharedPreferences("$OPTIONS_PREFS:$userId", Context.MODE_PRIVATE)

        val morning = prefs.getBoolean(KEY_MORNING, true)
        val eve = prefs.getBoolean(KEY_EVE, true)
        return ReminderOptions(morningEnabled = morning, eveEnabled = eve)
    }

    fun getUserOptionsForUi(context: Context, userId: String): ReminderOptions {
        return getUserOptions(context, userId)
    }

    data class ReminderOptions(
        val morningEnabled: Boolean,
        val eveEnabled: Boolean
    )

    private fun scheduleOne(
        context: Context,
        userId: String,
        date: String,
        triggerAt: Long,
        reminderLabel: String
    ) {
        if (triggerAt <= System.currentTimeMillis()) return
        val requestCode = "$userId:$date:$reminderLabel".hashCode()
        val intent = Intent(ACTION_MEAL_REMINDER, null).apply {
            setPackage(context.packageName)
            putExtra("userId", userId)
            putExtra("date", date)
            putExtra("reminderLabel", reminderLabel)
        }

        intent.component = android.content.ComponentName(context, MealReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        rememberCode(context, requestCode)
    }

    private fun parseMorningMillis(date: String): Long? {
        return try {
            val parser = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val day = parser.parse(date) ?: return null
            val calendar = Calendar.getInstance().apply {
                time = day

                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEveBeforeMillis(date: String): Long? {
        return try {
            val parser = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val day = parser.parse(date) ?: return null
            val calendar = Calendar.getInstance().apply {
                time = day
                add(Calendar.DAY_OF_MONTH, -1)

                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun clearScheduled(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet())?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        codes.forEach { code ->
            val intent = Intent(ACTION_MEAL_REMINDER, null).apply {
                setPackage(context.packageName)
                component = android.content.ComponentName(context, MealReminderReceiver::class.java)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
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

    private fun rememberCode(context: Context, code: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_CODES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(code.toString())
        prefs.edit().putStringSet(KEY_CODES, set).apply()
    }
}
