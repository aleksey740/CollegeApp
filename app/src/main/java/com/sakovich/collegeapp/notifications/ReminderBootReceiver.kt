package com.sakovich.collegeapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.presentation.clubs.ClubReminderScheduler
import com.sakovich.collegeapp.presentation.events.GroupEventReminderScheduler
import com.sakovich.collegeapp.presentation.nutrition.MealReminderScheduler

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val uid = Firebase.auth.currentUser?.uid ?: return
        MealReminderScheduler.scheduleForUser(context, uid)
        ClubReminderScheduler.scheduleForUser(context, uid)
        GroupEventReminderScheduler.scheduleForCreator(context, uid)
    }
}
