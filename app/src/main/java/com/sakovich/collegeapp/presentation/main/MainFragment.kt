package com.sakovich.collegeapp.presentation.main

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.AbsenceRepository
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.presentation.absences.MyAbsencesFragment
import com.sakovich.collegeapp.presentation.admin.AdminPanelFragment
import com.sakovich.collegeapp.presentation.clubs.ClubsFragment
import com.sakovich.collegeapp.presentation.events.GroupEventsFragment
import com.sakovich.collegeapp.presentation.grades.GradesFragment
import com.sakovich.collegeapp.presentation.headman.HeadmanProfileFragment
import com.sakovich.collegeapp.presentation.chat.ChatFragment
import com.sakovich.collegeapp.presentation.nutrition.NutritionFragment
import com.sakovich.collegeapp.presentation.notifications.NotificationsFragment
import com.sakovich.collegeapp.presentation.profile.ProfileFragment
import com.sakovich.collegeapp.presentation.schedule.ScheduleFragment
import com.sakovich.collegeapp.presentation.teacher.TeacherProfileFragment
import com.sakovich.collegeapp.utils.DrawableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var gradeRepository: GradeRepository
    private lateinit var adminRepository: AdminRepository
    private lateinit var absenceRepository: AbsenceRepository
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var greetingText: TextView
    private lateinit var userNameText: TextView
    private lateinit var roleBadge: TextView
    private lateinit var groupBadge: TextView
    private lateinit var dateText: TextView

    private lateinit var quickStatsLayout: LinearLayout
    private lateinit var avgGradeValue: TextView
    private lateinit var gradesCountValue: TextView
    private lateinit var absencesCountValue: TextView

    private lateinit var gradesCard: MaterialCardView
    private lateinit var absencesCard: MaterialCardView
    private lateinit var scheduleCard: MaterialCardView
    private lateinit var eventsCard: MaterialCardView
    private lateinit var notificationsCard: MaterialCardView
    private lateinit var chatCard: MaterialCardView
    private lateinit var notificationsBadge: TextView
    private lateinit var notificationsSubtitle: TextView
    private lateinit var nutritionCard: MaterialCardView
    private lateinit var clubsCard: MaterialCardView
    private lateinit var teacherCard: MaterialCardView
    private lateinit var headmanCard: MaterialCardView
    private lateinit var adminCard: MaterialCardView
    private lateinit var profileCard: MaterialCardView

    private var currentUser: User? = null

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
        gradeRepository = GradeRepository()
        adminRepository = AdminRepository()
        absenceRepository = AbsenceRepository()
        notificationRepository = NotificationRepository()

        initViews(view)
        setupDate()
        setupClickListeners()

        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            loadUserData(firebaseUser.uid)
        } else {
            showDefaultView()
        }
    }

    override fun onResume() {
        super.onResume()

        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            loadUnreadNotificationsCount(firebaseUser.uid)
        }

        currentUser?.let { user ->
            if (user.role == "student" || user.role == "headman") {
                loadQuickStats(user)
            }
        }
    }

    private fun initViews(view: View) {

        greetingText = view.findViewById(R.id.greetingText)
        userNameText = view.findViewById(R.id.userNameText)
        roleBadge = view.findViewById(R.id.roleBadge)
        groupBadge = view.findViewById(R.id.groupBadge)
        dateText = view.findViewById(R.id.dateText)

        quickStatsLayout = view.findViewById(R.id.quickStatsLayout)
        avgGradeValue = view.findViewById(R.id.avgGradeValue)
        gradesCountValue = view.findViewById(R.id.gradesCountValue)
        absencesCountValue = view.findViewById(R.id.absencesCountValue)

        gradesCard = view.findViewById(R.id.gradesCard)
        absencesCard = view.findViewById(R.id.absencesCard)
        scheduleCard = view.findViewById(R.id.scheduleCard)
        eventsCard = view.findViewById(R.id.eventsCard)
        notificationsCard = view.findViewById(R.id.notificationsCard)
        chatCard = view.findViewById(R.id.chatCard)
        notificationsBadge = view.findViewById(R.id.notificationsBadge)
        notificationsSubtitle = view.findViewById(R.id.notificationsSubtitle)
        nutritionCard = view.findViewById(R.id.nutritionCard)
        clubsCard = view.findViewById(R.id.clubsCard)
        teacherCard = view.findViewById(R.id.teacherCard)
        headmanCard = view.findViewById(R.id.headmanCard)
        adminCard = view.findViewById(R.id.adminCard)
        profileCard = view.findViewById(R.id.profileCard)
    }

    private fun setupDate() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val greeting = when {
            hour in 5..11 -> "Доброе утро! ☀️"
            hour in 12..17 -> "Добрый день! 👋"
            hour in 18..22 -> "Добрый вечер! 🌙"
            else -> "Доброй ночи! 🌟"
        }
        greetingText.text = greeting

        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru"))
        val formattedDate = dateFormat.format(calendar.time)
        dateText.text = "📅 ${formattedDate.replaceFirstChar { it.uppercase() }}"
    }

    private fun loadUserData(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = userRepository.getUser(userId)

                withContext(Dispatchers.Main) {
                    if (user != null) {
                        currentUser = user
                        showUserSpecificView(user)
                    } else {
                        showDefaultView()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showDefaultView()
                    Toast.makeText(requireContext(), "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUserSpecificView(user: User) {

        val displayName = user.fullName.ifEmpty { user.email.substringBefore("@") }
        userNameText.text = displayName

        roleBadge.text = user.roleBadgeLabel()
        DrawableUtils.setViewBackgroundColor(roleBadge, Color.parseColor(user.roleBadgeColorHex()))

        if (user.role != "teacher" && user.role != "admin" && user.groupName.isNotEmpty()) {
            groupBadge.text = user.groupName
            groupBadge.visibility = View.VISIBLE
        } else {
            groupBadge.visibility = View.GONE
        }

        when (user.role) {
            "teacher" -> {
                gradesCard.visibility = View.GONE
                absencesCard.visibility = View.GONE
                clubsCard.visibility = View.GONE
                scheduleCard.visibility = View.GONE
                eventsCard.visibility = View.GONE
                nutritionCard.visibility = View.GONE
                teacherCard.visibility = View.VISIBLE
                headmanCard.visibility = View.GONE
                adminCard.visibility = View.VISIBLE
                quickStatsLayout.visibility = View.GONE
            }
            "headman" -> {
                gradesCard.visibility = View.VISIBLE
                absencesCard.visibility = View.VISIBLE
                clubsCard.visibility = View.VISIBLE
                scheduleCard.visibility = View.VISIBLE
                eventsCard.visibility = View.VISIBLE
                nutritionCard.visibility = View.VISIBLE
                teacherCard.visibility = View.GONE
                headmanCard.visibility = View.VISIBLE
                adminCard.visibility = View.GONE
                quickStatsLayout.visibility = View.VISIBLE
                loadQuickStats(user)
            }
            "admin" -> {

                gradesCard.visibility = View.GONE
                absencesCard.visibility = View.GONE
                clubsCard.visibility = View.GONE
                scheduleCard.visibility = View.GONE
                eventsCard.visibility = View.GONE
                nutritionCard.visibility = View.GONE
                teacherCard.visibility = View.VISIBLE
                headmanCard.visibility = View.GONE
                adminCard.visibility = View.VISIBLE
                quickStatsLayout.visibility = View.GONE
            }
            else -> {
                gradesCard.visibility = View.VISIBLE
                absencesCard.visibility = View.VISIBLE
                clubsCard.visibility = View.VISIBLE
                scheduleCard.visibility = View.VISIBLE
                eventsCard.visibility = View.VISIBLE
                nutritionCard.visibility = View.VISIBLE
                teacherCard.visibility = View.GONE
                headmanCard.visibility = View.GONE
                adminCard.visibility = View.GONE
                quickStatsLayout.visibility = View.VISIBLE
                loadQuickStats(user)
            }
        }

        notificationsCard.visibility = View.VISIBLE
        chatCard.visibility = View.VISIBLE
        profileCard.visibility = View.VISIBLE

        loadUnreadNotificationsCount(user.id)
    }

    private fun loadUnreadNotificationsCount(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = notificationRepository.getUnreadCount(userId)
                withContext(Dispatchers.Main) {
                    if (count > 0) {
                        notificationsBadge.text = if (count > 99) "99+" else count.toString()
                        notificationsBadge.visibility = View.VISIBLE
                        notificationsSubtitle.text = "Непрочитанных: $count"
                    } else {
                        notificationsBadge.visibility = View.GONE
                        notificationsSubtitle.text = "Нет новых уведомлений"
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun loadQuickStats(user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentSemesterNumber = resolveCurrentSemesterNumber(user)
                val currentSemesterRange = resolveCurrentSemesterDateRange(user)

                val allGrades = gradeRepository.getStudentGrades(user.id)
                val visibleGrades = allGrades.filter { isVisibleGrade(it) }
                val grades = if (currentSemesterRange != null) {
                    val (start, end) = currentSemesterRange
                    val filteredByDate = visibleGrades.filter { isDateInRange(it.date, start, end) }
                    when {
                        filteredByDate.isNotEmpty() -> filteredByDate
                        currentSemesterNumber != null -> visibleGrades.filter { it.semester == currentSemesterNumber }
                        else -> visibleGrades
                    }
                } else {
                    if (currentSemesterNumber != null) {
                        visibleGrades.filter { it.semester == currentSemesterNumber }
                    } else visibleGrades
                }
                val totalGrades = grades.size
                val forAvg = grades.filter { !it.isAbsence() }
                val avgGrade = if (forAvg.isNotEmpty()) {
                    forAvg.map { it.value.toDouble() }.average()
                } else 0.0

                val absences = absenceRepository.getStudentAbsences(user.id)
                val totalAbsenceHours = if (currentSemesterRange != null) {
                    val (start, end) = currentSemesterRange
                    absences.filter { isDateInRange(it.date, start, end) }.sumOf { it.hours }
                } else {
                    absences.sumOf { it.hours }
                }

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

            }
        }
    }

    private fun isVisibleGrade(grade: Grade): Boolean {
        if (grade.isAbsence()) return false
        val typeRaw = grade.type.trim().lowercase(Locale.getDefault())
        if (typeRaw.contains("неяв")) return false
        if (typeRaw == "н") return false
        return grade.value in 1..10
    }

    private suspend fun resolveCurrentSemesterNumber(user: User): Int? {
        val groupId = user.groupId.ifBlank { user.groupName.ifBlank { user.group } }
        if (groupId.isBlank()) return null

        val semesters = adminRepository.getSemestersForGroup(groupId)
        if (semesters.isEmpty()) return null
        val today = Calendar.getInstance().time

        val currentTemplate = semesters.firstOrNull { sem ->
            val start = parseDate(sem.startDate) ?: return@firstOrNull false
            val end = parseDate(sem.endDate) ?: return@firstOrNull false
            !today.before(start) && !today.after(end)
        } ?: return null

        return Regex("(\\d+)").find(currentTemplate.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private suspend fun resolveCurrentSemesterDateRange(user: User): Pair<Date, Date>? {
        val groupId = user.groupId.ifBlank { user.groupName.ifBlank { user.group } }
        if (groupId.isBlank()) return null

        val semesters = adminRepository.getSemestersForGroup(groupId)
        if (semesters.isEmpty()) return null
        val today = Calendar.getInstance().time

        val currentTemplate = semesters.firstOrNull { sem ->
            val start = parseDate(sem.startDate) ?: return@firstOrNull false
            val end = parseDate(sem.endDate) ?: return@firstOrNull false
            !today.before(start) && !today.after(end)
        } ?: return null

        val start = parseDate(currentTemplate.startDate) ?: return null
        val end = parseDate(currentTemplate.endDate) ?: return null
        return start to end
    }

    private fun isDateInRange(dateString: String, start: Date, end: Date): Boolean {
        val date = parseDate(dateString) ?: return false
        return !date.before(start) && !date.after(end)
    }

    private fun parseDate(value: String): Date? {
        if (value.isBlank()) return null
        return try {

            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(value)
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun showDefaultView() {
        userNameText.text = "Гость"
        roleBadge.text = "🎓 Учащийся"
        groupBadge.visibility = View.GONE
        quickStatsLayout.visibility = View.GONE

        gradesCard.visibility = View.VISIBLE
        absencesCard.visibility = View.VISIBLE
        teacherCard.visibility = View.GONE
        headmanCard.visibility = View.GONE
        adminCard.visibility = View.GONE
        scheduleCard.visibility = View.VISIBLE
        nutritionCard.visibility = View.VISIBLE
        clubsCard.visibility = View.VISIBLE
        eventsCard.visibility = View.VISIBLE
        chatCard.visibility = View.VISIBLE
        profileCard.visibility = View.VISIBLE
    }

    private fun setupClickListeners() {
        gradesCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GradesFragment())
                .addToBackStack(null)
                .commit()
        }

        absencesCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MyAbsencesFragment())
                .addToBackStack(null)
                .commit()
        }

        scheduleCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScheduleFragment())
                .addToBackStack(null)
                .commit()
        }

        eventsCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GroupEventsFragment())
                .addToBackStack(null)
                .commit()
        }

        notificationsCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }

        chatCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChatFragment())
                .addToBackStack(null)
                .commit()
        }

        nutritionCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NutritionFragment())
                .addToBackStack(null)
                .commit()
        }

        clubsCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ClubsFragment())
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

        adminCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminPanelFragment())
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
