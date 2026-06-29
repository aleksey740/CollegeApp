package com.sakovich.collegeapp.presentation.auth

import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.notifications.NotificationHelper
import com.sakovich.collegeapp.presentation.legal.PrivacyPolicyFragment
import com.sakovich.collegeapp.notifications.NotificationRealtimeManager
import com.sakovich.collegeapp.notifications.PushTokenManager
import com.sakovich.collegeapp.presentation.clubs.ClubReminderScheduler
import com.sakovich.collegeapp.presentation.nutrition.MealReminderScheduler
import com.sakovich.collegeapp.presentation.main.MainFragment

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth

        if (auth.currentUser != null) {
            startRealtimePushForCurrentUser()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment())
                .commit()
            return
        }

        val emailEditText = view.findViewById<TextInputEditText>(R.id.emailEditText)
        val passwordEditText = view.findViewById<TextInputEditText>(R.id.passwordEditText)
        val loginButton = view.findViewById<MaterialButton>(R.id.loginButton)
        val registerLink = view.findViewById<TextView>(R.id.registerLink)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                emailEditText.error = "Заполните все поля"
            }
        }

        registerLink.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }

        setupPrivacyPolicyConsent(view)
    }

    private fun setupPrivacyPolicyConsent(view: View) {
        val consentText = view.findViewById<TextView>(R.id.privacyPolicyConsentText)
        val fullText = "Входя в приложение, вы соглашаетесь с Политикой конфиденциальности."
        val linkText = "Политикой конфиденциальности"
        val start = fullText.indexOf(linkText)
        val end = start + linkText.length
        val spannable = SpannableString(fullText).apply {
            setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, PrivacyPolicyFragment.newInstance())
                        .addToBackStack(null)
                        .commit()
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = 0xFF60A5FA.toInt()
                    ds.isUnderlineText = false
                }
            }, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(0xFF60A5FA.toInt()), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        consentText.text = spannable
        consentText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun loginUser(email: String, password: String) {
        val loginButton = view?.findViewById<MaterialButton>(R.id.loginButton)
        loginButton?.text = "Вход..."
        loginButton?.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {

                    Toast.makeText(requireContext(), "Вход успешен!", Toast.LENGTH_SHORT).show()
                    startRealtimePushForCurrentUser()

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, MainFragment())
                        .commit()

                } else {

                    Toast.makeText(requireContext(), "Ошибка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    loginButton?.text = "Войти"
                    loginButton?.isEnabled = true
                }
            }
    }

    private fun startRealtimePushForCurrentUser() {
        val userId = auth.currentUser?.uid ?: return
        NotificationHelper.createNotificationChannel(requireContext())
        NotificationRealtimeManager.start(requireContext(), userId)
        PushTokenManager.syncTokenForCurrentUser(requireContext())
        MealReminderScheduler.scheduleForUser(requireContext(), userId)
        ClubReminderScheduler.scheduleForUser(requireContext(), userId)
    }
}
