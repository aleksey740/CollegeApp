package com.sakovich.collegeapp.presentation.headman

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R

class HeadmanProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_headman_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        val currentUser = auth.currentUser

        val welcomeText = view.findViewById<TextView>(R.id.welcomeText)
        val userEmailText = view.findViewById<TextView>(R.id.userEmailText)
        val userRoleText = view.findViewById<TextView>(R.id.userRoleText)
        val attendanceBtn = view.findViewById<Button>(R.id.attendanceBtn)
        val scheduleBtn = view.findViewById<Button>(R.id.scheduleBtn)
        val statisticsBtn = view.findViewById<Button>(R.id.statisticsBtn)

        welcomeText.text = "Кабинет старосты"
        userEmailText.text = "Email: ${currentUser?.email ?: "Неизвестный"}"
        userRoleText.text = "Роль: Староста"

        // Обработчики кнопок
        attendanceBtn.setOnClickListener {
            Toast.makeText(requireContext(), "Система посещаемости скоро будет доступна", Toast.LENGTH_SHORT).show()
        }

        scheduleBtn.setOnClickListener {
            Toast.makeText(requireContext(), "Управление расписанием скоро будет доступно", Toast.LENGTH_SHORT).show()
        }

        statisticsBtn.setOnClickListener {
            Toast.makeText(requireContext(), "Статистика скоро будет доступна", Toast.LENGTH_SHORT).show()
        }
    }
}