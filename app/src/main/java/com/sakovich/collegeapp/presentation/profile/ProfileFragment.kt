package com.sakovich.collegeapp.presentation.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.presentation.legal.PrivacyPolicyFragment
import com.sakovich.collegeapp.presentation.clubs.ClubReminderScheduler
import com.sakovich.collegeapp.data.repositories.AbsenceRepository
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.DrawableUtils
import com.sakovich.collegeapp.utils.SemesterStatsHelper
import com.sakovich.collegeapp.notifications.NotificationRealtimeManager
import com.sakovich.collegeapp.presentation.nutrition.MealReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var gradeRepository: GradeRepository
    private lateinit var absenceRepository: AbsenceRepository
    private lateinit var adminRepository: AdminRepository

    private lateinit var avatarText: TextView
    private lateinit var userNameText: TextView
    private lateinit var userEmailText: TextView
    private lateinit var userRoleText: TextView
    private lateinit var userGroupText: TextView
    private lateinit var profileBackButton: ImageButton

    private lateinit var statisticsContainer: LinearLayout
    private lateinit var avgGradeValue: TextView
    private lateinit var gradesCountValue: TextView
    private lateinit var absencesCountValue: TextView

    private lateinit var personalInfoCard: View
    private lateinit var addressText: TextView
    private lateinit var birthDateText: TextView
    private lateinit var phoneText: TextView

    private lateinit var editPersonalInfoButton: View
    private lateinit var divider1: View
    private lateinit var changePasswordButton: View
    private lateinit var privacyPolicyButton: View
    private lateinit var logoutButton: View

    private var currentUser: User? = null

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
        gradeRepository = GradeRepository()
        absenceRepository = AbsenceRepository()
        adminRepository = AdminRepository()

        initViews(view)
        setupClickListeners()

        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            userEmailText.text = firebaseUser.email ?: "Неизвестно"
            loadUserData(firebaseUser.uid)
        } else {
            showDefaultData()
        }
    }

    override fun onResume() {
        super.onResume()
        currentUser?.let { user ->
            if (user.role == "student" || user.role == "headman") {
                loadStatistics(user)
            }
            loadPersonalInfo(user)
        }
    }

    private fun initViews(view: View) {

        avatarText = view.findViewById(R.id.avatarText)
        userNameText = view.findViewById(R.id.userNameText)
        userEmailText = view.findViewById(R.id.userEmailText)
        userRoleText = view.findViewById(R.id.userRoleText)
        userGroupText = view.findViewById(R.id.userGroupText)
        profileBackButton = view.findViewById(R.id.profileBackButton)

        statisticsContainer = view.findViewById(R.id.statisticsContainer)
        avgGradeValue = view.findViewById(R.id.avgGradeValue)
        gradesCountValue = view.findViewById(R.id.gradesCountValue)
        absencesCountValue = view.findViewById(R.id.absencesCountValue)

        personalInfoCard = view.findViewById(R.id.personalInfoCard)
        addressText = view.findViewById(R.id.addressText)
        birthDateText = view.findViewById(R.id.birthDateText)
        phoneText = view.findViewById(R.id.phoneText)

        editPersonalInfoButton = view.findViewById(R.id.editPersonalInfoButton)
        divider1 = view.findViewById(R.id.divider1)
        changePasswordButton = view.findViewById(R.id.changePasswordButton)
        privacyPolicyButton = view.findViewById(R.id.privacyPolicyButton)
        logoutButton = view.findViewById(R.id.logoutButton)

        statisticsContainer.visibility = View.GONE
        personalInfoCard.visibility = View.GONE
        editPersonalInfoButton.visibility = View.GONE
        divider1.visibility = View.GONE
    }

    private fun setupClickListeners() {
        profileBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        editPersonalInfoButton.setOnClickListener {
            openEditPersonalInfoFragment()
        }

        changePasswordButton.setOnClickListener {
            changePassword()
        }

        privacyPolicyButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PrivacyPolicyFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }

        logoutButton.setOnClickListener {
            logoutUser()
        }
    }

    private fun loadUserData(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Загрузка данных пользователя: $userId")
                val user = userRepository.getUser(userId)

                withContext(Dispatchers.Main) {
                    if (user != null) {
                        currentUser = user
                        displayUserData(user)

                        if (user.role == "student" || user.role == "headman") {
                            statisticsContainer.visibility = View.VISIBLE
                            editPersonalInfoButton.visibility = View.VISIBLE
                            divider1.visibility = View.VISIBLE
                            loadStatistics(user)
                            loadPersonalInfo(user)
                        }
                    } else {
                        showDefaultData()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showDefaultData()
                    Toast.makeText(requireContext(), "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayUserData(user: User) {

        val displayName = user.fullName.ifEmpty { user.email.substringBefore("@") }
        userNameText.text = displayName

        val initial = displayName.firstOrNull()?.uppercase() ?: "?"
        avatarText.text = initial

        val avatarColor = when (user.role) {
            "teacher" -> "#8B5CF6"
            "headman" -> "#8B5CF6"
            "admin" -> "#F59E0B"
            else -> "#10B981"
        }
        DrawableUtils.setViewBackgroundColor(avatarText, Color.parseColor(avatarColor))

        userRoleText.text = user.roleBadgeLabel()
        DrawableUtils.setViewBackgroundColor(userRoleText, Color.parseColor(user.roleBadgeColorHex()))

        if (user.role != "teacher" && user.role != "admin" && user.groupName.isNotEmpty()) {
            userGroupText.text = user.groupName
            userGroupText.visibility = View.VISIBLE
        } else {
            userGroupText.visibility = View.GONE
        }
    }

    private fun loadStatistics(user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allGrades = gradeRepository.getStudentGrades(user.id)
                val visibleGrades = allGrades.filter { SemesterStatsHelper.isVisibleGrade(it) }
                val grades = SemesterStatsHelper.filterGradesForCurrentSemester(visibleGrades, user, adminRepository)
                val totalGrades = grades.size
                val forAvg = grades.filter { !it.isAbsence() }
                val avgGrade = if (forAvg.isNotEmpty()) {
                    forAvg.map { it.value.toDouble() }.average()
                } else 0.0

                val allAbsences = absenceRepository.getStudentAbsences(user.id)
                val absences = SemesterStatsHelper.filterAbsencesForCurrentSemester(allAbsences, user, adminRepository)
                val totalAbsenceHours = absences.sumOf { it.hours }

                withContext(Dispatchers.Main) {
                    gradesCountValue.text = totalGrades.toString()
                    avgGradeValue.text = if (totalGrades > 0) "%.1f".format(avgGrade) else "—"
                    absencesCountValue.text = "$totalAbsenceHours ч."

                    val gradeColor = when {
                        avgGrade >= 9 -> "#10B981"
                        avgGrade >= 7 -> "#A78BFA"
                        avgGrade >= 5 -> "#F59E0B"
                        avgGrade > 0 -> "#EF4444"
                        else -> "#94A3B8"
                    }
                    avgGradeValue.setTextColor(Color.parseColor(gradeColor))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки статистики: ${e.message}")
            }
        }
    }

    private fun loadPersonalInfo(user: User) {
        if (user.address.isNotEmpty() || user.birthDate.isNotEmpty() || user.phone.isNotEmpty()) {
            personalInfoCard.visibility = View.VISIBLE
            addressText.text = user.address.ifEmpty { "Не указано" }
            birthDateText.text = user.birthDate.ifEmpty { "Не указано" }
            phoneText.text = user.phone.ifEmpty { "Не указано" }
        } else {
            personalInfoCard.visibility = View.GONE
        }
    }

    private fun showDefaultData() {
        val email = auth.currentUser?.email ?: "Гость"
        userNameText.text = email.substringBefore("@")
        avatarText.text = email.firstOrNull()?.uppercase() ?: "?"
        userRoleText.text = "🎓 Учащийся"
        userGroupText.visibility = View.GONE
        statisticsContainer.visibility = View.GONE
        personalInfoCard.visibility = View.GONE
        editPersonalInfoButton.visibility = View.GONE
        divider1.visibility = View.GONE
    }

    private fun openEditPersonalInfoFragment() {
        val editFragment = EditPersonalInfoFragment.newInstance()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun changePassword() {
        val email = auth.currentUser?.email
        if (email != null) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            requireContext(),
                            "Письмо для смены пароля отправлено на $email",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Ошибка: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        } else {
            Toast.makeText(requireContext(), "Email не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutUser() {

        MealReminderScheduler.cancelAll(requireContext())
        ClubReminderScheduler.cancelAll(requireContext())
        NotificationRealtimeManager.stop()
        auth.signOut()
        requireActivity().finish()
        startActivity(Intent(requireContext(), requireActivity()::class.java))
    }
}
