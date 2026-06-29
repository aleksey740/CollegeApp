package com.sakovich.collegeapp.presentation.auth

import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.models.User.Companion.GENDER_FEMALE
import com.sakovich.collegeapp.data.models.User.Companion.GENDER_MALE
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.presentation.legal.PrivacyPolicyFragment
import com.sakovich.collegeapp.notifications.NotificationHelper
import com.sakovich.collegeapp.notifications.NotificationRealtimeManager
import com.sakovich.collegeapp.notifications.PushTokenManager
import com.sakovich.collegeapp.presentation.clubs.ClubReminderScheduler
import com.sakovich.collegeapp.presentation.nutrition.MealReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private val adminRepository = AdminRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            auth = Firebase.auth

            val emailEditText = view.findViewById<TextInputEditText>(R.id.emailEditText)
            val passwordEditText = view.findViewById<TextInputEditText>(R.id.passwordEditText)
            val fullNameEditText = view.findViewById<TextInputEditText>(R.id.fullNameEditText)
            val genderInputLayout = view.findViewById<TextInputLayout>(R.id.genderInputLayout)
            val genderDropdown = view.findViewById<AutoCompleteTextView>(R.id.genderSpinner)
            val groupInputLayout = view.findViewById<TextInputLayout>(R.id.groupInputLayout)
            val groupEditText = view.findViewById<TextInputEditText>(R.id.groupEditText)
            val roleDropdown = view.findViewById<AutoCompleteTextView>(R.id.roleSpinner)
            val registerButton = view.findViewById<MaterialButton>(R.id.registerButton)
            val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            val loginLink = view.findViewById<TextView>(R.id.loginLink)

            if (emailEditText == null || passwordEditText == null || fullNameEditText == null ||
                genderInputLayout == null || genderDropdown == null ||
                groupInputLayout == null || groupEditText == null || roleDropdown == null || registerButton == null ||
                progressBar == null || loginLink == null) {
                android.util.Log.e("RegisterFragment", "Не все view найдены в layout")
                Toast.makeText(requireContext(), "Ошибка инициализации формы", Toast.LENGTH_LONG).show()
                return
            }

            val genders = arrayOf("Мужской", "Женский")
            val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genders)
            genderDropdown.setAdapter(genderAdapter)
            genderDropdown.setText("Мужской", false)
            groupInputLayout.visibility = View.VISIBLE

            fun studentRoleLabel(): String =
                if (genderDropdown.text?.toString() == "Женский") "Учащаяся" else "Учащийся"

            fun refreshRoleDropdown(preserveSelection: Boolean = true) {
                val current = roleDropdown.text?.toString().orEmpty()
                val roles = arrayOf(studentRoleLabel(), "Староста", "Куратор")
                val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
                roleDropdown.setAdapter(roleAdapter)
                val next = when {
                    !preserveSelection -> studentRoleLabel()
                    current == "Староста" || current == "Куратор" -> current
                    else -> studentRoleLabel()
                }
                roleDropdown.setText(next, false)
            }

            refreshRoleDropdown(false)

            genderDropdown.setOnItemClickListener { _, _, _, _ ->
                genderInputLayout.error = null
                refreshRoleDropdown()
            }

            roleDropdown.setOnItemClickListener { _, _, _, _ ->
                groupInputLayout.visibility = View.VISIBLE
            }

            registerButton.setOnClickListener {
                val email = emailEditText.text?.toString()?.trim() ?: ""
                val password = passwordEditText.text?.toString() ?: ""
                val fullName = fullNameEditText.text?.toString()?.trim() ?: ""
                val selectedRole = roleDropdown.text?.toString() ?: studentRoleLabel()
                val selectedGender = genderDropdown.text?.toString().orEmpty()

                val role = when (selectedRole) {
                    "Староста" -> "headman"
                    "Куратор" -> "teacher"
                    else -> "student"
                }

                val gender = when (selectedGender) {
                    "Женский" -> GENDER_FEMALE
                    "Мужской" -> GENDER_MALE
                    else -> ""
                }

                val group = groupEditText.text?.toString()?.trim() ?: ""

                if (role == "admin") {
                    Toast.makeText(requireContext(), "Роль администратора недоступна для регистрации", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                groupInputLayout.error = null
                when {
                    email.isEmpty() -> {
                        emailEditText.error = "Введите email"
                        emailEditText.requestFocus()
                    }
                    password.isEmpty() -> {
                        passwordEditText.error = "Введите пароль"
                        passwordEditText.requestFocus()
                    }
                    password.length < 6 -> {
                        passwordEditText.error = "Пароль должен быть не менее 6 символов"
                        passwordEditText.requestFocus()
                    }
                    fullName.isEmpty() -> {
                        fullNameEditText.error = "Введите ФИО"
                        fullNameEditText.requestFocus()
                    }
                    gender.isEmpty() -> {
                        genderInputLayout.error = "Выберите пол"
                        genderDropdown.requestFocus()
                    }
                    group.isEmpty() -> {
                        groupInputLayout.error = "Введите группу"
                        groupEditText.requestFocus()
                    }
                    else -> {

                        val groupId = GroupRepository.groupNameToDocumentId(group)
                        progressBar.visibility = View.VISIBLE
                        CoroutineScope(Dispatchers.IO).launch {
                            val allowed = adminRepository.canRegisterToGroup(role, groupId)
                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                if (!allowed) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Лимит для группы превышен. Регистрация невозможна.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    registerUser(email, password, fullName, group, role, gender, progressBar)
                                }
                            }
                        }
                    }
                }
            }

            loginLink.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            setupPrivacyPolicyConsent(view)
        } catch (e: Exception) {
            android.util.Log.e("RegisterFragment", "Ошибка в onViewCreated: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupPrivacyPolicyConsent(view: View) {
        val consentText = view.findViewById<TextView>(R.id.privacyPolicyConsentText)
        val fullText = "Продолжая, вы соглашаетесь с Политикой конфиденциальности."
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
                    ds.color = 0xFFC084FC.toInt()
                    ds.isUnderlineText = false
                }
            }, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(0xFFC084FC.toInt()), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        consentText.text = spannable
        consentText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun registerUser(
        email: String,
        password: String,
        fullName: String,
        group: String,
        role: String,
        gender: String,
        progressBar: ProgressBar
    ) {
        try {
            progressBar.visibility = View.VISIBLE

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    progressBar.visibility = View.GONE

                    if (task.isSuccessful) {

                        val user = auth.currentUser
                        if (user != null) {
                            progressBar.visibility = View.VISIBLE
                            CoroutineScope(Dispatchers.IO).launch {
                                val groupId = GroupRepository.groupNameToDocumentId(group.trim())
                                val allowed = adminRepository.canRegisterToGroup(role, groupId)
                                withContext(Dispatchers.Main) {
                                    progressBar.visibility = View.GONE
                                    if (!allowed) {
                                        user.delete()
                                            .addOnCompleteListener {
                                                auth.signOut()
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Лимит для группы превышен. Регистрация отменена.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                    } else {
                                        saveUserToFirestore(user.uid, email, fullName, role, gender, group) {

                                            startRealtimePushForCurrentUser()
                                            Toast.makeText(requireContext(), "Регистрация успешна!", Toast.LENGTH_SHORT).show()

                                            parentFragmentManager.popBackStack()
                                        }
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "Ошибка: пользователь не создан", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val exception = task.exception
                        val errorMessage = when {
                            exception?.message?.contains("email address is already") == true ->
                                "Этот email уже зарегистрирован"
                            exception?.message?.contains("password") == true ->
                                "Пароль слишком слабый"
                            exception?.message?.contains("network") == true ->
                                "Проверьте подключение к интернету"
                            else -> exception?.message ?: "Неизвестная ошибка"
                        }
                        Toast.makeText(requireContext(), "Ошибка: $errorMessage", Toast.LENGTH_LONG).show()
                        android.util.Log.e("RegisterFragment", "Ошибка регистрации: ${exception?.message}", exception)
                    }
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            android.util.Log.e("RegisterFragment", "Исключение в registerUser: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveUserToFirestore(
        userId: String,
        email: String,
        fullName: String,
        role: String,
        gender: String,
        group: String,
        onComplete: () -> Unit
    ) {
        try {
            val trimmedGroup = group.trim()
            val groupId = if (trimmedGroup.isBlank()) {
                ""
            } else {
                GroupRepository.groupNameToDocumentId(trimmedGroup)
            }
            val groupName = if (trimmedGroup.isBlank()) "" else trimmedGroup

            val user = User(
                id = userId,
                email = email,
                fullName = fullName,
                role = role,
                gender = gender,
                group = groupName,
                groupId = groupId,
                groupName = groupName
            )

            db.collection("users").document(userId).set(user.toMap())
                .addOnSuccessListener {
                    android.util.Log.d("RegisterFragment", "Пользователь успешно сохранён в Firestore")
                    onComplete()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("RegisterFragment", "Ошибка сохранения в Firestore: ${e.message}", e)
                    Toast.makeText(requireContext(), "Ошибка сохранения данных: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("RegisterFragment", "Исключение в saveUserToFirestore: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка создания пользователя: ${e.message}", Toast.LENGTH_LONG).show()
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
