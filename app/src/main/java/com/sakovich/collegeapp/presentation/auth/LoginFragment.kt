package com.sakovich.collegeapp.presentation.auth

import android.os.Bundle
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

        // Инициализируем Firebase Auth
        auth = Firebase.auth

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

        // Обработчик перехода к регистрации
        registerLink.setOnClickListener {
            // Переход к экрану регистрации
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loginUser(email: String, password: String) {
        val loginButton = view?.findViewById<MaterialButton>(R.id.loginButton)
        loginButton?.text = "Вход..."
        loginButton?.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Вход успешен - переходим на главный экран
                    Toast.makeText(requireContext(), "Вход успешен!", Toast.LENGTH_SHORT).show()

                    // Заменяем LoginFragment на MainFragment
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, MainFragment())
                        .commit()

                } else {
                    // Ошибка входа
                    Toast.makeText(requireContext(), "Ошибка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    loginButton?.text = "Войти"
                    loginButton?.isEnabled = true
                }
            }
    }
}