package com.sakovich.collegeapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.notifications.NotificationHelper
import com.sakovich.collegeapp.notifications.NotificationRealtimeManager
import com.sakovich.collegeapp.notifications.PushTokenManager
import com.sakovich.collegeapp.presentation.clubs.ClubReminderScheduler
import com.sakovich.collegeapp.presentation.events.GroupEventReminderScheduler
import com.sakovich.collegeapp.presentation.nutrition.MealReminderScheduler
import com.sakovich.collegeapp.presentation.auth.LoginFragment
import com.sakovich.collegeapp.presentation.main.MainFragment

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationHelper.createNotificationChannel(this)
        requestNotificationsPermissionIfNeeded()

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            val startFragment = if (Firebase.auth.currentUser != null) {
                MainFragment()
            } else {
                LoginFragment()
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, startFragment)
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        val user = Firebase.auth.currentUser
        if (user != null) {
            NotificationRealtimeManager.start(this, user.uid)
            PushTokenManager.syncTokenForCurrentUser(this)
            MealReminderScheduler.scheduleForUser(this, user.uid)
            ClubReminderScheduler.scheduleForUser(this, user.uid)
            GroupEventReminderScheduler.scheduleForCreator(this, user.uid)
        } else {
            NotificationRealtimeManager.stop()
        }
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
