package com.sakovich.collegeapp.presentation.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository

    companion object {
        private const val TAG = "ProfileFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()

        val currentUser = auth.currentUser

        val userNameText = view.findViewById<TextView>(R.id.userNameText)
        val userEmailText = view.findViewById<TextView>(R.id.userEmailText)
        val userRoleText = view.findViewById<TextView>(R.id.userRoleText)
        val userGroupText = view.findViewById<TextView>(R.id.userGroupText) // Новое поле
        val logoutButton = view.findViewById<Button>(R.id.logoutButton)
        val changePasswordButton = view.findViewById<Button>(R.id.changePasswordButton)

        // Временно показываем базовые данные
        userEmailText.text = "Email: ${currentUser?.email ?: "Неизвестный"}"
        userNameText.text = "Имя: Загрузка..."
        userRoleText.text = "Роль: Загрузка..."
        userGroupText.text = "Группа: Загрузка..."

        // Загружаем полные данные из Firestore
        if (currentUser != null) {
            loadUserDataFromFirestore(currentUser.uid, userNameText, userRoleText, userGroupText)
        } else {
            showDefaultData(userNameText, userRoleText, userGroupText)
        }

        // Обработчики кнопок
        logoutButton.setOnClickListener {
            logoutUser()
        }

        changePasswordButton.setOnClickListener {
            changePassword()
        }
    }

    private fun loadUserDataFromFirestore(
        userId: String,
        userNameText: TextView,
        userRoleText: TextView,
        userGroupText: TextView
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Загрузка данных пользователя из Firestore: $userId")
                val user = userRepository.getUser(userId)

                requireActivity().runOnUiThread {
                    if (user != null) {
                        Log.d(TAG, "Данные получены: fullName=${user.fullName}, role=${user.role}, groupName=${user.groupName}")
                        displayUserData(user, userNameText, userRoleText, userGroupText)
                    } else {
                        Log.e(TAG, "Пользователь не найден в Firestore")
                        showDefaultData(userNameText, userRoleText, userGroupText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки из Firestore: ${e.message}", e)
                requireActivity().runOnUiThread {
                    showDefaultData(userNameText, userRoleText, userGroupText)
                    // Показываем Toast с ошибкой
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Ошибка загрузки данных профиля",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun displayUserData(
        user: com.sakovich.collegeapp.data.models.User,
        userNameText: TextView,
        userRoleText: TextView,
        userGroupText: TextView
    ) {
        // Отображаем полное имя из Firestore
        val displayName = if (user.fullName.isNullOrEmpty()) "Не указано" else user.fullName
        userNameText.text = "Имя: $displayName"

        // Определяем роль из Firestore
        val roleText = when {
            user.teacher || user.role == "teacher" -> "Преподаватель"
            user.headman || user.role == "headman" -> "Староста"
            user.student || user.role == "student" -> "Студент"
            else -> "Роль не указана"
        }
        userRoleText.text = "Роль: $roleText"

        // Отображаем группу (для преподавателя показываем группу, которую он ведет)
        val groupDisplay = when {
            !user.groupName.isNullOrEmpty() -> user.groupName
            !user.groupId.isNullOrEmpty() -> user.groupId
            user.teacher || user.role == "teacher" -> "Преподаватель"
            else -> "Не назначена"
        }
        userGroupText.text = "Группа: $groupDisplay"
    }

    private fun showDefaultData(
        userNameText: TextView,
        userRoleText: TextView,
        userGroupText: TextView
    ) {
        val currentUser = auth.currentUser

        // Показываем данные только из Auth (запасной вариант)
        val displayName = currentUser?.displayName ?: "Не указано"
        userNameText.text = "Имя: $displayName"

        // Определяем роль по email (запасной вариант)
        val isTeacher = currentUser?.email?.contains("teacher", ignoreCase = true) == true
        userRoleText.text = "Роль: ${if (isTeacher) "Преподаватель" else "Студент"}"
        userGroupText.text = "Группа: Неизвестна"
    }

    private fun logoutUser() {
        auth.signOut()
        // Возвращаемся на экран входа
        requireActivity().finish()
        startActivity(Intent(requireContext(), requireActivity()::class.java))
    }

    private fun changePassword() {
        val email = auth.currentUser?.email
        if (email != null) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Письмо для смены пароля отправлено на $email",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Ошибка: ${task.exception?.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                "Email не найден",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}