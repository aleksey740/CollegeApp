package com.sakovich.collegeapp.notifications

import android.content.Context
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

object PushTokenManager {
    private const val PREFS_NAME = "push_prefs"
    private const val KEY_LAST_TOKEN = "last_token"

    fun syncTokenForCurrentUser(context: Context) {
        Firebase.messaging.token
            .addOnSuccessListener { token ->
                saveTokenLocally(context, token)
                saveTokenToCurrentUser(token)
            }
            .addOnFailureListener {

            }
    }

    fun saveTokenFromService(context: Context, token: String) {
        saveTokenLocally(context, token)
        saveTokenToCurrentUser(token)
    }

    private fun saveTokenToCurrentUser(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users")
            .document(uid)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "fcmUpdatedAt" to com.google.firebase.Timestamp.now()
                ),
                SetOptions.merge()
            )
    }

    private fun saveTokenLocally(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TOKEN, token)
            .apply()
    }
}
