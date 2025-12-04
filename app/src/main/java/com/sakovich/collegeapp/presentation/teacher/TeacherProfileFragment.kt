package com.sakovich.collegeapp.presentation.teacher

import android.os.Bundle
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

class TeacherProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        val currentUser = auth.currentUser

        val welcomeText = view.findViewById<TextView>(R.id.welcomeText)
        val userEmailText = view.findViewById<TextView>(R.id.userEmailText)
        val userRoleText = view.findViewById<TextView>(R.id.userRoleText)
        val addGradesBtn = view.findViewById<Button>(R.id.addGradesBtn)
        val viewGroupsBtn = view.findViewById<Button>(R.id.viewGroupsBtn)
        val statisticsBtn = view.findViewById<Button>(R.id.statisticsBtn)
        val quickScheduleBtn = view.findViewById<Button>(R.id.quickScheduleBtn)
        val quickStudentsBtn = view.findViewById<Button>(R.id.quickStudentsBtn)

        welcomeText.text = "Кабинет преподавателя"
        userEmailText.text = "Email: ${currentUser?.email ?: "Неизвестный"}"
        userRoleText.text = "Роль: Преподаватель"

        // Обработчики кнопок
        addGradesBtn.setOnClickListener {
            // Переход к выбору группы для выставления оценок
            val teacherGroupsFragment = TeacherGroupsFragment.newInstance()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, teacherGroupsFragment)
                .addToBackStack(null)
                .commit()
        }

        viewGroupsBtn.setOnClickListener {
            // Переход к просмотру групп
            val teacherGroupsFragment = TeacherGroupsFragment.newInstance()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, teacherGroupsFragment)
                .addToBackStack(null)
                .commit()
        }

        statisticsBtn.setOnClickListener {
            // TODO: Переход к статистике
            // Показываем сообщение, что функция в разработке
            android.widget.Toast.makeText(
                requireContext(),
                "Статистика будет доступна в следующем обновлении",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        quickScheduleBtn.setOnClickListener {
            // TODO: Переход к расписанию преподавателя
            android.widget.Toast.makeText(
                requireContext(),
                "Расписание преподавателя будет доступно в следующем обновлении",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        quickStudentsBtn.setOnClickListener {
            // Переход к выбору группы (быстрый доступ к студентам)
            val teacherGroupsFragment = TeacherGroupsFragment.newInstance()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, teacherGroupsFragment)
                .addToBackStack(null)
                .commit()
        }
    }
}