package com.sakovich.collegeapp.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.presentation.calendar.CalendarFragment
import com.sakovich.collegeapp.presentation.grades.GradesFragment
import com.sakovich.collegeapp.presentation.headman.HeadmanProfileFragment
import com.sakovich.collegeapp.presentation.profile.ProfileFragment
import com.sakovich.collegeapp.presentation.schedule.ScheduleFragment
import com.sakovich.collegeapp.presentation.teacher.TeacherProfileFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()

        val welcomeText = view.findViewById<TextView>(R.id.welcomeText)
        val userEmailText = view.findViewById<TextView>(R.id.userEmailText)
        val gradesCard = view.findViewById<MaterialCardView>(R.id.gradesCard)
        val scheduleCard = view.findViewById<MaterialCardView>(R.id.scheduleCard)
        val calendarCard = view.findViewById<MaterialCardView>(R.id.calendarCard) // 👈 НОВАЯ КАРТОЧКА
        val teacherCard = view.findViewById<MaterialCardView>(R.id.teacherCard)
        val headmanCard = view.findViewById<MaterialCardView>(R.id.headmanCard)
        val profileCard = view.findViewById<MaterialCardView>(R.id.profileCard)

        val currentUser = auth.currentUser

        // Временно показываем загрузку
        welcomeText.text = "Загрузка..."
        userEmailText.text = "Вы вошли как: ${currentUser?.email ?: "Неизвестный пользователь"}"

        // Загружаем данные пользователя из Firestore
        if (currentUser != null) {
            loadUserData(currentUser.uid, welcomeText, gradesCard, scheduleCard, calendarCard, teacherCard, headmanCard, profileCard)
        } else {
            showDefaultView(welcomeText, gradesCard, scheduleCard, calendarCard, teacherCard, headmanCard, profileCard)
        }

        // Обработчики кликов по карточкам
        setupClickListeners(gradesCard, scheduleCard, calendarCard, teacherCard, headmanCard, profileCard)
    }

    private fun loadUserData(
        userId: String,
        welcomeText: TextView,
        gradesCard: MaterialCardView,
        scheduleCard: MaterialCardView,
        calendarCard: MaterialCardView, // 👈 ДОБАВЛЕНО
        teacherCard: MaterialCardView,
        headmanCard: MaterialCardView,
        profileCard: MaterialCardView
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = userRepository.getUser(userId)

                requireActivity().runOnUiThread {
                    if (user != null) {
                        showUserSpecificView(user, welcomeText, gradesCard, scheduleCard, calendarCard, teacherCard, headmanCard, profileCard)
                    } else {
                        showDefaultView(welcomeText, gradesCard, scheduleCard, calendarCard, teacherCard, headmanCard, profileCard)
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    showDefaultView(welcomeText, gradesCard, scheduleCard, calendarCard, teacherCard, headmanCard, profileCard)
                    Toast.makeText(requireContext(), "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUserSpecificView(
        user: com.sakovich.collegeapp.data.models.User,
        welcomeText: TextView,
        gradesCard: MaterialCardView,
        scheduleCard: MaterialCardView,
        calendarCard: MaterialCardView, // 👈 ДОБАВЛЕНО
        teacherCard: MaterialCardView,
        headmanCard: MaterialCardView,
        profileCard: MaterialCardView
    ) {
        when (user.role) {
            "teacher" -> {
                welcomeText.text = "Кабинет преподавателя"
                gradesCard.visibility = View.GONE
                teacherCard.visibility = View.VISIBLE
                headmanCard.visibility = View.GONE
                scheduleCard.visibility = View.VISIBLE
                calendarCard.visibility = View.VISIBLE
                profileCard.visibility = View.VISIBLE
            }
            "headman" -> {
                welcomeText.text = "Кабинет старосты"
                gradesCard.visibility = View.VISIBLE
                teacherCard.visibility = View.GONE
                headmanCard.visibility = View.VISIBLE
                scheduleCard.visibility = View.VISIBLE
                calendarCard.visibility = View.VISIBLE
                profileCard.visibility = View.VISIBLE
            }
            else -> { // student
                welcomeText.text = "Добро пожаловать в CollegeApp!"
                gradesCard.visibility = View.VISIBLE
                teacherCard.visibility = View.GONE
                headmanCard.visibility = View.GONE
                scheduleCard.visibility = View.VISIBLE
                calendarCard.visibility = View.VISIBLE
                profileCard.visibility = View.VISIBLE
            }
        }
    }

    private fun showDefaultView(
        welcomeText: TextView,
        gradesCard: MaterialCardView,
        scheduleCard: MaterialCardView,
        calendarCard: MaterialCardView, // 👈 ДОБАВЛЕНО
        teacherCard: MaterialCardView,
        headmanCard: MaterialCardView,
        profileCard: MaterialCardView
    ) {
        welcomeText.text = "Добро пожаловать в CollegeApp!"
        gradesCard.visibility = View.VISIBLE
        teacherCard.visibility = View.GONE
        headmanCard.visibility = View.GONE
        scheduleCard.visibility = View.VISIBLE
        calendarCard.visibility = View.VISIBLE
        profileCard.visibility = View.VISIBLE
    }

    private fun setupClickListeners(
        gradesCard: MaterialCardView,
        scheduleCard: MaterialCardView,
        calendarCard: MaterialCardView, // 👈 ДОБАВЛЕНО
        teacherCard: MaterialCardView,
        headmanCard: MaterialCardView,
        profileCard: MaterialCardView
    ) {
        gradesCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GradesFragment())
                .addToBackStack(null)
                .commit()
        }

        scheduleCard.setOnClickListener {
            // 👇 ТЕПЕРЬ ОТКРЫВАЕТ ScheduleFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScheduleFragment())
                .addToBackStack(null)
                .commit()
        }

        calendarCard.setOnClickListener {
            // 👇 ОТКРЫВАЕТ CalendarFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CalendarFragment())
                .addToBackStack(null)
                .commit()
        }

        teacherCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TeacherProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        headmanCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HeadmanProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        profileCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}