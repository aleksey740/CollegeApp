package com.sakovich.collegeapp.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth

        val emailEditText = view.findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = view.findViewById<EditText>(R.id.passwordEditText)
        val fullNameEditText = view.findViewById<EditText>(R.id.fullNameEditText)
        val groupEditText = view.findViewById<EditText>(R.id.groupEditText)
        val roleSpinner = view.findViewById<Spinner>(R.id.roleSpinner)
        val registerButton = view.findViewById<Button>(R.id.registerButton)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val loginLink = view.findViewById<TextView>(R.id.loginLink)

        // Настройка Spinner для ролей
        val roles = arrayOf("Учащийся", "Староста", "Преподаватель")
        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roles)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = roleAdapter

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val fullName = fullNameEditText.text.toString()
            val group = groupEditText.text.toString()
            val role = when (roleSpinner.selectedItem as String) {
                "Староста" -> "headman"
                "Преподаватель" -> "teacher"
                else -> "student"
            }

            if (email.isNotEmpty() && password.isNotEmpty() && fullName.isNotEmpty()) {
                registerUser(email, password, fullName, group, role, progressBar)
            } else {
                Toast.makeText(requireContext(), "Заполните все поля", Toast.LENGTH_SHORT).show()
            }
        }

        loginLink.setOnClickListener {
            // Возврат к экрану входа
            parentFragmentManager.popBackStack()
        }
    }

    private fun registerUser(
        email: String,
        password: String,
        fullName: String,
        group: String,
        role: String,
        progressBar: ProgressBar
    ) {
        progressBar.visibility = View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Регистрация успешна - сохраняем данные пользователя в Firestore
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserToFirestore(user.uid, email, fullName, role, group)
                    }
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Регистрация успешна!", Toast.LENGTH_SHORT).show()

                    // Возвращаемся к экрану входа
                    parentFragmentManager.popBackStack()
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Ошибка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserToFirestore(
        userId: String,
        email: String,
        fullName: String,
        role: String,
        group: String
    ) {
        val user = User(
            id = userId,
            email = email,
            fullName = fullName,
            role = role,
            groupId = group,
            groupName = group
        )

        db.collection("users").document(userId).set(user)
    }
}