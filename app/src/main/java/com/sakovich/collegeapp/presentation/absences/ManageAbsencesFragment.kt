package com.sakovich.collegeapp.presentation.absences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.SubjectForGroup
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.models.getAbsenceReasonDisplayName
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.repositories.AbsenceRepository
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.AbsenceFormat
import com.sakovich.collegeapp.utils.ContentOwnershipRules
import com.sakovich.collegeapp.utils.SemesterFilterHelper
import com.sakovich.collegeapp.utils.StatisticsExporter
import com.sakovich.collegeapp.utils.SubjectSemesterFilterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ManageAbsencesFragment : Fragment() {

    private lateinit var absencesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var totalHoursText: TextView
    private lateinit var fabAddAbsence: FloatingActionButton
    private lateinit var exportButton: MaterialButton
    private lateinit var semesterFilterLayout: TextInputLayout
    private lateinit var semesterFilterDropdown: AutoCompleteTextView
    private lateinit var studentFilterLayout: TextInputLayout
    private lateinit var studentFilterDropdown: AutoCompleteTextView
    private lateinit var statusFilterLayout: TextInputLayout
    private lateinit var statusFilterDropdown: AutoCompleteTextView
    private lateinit var dateFromButton: MaterialButton
    private lateinit var dateToButton: MaterialButton
    private lateinit var clearDatesButton: MaterialButton
    private lateinit var manageAbsencesBackButton: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var absenceRepository: AbsenceRepository
    private lateinit var gradeRepository: GradeRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var adminRepository: AdminRepository
    private lateinit var absencesAdapter: AbsencesAdapter

    private var currentUser: User? = null
    private val absencesList = mutableListOf<Absence>()
    private val studentsList = mutableListOf<User>()
    private var currentStudentFilter: String? = null
    private var currentStatusFilter: String? = null
    private var dateFrom: Long? = null
    private var dateTo: Long? = null
    private var currentSemesterFilter: Int? = null
    private var semesterDateRange: Pair<Date, Date>? = null
    private var subjectOptionsList: List<String> = emptyList()
    private var catalogSubjectsForGroup: List<SubjectForGroup> = emptyList()
    private var groupSemesters: List<AdminSemesterTemplate> = emptyList()
    private var filteredAbsencesForExport = emptyList<Absence>()

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri -> savePdfToUri(uri) }
        } else {
            exportButton.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_manage_absences, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        absenceRepository = AbsenceRepository()
        gradeRepository = GradeRepository()
        notificationRepository = NotificationRepository()
        userRepository = UserRepository()
        groupRepository = GroupRepository()
        adminRepository = AdminRepository()

        initViews(view)
        setupRecyclerView()
        setupFilters()
        setupSemesterDropdown()
        loadCurrentUser()
    }

    override fun onResume() {
        super.onResume()
        if (currentUser != null) {
            loadAbsences()
        }
    }

    private fun initViews(view: View) {
        absencesRecyclerView = view.findViewById(R.id.absencesRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        countText = view.findViewById(R.id.countText)
        totalHoursText = view.findViewById(R.id.totalHoursText)
        fabAddAbsence = view.findViewById(R.id.fabAddAbsence)
        exportButton = view.findViewById(R.id.exportButton)
        semesterFilterLayout = view.findViewById(R.id.semesterFilterLayout)
        semesterFilterDropdown = view.findViewById(R.id.semesterFilterDropdown)
        studentFilterLayout = view.findViewById(R.id.studentFilterLayout)
        studentFilterDropdown = view.findViewById(R.id.studentFilterDropdown)
        statusFilterLayout = view.findViewById(R.id.statusFilterLayout)
        statusFilterDropdown = view.findViewById(R.id.statusFilterDropdown)
        dateFromButton = view.findViewById(R.id.dateFromButton)
        dateToButton = view.findViewById(R.id.dateToButton)
        clearDatesButton = view.findViewById(R.id.clearDatesButton)
        manageAbsencesBackButton = view.findViewById(R.id.manageAbsencesBackButton)

        manageAbsencesBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        exportButton.setOnClickListener { showExportOptions() }
        fabAddAbsence.setOnClickListener {
            showAddAbsenceDialog()
        }
    }

    private val statusFilterOptions = listOf(
        "Все",
        "❌ Неуважительные",
        "✅ Уважительные"
    )

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private fun setupSemesterDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            SemesterFilterHelper.dropdownOptions
        )
        semesterFilterDropdown.setAdapter(adapter)
        semesterFilterDropdown.setText(SemesterFilterHelper.dropdownOptions.first(), false)
        semesterFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentSemesterFilter = SemesterFilterHelper.semesterNumberFromPosition(position)
            semesterFilterDropdown.setText(SemesterFilterHelper.dropdownOptions[position], false)
            loadSemesterDateRange()
        }
    }

    private fun loadSemesterDateRange() {
        val semester = currentSemesterFilter
        if (semester == null) {
            semesterDateRange = null
            refreshSubjectOptions()
            filterAbsences()
            return
        }
        val groupId = currentUser?.groupId?.takeIf { it.isNotBlank() }
            ?: currentUser?.groupName?.takeIf { it.isNotBlank() }?.let { GroupRepository.groupNameToDocumentId(it) }
            ?: currentUser?.group?.takeIf { it.isNotBlank() }?.let { GroupRepository.groupNameToDocumentId(it) }
            ?: ""
        if (groupId.isBlank()) {
            semesterDateRange = null
            refreshSubjectOptions()
            filterAbsences()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val range = withContext(Dispatchers.IO) {
                SemesterFilterHelper.dateRangeForSemester(adminRepository, groupId, semester)
            }
            if (!isAdded || view == null) return@launch
            semesterDateRange = range
            refreshSubjectOptions()
            filterAbsences()
        }
    }

    private fun effectiveFilterDateRange(): Pair<Date?, Date?> {
        val from = dateFrom?.let { Date(it) }
        val to = dateTo?.let { Date(it) }
        if (from != null || to != null) {
            return Pair(from ?: to, to ?: from)
        }
        return semesterDateRange?.let { it.first to it.second } ?: Pair(null, null)
    }

    private fun refreshSubjectOptions() {
        val (start, end) = effectiveFilterDateRange()
        subjectOptionsList = SubjectSemesterFilterHelper.filterSubjectNames(
            catalogSubjectsForGroup,
            groupSemesters,
            start,
            end
        )
    }

    private fun setupFilters() {

        val statusAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            statusFilterOptions
        )
        statusFilterDropdown.setAdapter(statusAdapter)

        statusFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentStatusFilter = when (position) {
                1 -> "unexcused"
                2 -> "excused"
                else -> null
            }
            statusFilterDropdown.setText(statusFilterOptions[position], false)
            filterAbsences()
        }

        dateFromButton.setOnClickListener { showDatePicker(true) }
        dateToButton.setOnClickListener { showDatePicker(false) }
        clearDatesButton.setOnClickListener {
            dateFrom = null
            dateTo = null
            dateFromButton.text = "С даты"
            dateToButton.text = "По дату"
                clearDatesButton.visibility = View.GONE
            refreshSubjectOptions()
            filterAbsences()
        }
    }

    private fun updateStudentFilterDropdown() {
        val studentNames = listOf("Все учащиеся") + studentsList.map { it.fullName.removeSuffix(" (Староста)").trim() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        studentFilterDropdown.setAdapter(adapter)
        studentFilterDropdown.setText("Все учащиеся", false)

        studentFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentStudentFilter = if (position == 0) null else studentsList.getOrNull(position - 1)?.id
            studentFilterDropdown.setText(studentNames[position], false)
            filterAbsences()
        }
    }

    private fun showDatePicker(isFromDate: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dateStr = String.format(Locale.getDefault(), "%02d.%02d.%04d", dayOfMonth, month + 1, year)
                if (isFromDate) {
                    dateFrom = selectedCal.timeInMillis
                    dateFromButton.text = dateStr
                } else {
                    dateTo = selectedCal.timeInMillis
                    dateToButton.text = dateStr
                }
                clearDatesButton.visibility = View.VISIBLE
                refreshSubjectOptions()
                filterAbsences()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun canModifyAbsence(absence: Absence): Boolean {
        val user = currentUser ?: return false
        return ContentOwnershipRules.canModify(user, absence.createdBy, absence.createdByRole)
    }

    private fun setupRecyclerView() {
        absencesAdapter = AbsencesAdapter(
            absences = absencesList,
            showStudentName = true,
            canManage = true,
            canModifyItem = { canModifyAbsence(it) },
            onAbsenceClick = { absence -> showAbsenceDetails(absence) },
            onAbsenceLongClick = { absence -> showAbsenceActionDialog(absence) }
        )
        absencesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        absencesRecyclerView.adapter = absencesAdapter
    }

    private fun loadCurrentUser() {
        val firebaseUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    userRepository.getUser(firebaseUser.uid)
                }
                if (!isAdded || view == null) return@launch
                currentUser = user
                if (user?.role == "teacher" || user?.role == "headman") {
                    exportButton.visibility = View.VISIBLE
                }
                user?.groupName?.takeIf { it.isNotBlank() }?.let { loadSubjectsForGroup(it) }
                loadStudents()
                loadAbsences()
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                showEmptyState("Ошибка загрузки")
            }
        }
    }

    private fun loadSubjectsForGroup(groupName: String) {
        if (groupName.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val groupId = GroupRepository.groupNameToDocumentId(groupName)
                    val subjects = adminRepository.getSubjects().filter { it.groupId == groupId }
                    val semesters = adminRepository.getSemestersForGroup(groupId)
                    subjects to semesters
                }
                if (!isAdded || view == null) return@launch
                catalogSubjectsForGroup = loaded.first
                groupSemesters = loaded.second
                refreshSubjectOptions()
            } catch (_: Exception) {
                if (!isAdded || view == null) return@launch
                catalogSubjectsForGroup = emptyList()
                groupSemesters = emptyList()
                subjectOptionsList = emptyList()
            }
        }
    }

    private fun loadStudents() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val studentsAndHeadmen = withContext(Dispatchers.IO) {
                    val userGroupId = currentUser?.groupId?.takeIf { it.isNotBlank() }
                    val userGroupName = currentUser?.groupName?.takeIf { it.isNotBlank() }
                        ?: currentUser?.group?.takeIf { it.isNotBlank() }
                    if (userGroupId != null || userGroupName != null) {
                        groupRepository.getStudentsByGroupId(
                            userGroupId ?: GroupRepository.groupNameToDocumentId(userGroupName!!)
                        ).map { s ->
                            com.sakovich.collegeapp.data.models.User(
                                id = s.id,
                                fullName = s.fullName,
                                email = s.email,
                                group = s.groupName,
                                groupId = s.groupId,
                                groupName = s.groupName,
                                role = if (s.isHeadman) "headman" else "student"
                            )
                        }
                    } else {
                        val allUsers = userRepository.getAllUsers()
                        allUsers.filter { it.role == "student" || it.role == "headman" }
                    }
                }
                if (!isAdded || view == null) return@launch
                studentsList.clear()
                studentsList.addAll(studentsAndHeadmen)
                updateStudentFilterDropdown()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadAbsences() {
        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val absences = withContext(Dispatchers.IO) {
                    val groupName = currentUser?.groupName?.takeIf { it.isNotBlank() }
                        ?: currentUser?.group?.takeIf { it.isNotBlank() } ?: ""
                    val loaded = if (groupName.isNotBlank()) {
                        absenceRepository.getGroupAbsences(groupName)
                    } else {
                        absenceRepository.getAllAbsences()
                    }
                    AbsenceFormat.enrichAbsences(loaded, userRepository)
                }
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                absencesList.clear()
                absencesList.addAll(absences)
                if (currentSemesterFilter != null) {
                    loadSemesterDateRange()
                } else {
                    filterAbsences()
                }
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                showEmptyState("Ошибка загрузки")
            }
        }
    }

    private fun filterAbsences() {
        var filtered = absencesList.toList()

        currentStudentFilter?.let { studentId ->
            filtered = filtered.filter { it.studentId == studentId }
        }

        when (currentStatusFilter) {
            "excused" -> filtered = filtered.filter { it.isExcused }
            "unexcused" -> filtered = filtered.filter { !it.isExcused }
        }

        filtered = filtered.filter { absence ->
            SemesterFilterHelper.matchesSemesterFilter(absence.date, semesterDateRange)
        }

        filtered = filtered.filter { absence ->
            val absenceTime = parseAbsenceDate(absence.date)
            val fromOk = dateFrom?.let { absenceTime >= it } ?: true
            val toOk = dateTo?.let { absenceTime <= it + 86400000 } ?: true
            fromOk && toOk
        }

        filteredAbsencesForExport = filtered
        absencesAdapter.updateAbsences(filtered)

        val totalHours = filtered.sumOf { it.hours }
        countText.text = "Записей: ${filtered.size}"
        totalHoursText.text = "$totalHours ч."

        if (filtered.isEmpty()) {
            showEmptyState(if (absencesList.isEmpty()) "Пропусков нет" else "Нет пропусков по фильтру")
        } else {
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun parseAbsenceDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun showExportOptions() {
        if (filteredAbsencesForExport.isEmpty()) {
            Snackbar.make(requireView(), "Нет данных для экспорта", Snackbar.LENGTH_SHORT).show()
            return
        }

        exportToPdf()
    }

    private fun exportToPdf() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Посещаемость_${dateFormat.format(Date())}.pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        exportButton.isEnabled = false
        savePdfLauncher.launch(intent)
    }

    private fun savePdfToUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val totalHours = filteredAbsencesForExport.sumOf { it.hours }
            val excusedHours = filteredAbsencesForExport.filter { it.isExcused }.sumOf { it.hours }
            val unexcusedHours = filteredAbsencesForExport.filter { !it.isExcused }.sumOf { it.hours }
            val groupName = currentUser?.groupName?.takeIf { it.isNotBlank() } ?: currentUser?.group ?: ""
            val title = "Пропуски${if (groupName.isNotBlank()) " — $groupName" else ""}"
            val success = withContext(Dispatchers.IO) {
                val doc = StatisticsExporter.createAbsencesPdfDocument(
                    title = title,
                    absences = filteredAbsencesForExport,
                    showStudentName = true,
                    totalHours = totalHours,
                    excusedHours = excusedHours,
                    unexcusedHours = unexcusedHours
                )
                StatisticsExporter.savePdfToUri(ctx, doc, uri)
            }
            if (!isAdded || view == null) return@launch
            exportButton.isEnabled = true
            if (success) {
                Snackbar.make(requireView(), "PDF сохранён", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(requireView(), "Ошибка сохранения PDF", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportToCsv() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Посещаемость_${dateFormat.format(Date())}"
        val uri = StatisticsExporter.exportAbsencesToCsv(requireContext(), filteredAbsencesForExport, fileName)
        if (uri != null) {
            Snackbar.make(requireView(), "CSV сохранён в Документы", Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(requireView(), "Ошибка сохранения CSV", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showAddAbsenceDialog(editingAbsence: Absence? = null) {
        val availableStudents = if (currentUser?.role == "headman") {

            studentsList.filter { it.group == currentUser?.group && it.role == "student" }
        } else {

            studentsList
        }

        if (availableStudents.isEmpty()) {
            Snackbar.make(requireView(), "Нет доступных учащихся", Snackbar.LENGTH_SHORT).show()
            return
        }

        val groupId = currentUser?.groupId?.takeIf { it.isNotBlank() }
            ?: currentUser?.groupName?.takeIf { it.isNotBlank() }?.let { GroupRepository.groupNameToDocumentId(it) }
            ?: currentUser?.group?.takeIf { it.isNotBlank() }?.let { GroupRepository.groupNameToDocumentId(it) }
            ?: ""

        val dialog = AddAbsenceDialogFragment.newInstance(
            currentUser = currentUser,
            students = availableStudents,
            subjectOptions = subjectOptionsList,
            groupId = groupId,
            editingAbsence = editingAbsence
        )

        dialog.setOnAbsenceSavedListener { absence, isEdit ->
            if (isEdit) {
                updateAbsence(absence)
            } else {
                addAbsence(absence)
            }
        }

        dialog.show(parentFragmentManager, "AddAbsenceDialog")
    }

    private fun addAbsence(absence: Absence) {
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    val newId = absenceRepository.addAbsence(absence)
                    try {
                        val grade = Grade(
                            studentId = absence.studentId,
                            studentName = absence.studentName,
                            subject = absence.subject,
                            date = absence.date,
                            value = Grade.VALUE_ABSENCE,
                            type = "",
                            teacherId = absence.createdBy,
                            teacherName = absence.createdByName,
                            comment = ""
                        )
                        gradeRepository.addGrade(grade)
                    } catch (_: Exception) {
                    }
                    try {
                        notificationRepository.createAbsenceNotification(
                            studentId = absence.studentId,
                            studentName = absence.studentName,
                            subject = absence.subject,
                            date = absence.date,
                            hours = absence.hours,
                            createdByName = absence.createdByName,
                            absenceId = newId,
                            isExcused = absence.isExcused
                        )
                    } catch (_: Exception) {
                    }
                    newId
                }
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                val absenceWithId = absence.copy(id = id)
                absencesList.add(0, absenceWithId)
                filterAbsences()
                Snackbar.make(requireView(), "✅ Пропуск добавлен", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateAbsence(absence: Absence) {
        if (!canModifyAbsence(absence)) {
            Snackbar.make(requireView(), "Нет прав для изменения этой записи", Snackbar.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val ok = absenceRepository.updateAbsence(absence.id, absence)
                    if (ok) {
                        try {
                            notificationRepository.createAbsenceUpdatedNotification(
                                studentId = absence.studentId,
                                subject = absence.subject,
                                date = absence.date,
                                hours = absence.hours,
                                updatedByName = absence.createdByName,
                                absenceId = absence.id,
                                isExcused = absence.isExcused
                            )
                        } catch (_: Exception) {
                        }
                    }
                    ok
                }
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                if (success) {
                    val index = absencesList.indexOfFirst { it.id == absence.id }
                    if (index >= 0) {
                        absencesList[index] = absence
                        filterAbsences()
                    }
                    Snackbar.make(requireView(), "✅ Пропуск обновлён", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "❌ Ошибка обновления", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteAbsence(absence: Absence) {
        if (!canModifyAbsence(absence)) {
            Snackbar.make(requireView(), "Нет прав для удаления этой записи", Snackbar.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    absenceRepository.deleteAbsence(absence.id)
                }
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                if (success) {
                    absencesList.removeAll { it.id == absence.id }
                    filterAbsences()
                    Snackbar.make(requireView(), "✅ Пропуск удалён", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "❌ Ошибка удаления", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                progressBar.visibility = View.GONE
                Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showAbsenceDetails(absence: Absence) {
        val excusedText = if (absence.isExcused) "✅ Уважительная" else "❌ Неуважительная"
        val commentText = if (absence.comment.isNotEmpty()) "\n\n💬 Комментарий:\n${absence.comment}" else ""

        val details = """
            👤 Учащийся: ${absence.studentName}
            👥 Группа: ${absence.studentGroup}
            📚 Предмет: ${absence.subject}
            📅 Дата: ${absence.date}
            ⏱️ Часы: ${absence.hours}

            📋 Причина: ${getAbsenceReasonDisplayName(absence.reason)}
            $excusedText

            ✍️ ${AbsenceFormat.creatorLine(absence.createdByRole, absence.createdByName)}$commentText
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 Детали пропуска")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAbsenceActionDialog(absence: Absence) {
        if (!canModifyAbsence(absence)) {
            Snackbar.make(requireView(), "Нет прав для изменения этой записи", Snackbar.LENGTH_SHORT).show()
            showAbsenceDetails(absence)
            return
        }
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_menu, null)
        val titleText = menuView.findViewById<TextView>(R.id.menuTitleText)
        val editBtn = menuView.findViewById<MaterialButton>(R.id.actionButton1)
        val deleteBtn = menuView.findViewById<MaterialButton>(R.id.actionButton2)
        menuView.findViewById<View>(R.id.actionButton3).visibility = View.GONE
        menuView.findViewById<View>(R.id.actionButton4).visibility = View.GONE

        titleText.text = "${absence.studentName} • ${absence.subject}\n${absence.date}"
        editBtn.setText("Редактировать")
        deleteBtn.setText("Удалить")

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(menuView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        editBtn.setOnClickListener { dialog.dismiss(); showAddAbsenceDialog(absence) }
        deleteBtn.setOnClickListener { dialog.dismiss(); showDeleteConfirmation(absence) }
        dialog.show()
    }

    private fun showDeleteConfirmation(absence: Absence) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🗑️ Удаление пропуска")
            .setMessage("Удалить пропуск?\n\n👤 ${absence.studentName}\n📚 ${absence.subject}\n📅 ${absence.date}")
            .setPositiveButton("Удалить") { _, _ ->
                deleteAbsence(absence)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEmptyState(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        emptyText.text = message
    }
}
