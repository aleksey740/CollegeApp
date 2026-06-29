package com.sakovich.collegeapp.presentation.schedule

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.ScheduleRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.AppLog
import com.sakovich.collegeapp.utils.ContentOwnershipRules
import com.sakovich.collegeapp.utils.DrawableUtils
import com.sakovich.collegeapp.utils.ScheduleSortHelper
import com.sakovich.collegeapp.utils.ScheduleTypeLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment() {

    companion object {
        private const val TAG = "ScheduleFragment"
    }

    private lateinit var scheduleRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var fabAddSchedule: FloatingActionButton
    private lateinit var dateText: TextView
    private lateinit var prevDateBtn: com.google.android.material.button.MaterialButton
    private lateinit var nextDateBtn: com.google.android.material.button.MaterialButton
    private lateinit var scheduleBackButton: ImageButton

    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var userRepository: UserRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var scheduleAdapter: ScheduleAdapter

    private var scheduleList = mutableListOf<ScheduleItem>()
    private var currentDayFilter: String? = null
    private var currentGroupFilter: String? = null
    private var canEditSchedule = false
    private var currentUser: User? = null
    private val selectedDateCalendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var scheduleListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        scheduleRepository = ScheduleRepository()
        userRepository = UserRepository()

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()

        startRealtimeUpdates()
    }

    override fun onResume() {
        super.onResume()

        if (scheduleList.isNotEmpty()) {
            applyDayFilter()
        }
    }

    override fun onPause() {
        super.onPause()
        stopRealtimeUpdates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRealtimeUpdates()
    }

    private fun initViews(view: View) {
        scheduleRecyclerView = view.findViewById(R.id.scheduleRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        fabAddSchedule = view.findViewById(R.id.fabAddSchedule)
        dateText = view.findViewById(R.id.dateText)
        prevDateBtn = view.findViewById(R.id.prevDateBtn)
        nextDateBtn = view.findViewById(R.id.nextDateBtn)
        scheduleBackButton = view.findViewById(R.id.scheduleBackButton)

        normalizeSelectedDateToStudyDay()
        syncDayFilterWithSelectedDate()

        scheduleBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadCurrentUser() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val user = userRepository.getUser(firebaseUser.uid)
                    requireActivity().runOnUiThread {
                        currentUser = user
                        canEditSchedule = user?.canEditEvents() ?: false
                        fabAddSchedule.visibility = if (canEditSchedule) View.VISIBLE else View.GONE
                        updateAdapterWithPermissions()
                        applyUserGroupRestriction()
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    private fun applyUserGroupRestriction() {
        val user = currentUser ?: return
        if (user.isAdmin()) return
        val groupName = user.groupName.takeIf { it.isNotBlank() } ?: user.group.takeIf { it.isNotBlank() } ?: return
        val filtered = scheduleList.filter { it.group.equals(groupName, ignoreCase = true) }
        if (filtered.size < scheduleList.size) {
            scheduleList.clear()
            scheduleList.addAll(filtered)
        }
        currentGroupFilter = groupName
        applyDayFilter()
    }

    private fun canModifyScheduleItem(schedule: ScheduleItem): Boolean {
        val user = currentUser ?: return false
        return ContentOwnershipRules.canModify(user, schedule.createdBy, schedule.createdByRole)
    }

    private fun updateAdapterWithPermissions() {
        scheduleAdapter = ScheduleAdapter(
            scheduleList.toList(),
            canEditSchedule,
            { item -> canModifyScheduleItem(item) },
            { schedule -> showScheduleDetails(schedule) },
            { schedule -> showActionDialog(schedule) }
        )
        scheduleRecyclerView.adapter = scheduleAdapter

        if (scheduleList.isNotEmpty()) {
            applyDayFilter()
        }
    }

    private fun startRealtimeUpdates() {
        stopRealtimeUpdates()

        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        try {

            val query = scheduleRepository.scheduleCollection

            AppLog.d(TAG, "📡 Загрузка расписания...")

            scheduleListener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLog.e(TAG, "❌ Ошибка загрузки расписания: ${error.message}")
                    error.printStackTrace()
                    progressBar.visibility = View.GONE

                    if (scheduleList.isNotEmpty()) {
                        applyDayFilter()
                    } else {
                        showEmptyState("Ошибка загрузки. Проверьте интернет.")
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null) {

                    val isFromCache = snapshot.metadata.isFromCache

                    AppLog.d(TAG, "📊 Источник данных: ${if (isFromCache) "КЭШ" else "СЕРВЕР"}")

                    progressBar.visibility = View.GONE

                    if (!snapshot.isEmpty) {
                        val schedules = mutableListOf<ScheduleItem>()

                        for (document in snapshot.documents) {
                            val schedule = documentToScheduleItem(document)
                            if (schedule != null) {
                                schedules.add(schedule)
                            }
                        }

                        val sortedSchedules = ScheduleSortHelper.sortedAll(schedules) { getDayOrder(it) }

                        AppLog.d(TAG, "✅ Загружено ${sortedSchedules.size} занятий (${if (isFromCache) "из кэша" else "с сервера"})")

                        if (!isFromCache || scheduleList.isEmpty()) {
                            scheduleList.clear()
                            scheduleList.addAll(sortedSchedules)
                            applyUserGroupRestriction()
                            applyDayFilter()
                        }
                    } else {
                        AppLog.d(TAG, "📭 Расписаний нет в базе (${if (isFromCache) "кэш" else "сервер"})")
                        scheduleList.clear()
                        scheduleAdapter.updateSchedule(emptyList())
                        showEmptyState("Расписаний пока нет")
                    }
                }
            }

            AppLog.d(TAG, "✅ Realtime listener запущен")

        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            AppLog.e(TAG, "❌ Ошибка: ${e.message}")
            showEmptyState("Ошибка загрузки")
        }
    }

    private fun getDayOrder(day: String): Int {
        return when (day.lowercase()) {
            "понедельник" -> 1
            "вторник" -> 2
            "среда" -> 3
            "четверг" -> 4
            "пятница" -> 5
            "суббота" -> 6
            "воскресенье" -> 7
            else -> 8
        }
    }

    private fun showEmptyState(message: String) {
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = message
    }

    private fun documentToScheduleItem(document: com.google.firebase.firestore.DocumentSnapshot): ScheduleItem? {
        return try {
            val day = document.getString("day") ?: ""
            val date = document.getString("date") ?: ""
            val time = document.getString("time") ?: ""
            val subject = document.getString("subject") ?: ""
            val teacher = document.getString("teacherName") ?: ""
            val room = document.getString("room") ?: ""
            val isSubgroup = document.getBoolean("isSubgroup") ?: false
            val teacherName2 = document.getString("teacherName2") ?: ""
            val room2 = document.getString("room2") ?: ""
            val group = document.getString("group") ?: ""
            val typeStr = document.getString("type") ?: "LECTURE"

            if (day.isEmpty() || time.isEmpty() || subject.isEmpty()) {
                AppLog.w(TAG, "⚠️ Пропущен документ с пустыми полями: $subject")
                return null
            }

            val type = try {
                ScheduleType.valueOf(typeStr)
            } catch (e: IllegalArgumentException) {
                ScheduleType.LECTURE
            }

            ScheduleItem(
                id = document.id,
                day = day,
                date = date,
                time = time,
                subject = subject,
                teacherName = teacher,
                room = room,
                isSubgroup = isSubgroup,
                teacherName2 = teacherName2,
                room2 = room2,
                type = type,
                group = group,
                createdAt = java.util.Date(),
                createdBy = document.getString("createdBy") ?: "",
                createdByRole = document.getString("createdByRole") ?: ""
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка конвертации: ${e.message}")
            null
        }
    }

    private fun applyDayFilter() {
        val selectedDateStr = dateFormat.format(selectedDateCalendar.time)

        var filtered = if (currentGroupFilter != null) {
            scheduleList.filter { it.group.equals(currentGroupFilter, ignoreCase = true) }
        } else {
            scheduleList.toList()
        }

        filtered = filtered.filter { it.date == selectedDateStr }
        filtered = ScheduleSortHelper.sortedForDay(filtered)

        AppLog.d(TAG, "📊 Отображаем ${filtered.size} занятий (дата: $selectedDateStr, группа: ${currentGroupFilter ?: "все"})")

        scheduleAdapter.updateSchedule(filtered)

        scheduleRecyclerView.post {
            scheduleAdapter.notifyDataSetChanged()
        }

        if (filtered.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = when {
                currentGroupFilter != null ->
                    "Нет занятий для группы $currentGroupFilter на $selectedDateStr"
                else ->
                    "Нет занятий на $selectedDateStr"
            }
        } else {
            emptyStateText.visibility = View.GONE
        }
    }

    private fun stopRealtimeUpdates() {
        scheduleListener?.remove()
        scheduleListener = null
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(
            emptyList(),
            canEditSchedule,
            { item -> canModifyScheduleItem(item) },
            { schedule -> showScheduleDetails(schedule) },
            { schedule -> showActionDialog(schedule) }
        )
        scheduleRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        scheduleRecyclerView.adapter = scheduleAdapter
    }

    private fun setupClickListeners() {
        fabAddSchedule.setOnClickListener {
            if (canEditSchedule) {
                showAddScheduleDialog()
            } else {
                Snackbar.make(requireView(), "Нет прав для добавления", Snackbar.LENGTH_SHORT).show()
            }
        }

        prevDateBtn.setOnClickListener {
            shiftDateSkippingSunday(-1)
            syncDayFilterWithSelectedDate()
            applyDayFilter()
        }

        nextDateBtn.setOnClickListener {
            shiftDateSkippingSunday(1)
            syncDayFilterWithSelectedDate()
            applyDayFilter()
        }

        dateText.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDateCalendar.set(Calendar.YEAR, year)
                selectedDateCalendar.set(Calendar.MONTH, month)
                selectedDateCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                normalizeSelectedDateToStudyDay()
                syncDayFilterWithSelectedDate()
                applyDayFilter()
            },
            selectedDateCalendar.get(Calendar.YEAR),
            selectedDateCalendar.get(Calendar.MONTH),
            selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun syncDayFilterWithSelectedDate() {
        currentDayFilter = getRussianDayName(selectedDateCalendar)
        updateDateLabelOnly()
    }

    private fun updateDateLabelOnly() {
        val selectedDate = dateFormat.format(selectedDateCalendar.time)
        val dayName = getRussianDayName(selectedDateCalendar)
        dateText.text = "$selectedDate\n$dayName"
    }

    private fun getRussianDayName(calendar: Calendar): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Понедельник"
            Calendar.TUESDAY -> "Вторник"
            Calendar.WEDNESDAY -> "Среда"
            Calendar.THURSDAY -> "Четверг"
            Calendar.FRIDAY -> "Пятница"
            Calendar.SATURDAY -> "Суббота"
            else -> "Понедельник"
        }
    }

    private fun normalizeSelectedDateToStudyDay() {
        if (selectedDateCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            selectedDateCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun shiftDateSkippingSunday(step: Int) {
        selectedDateCalendar.add(Calendar.DAY_OF_MONTH, step)
        if (selectedDateCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            selectedDateCalendar.add(Calendar.DAY_OF_MONTH, step)
        }
    }

    private fun showScheduleDetails(schedule: ScheduleItem) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_schedule_lesson_details, null)

        view.findViewById<TextView>(R.id.detailSubject).text = schedule.subject
        view.findViewById<TextView>(R.id.detailTypeBadge).text = getTypeDisplayName(schedule.type)
        view.findViewById<TextView>(R.id.detailTime).text = schedule.time
        val dateStr = if (schedule.date.isNotBlank()) "${schedule.day}, ${schedule.date}" else schedule.day
        view.findViewById<TextView>(R.id.detailDate).text = dateStr
        val teacherStr = if (schedule.isSubgroup && schedule.teacherName2.isNotBlank()) {
            "${schedule.teacherName} / ${schedule.teacherName2}"
        } else {
            schedule.teacherName
        }
        view.findViewById<TextView>(R.id.detailTeacher).text = teacherStr
        val roomStr = if (schedule.isSubgroup && schedule.room2.isNotBlank()) {
            "Ауд. ${schedule.room} / ${schedule.room2}"
        } else {
            "Ауд. ${schedule.room}"
        }
        view.findViewById<TextView>(R.id.detailRoom).text = roomStr
        view.findViewById<TextView>(R.id.detailGroup).text = schedule.group

        val typeBadge = view.findViewById<TextView>(R.id.detailTypeBadge)
        val typeColorRes = when (schedule.type) {
            ScheduleType.LECTURE -> R.color.lecture_color
            ScheduleType.PRACTICE -> R.color.practice_color
            ScheduleType.LAB -> R.color.lab_color
            ScheduleType.CONSULTATION -> R.color.consultation_color
            ScheduleType.EXAM -> R.color.exam_color
            ScheduleType.CONTROL_WORK -> R.color.control_work_color
            ScheduleType.LUNCH -> R.color.lunch_color
            ScheduleType.CURATOR_HOUR -> R.color.consultation_color
            ScheduleType.INFO_HOUR -> R.color.seminar_color
        }
        DrawableUtils.setViewBackgroundColor(
            typeBadge,
            androidx.core.content.ContextCompat.getColor(requireContext(), typeColorRes)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getTypeDisplayName(type: ScheduleType): String = ScheduleTypeLabels.displayName(type)

    private fun showActionDialog(schedule: ScheduleItem) {
        if (!canModifyScheduleItem(schedule)) {
            Snackbar.make(requireView(), "Нет прав для изменения этой записи", Snackbar.LENGTH_SHORT).show()
            return
        }
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_menu, null)
        val titleText = menuView.findViewById<TextView>(R.id.menuTitleText)
        val editBtn = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton1)
        val deleteBtn = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton2)
        menuView.findViewById<View>(R.id.actionButton3).visibility = View.GONE
        menuView.findViewById<View>(R.id.actionButton4).visibility = View.GONE

        titleText.text = "${schedule.subject}\n${schedule.day} ${schedule.time} • ${schedule.room}"
        editBtn.text = "Редактировать"
        deleteBtn.text = "Удалить"

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(menuView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        editBtn.setOnClickListener { dialog.dismiss(); showEditScheduleDialog(schedule) }
        deleteBtn.setOnClickListener { dialog.dismiss(); showDeleteConfirmationDialog(schedule) }
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(schedule: ScheduleItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Подтверждение удаления")
            .setMessage("Вы уверены, что хотите удалить занятие:\n\n" +
                    "📚 ${schedule.subject}\n" +
                    "👨‍🏫 ${schedule.teacherName}\n" +
                    "🏫 Ауд. ${schedule.room}\n" +
                    "👥 ${schedule.group}")
            .setPositiveButton("Удалить") { dialog, _ ->
                deleteScheduleFromFirestore(schedule)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteScheduleFromFirestore(schedule: ScheduleItem) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = scheduleRepository.deleteSchedule(
                    schedule.id,
                    auth.currentUser?.uid.orEmpty()
                )

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (success) {
                        scheduleList.removeAll { it.id == schedule.id }
                        applyDayFilter()
                        Snackbar.make(requireView(), "Занятие успешно удалено!", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(requireView(), "Ошибка при удалении занятия", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showEditScheduleDialog(schedule: ScheduleItem) {
        val lockGroup = (currentUser?.isAdmin() != true) && currentUser != null
        val groupForSubjects = schedule.group.takeIf { it.isNotBlank() }
        CoroutineScope(Dispatchers.IO).launch {
            val groupId = if (!groupForSubjects.isNullOrBlank()) GroupRepository.groupNameToDocumentId(groupForSubjects) else ""
            val adminRepo = AdminRepository()
            val subjectOptions = if (groupId.isNotBlank()) {
                adminRepo.getSubjectNamesForGroupOnDate(groupId, schedule.date)
            } else {
                emptyList()
            }
            val teacherNamesBySubject = if (groupId.isNotBlank() && subjectOptions.isNotEmpty()) {
                subjectOptions.associateWith { subject ->
                    adminRepo.getCatalogTeacherNamesForSubject(subject, groupId)
                }.filterValues { it.isNotEmpty() }
            } else {
                emptyMap()
            }
            val catalogTeacherNames = if (groupId.isNotBlank()) adminRepo.getCatalogTeacherNamesForGroup(groupId) else emptyList()
            val teacherNames = if (catalogTeacherNames.isNotEmpty()) catalogTeacherNames else {
                userRepository.getTeachers().map { it.fullName.ifBlank { it.email } }.filter { it.isNotBlank() }.distinct()
            }
            withContext(Dispatchers.Main) {
                val editDialog = EditScheduleDialogFragment.newInstance(
                    schedule,
                    lockGroup,
                    groupId,
                    subjectOptions,
                    teacherNames,
                    teacherNamesBySubject
                )
                editDialog.setOnScheduleUpdatedListener { updatedSchedule ->
                    updateScheduleInFirestore(updatedSchedule)
                }
                editDialog.show(parentFragmentManager, "EditScheduleDialog")
            }
        }
    }

    private fun updateScheduleInFirestore(schedule: ScheduleItem) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = scheduleRepository.updateSchedule(
                    schedule.id,
                    schedule,
                    auth.currentUser?.uid.orEmpty()
                )

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (success) {
                        val index = scheduleList.indexOfFirst { it.id == schedule.id }
                        if (index >= 0) scheduleList[index] = schedule
                        val resorted = ScheduleSortHelper.sortedAll(scheduleList.toList()) { getDayOrder(it) }
                        scheduleList.clear()
                        scheduleList.addAll(resorted)
                        applyDayFilter()
                        Snackbar.make(requireView(), "Занятие успешно обновлено!", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(requireView(), "Ошибка при обновлении занятия", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAddScheduleDialog() {
        val defaultGroup = currentUser?.takeIf { !it.isAdmin() }?.groupName?.takeIf { it.isNotBlank() } ?: ""
        CoroutineScope(Dispatchers.IO).launch {
            val groupId = if (defaultGroup.isNotBlank()) GroupRepository.groupNameToDocumentId(defaultGroup) else ""
            val adminRepo = AdminRepository()
            val currentDateStr = dateFormat.format(selectedDateCalendar.time)
            val subjectOptions = if (groupId.isNotBlank()) {
                adminRepo.getSubjectNamesForGroupOnDate(groupId, currentDateStr)
            } else {
                emptyList()
            }
            val teacherNamesBySubject = if (groupId.isNotBlank() && subjectOptions.isNotEmpty()) {
                subjectOptions.associateWith { subject ->
                    adminRepo.getCatalogTeacherNamesForSubject(subject, groupId)
                }.filterValues { it.isNotEmpty() }
            } else {
                emptyMap()
            }
            val catalogTeacherNames = when {
                groupId.isNotBlank() -> adminRepo.getCatalogTeacherNamesForGroup(groupId)
                else -> adminRepo.getAllCatalogTeacherNames()
            }
            val teacherNames = if (catalogTeacherNames.isNotEmpty()) catalogTeacherNames else {
                userRepository.getTeachers().map { it.fullName.ifBlank { it.email } }.filter { it.isNotBlank() }.distinct()
            }
            withContext(Dispatchers.Main) {
                val daySchedules = scheduleList.filter {
                    it.date == currentDateStr && it.group.equals(defaultGroup, ignoreCase = true)
                }
                val usedTimeSlots = daySchedules.map { it.time }.distinct()
                val datesWithLunch = scheduleList
                    .filter {
                        it.type == ScheduleType.LUNCH && it.group.equals(defaultGroup, ignoreCase = true)
                    }
                    .map { it.date }
                    .filter { it.isNotBlank() }
                    .toSet()
                val addScheduleDialog = AddScheduleDialogFragment.newInstance(
                    defaultGroup,
                    groupId,
                    subjectOptions,
                    teacherNames,
                    teacherNamesBySubject,
                    currentDateStr,
                    usedTimeSlots,
                    datesWithLunch
                )
                addScheduleDialog.setOnScheduleAddedListener { newSchedule ->
                    saveScheduleToFirestore(newSchedule)
                }
                addScheduleDialog.show(parentFragmentManager, "AddScheduleDialog")
            }
        }
    }

    private fun saveScheduleToFirestore(schedule: ScheduleItem) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firebaseUser = auth.currentUser
                val scheduleWithCreator = schedule.copy(
                    createdBy = firebaseUser?.uid ?: "",
                    createdByRole = currentUser?.role ?: ""
                )
                scheduleRepository.addSchedule(scheduleWithCreator)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    setSelectedDateFromString(schedule.date)
                    syncDayFilterWithSelectedDate()
                    applyDayFilter()
                    Snackbar.make(requireView(), "Занятие добавлено!", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setSelectedDateFromString(dateStr: String) {
        val parts = dateStr.split(".")
        if (parts.size == 3) {
            val day = parts[0].toIntOrNull() ?: return
            val month = (parts[1].toIntOrNull() ?: 1) - 1
            val year = parts[2].toIntOrNull() ?: return
            selectedDateCalendar.set(year, month, day)
            updateDateLabelOnly()
        }
    }

    private fun showError(message: String) {
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = message
    }
}
