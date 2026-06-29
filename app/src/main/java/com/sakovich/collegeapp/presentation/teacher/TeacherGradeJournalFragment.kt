package com.sakovich.collegeapp.presentation.teacher

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.AutoCompleteTextView
import androidx.core.widget.NestedScrollView
import android.widget.RadioButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.JournalColumnLabel
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.data.models.SubjectForGroup
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.AbsenceRepository
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.JournalColumnLabelRepository
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.presentation.absences.AddAbsenceDialogFragment
import com.sakovich.collegeapp.utils.GradeJournalAverage
import com.sakovich.collegeapp.utils.StatisticsExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TeacherGradeJournalFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var adminRepository: AdminRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var gradeRepository: GradeRepository
    private lateinit var absenceRepository: AbsenceRepository
    private lateinit var journalColumnLabelRepository: JournalColumnLabelRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var userRepository: UserRepository

    private var teacherProfile: User? = null

    private lateinit var groupText: TextView
    private lateinit var weekLabel: TextView
    private lateinit var subjectDropdown: MaterialAutoCompleteTextView
    private lateinit var semesterDropdown: MaterialAutoCompleteTextView
    private lateinit var prevWeekBtn: MaterialButton
    private lateinit var nextWeekBtn: MaterialButton
    private lateinit var exportBtn: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var journalPageScroll: NestedScrollView
    private lateinit var journalScroll: HorizontalScrollView
    private lateinit var tableLayout: TableLayout
    private lateinit var teacherJournalBackButton: ImageButton

    private var groupName: String = ""
    private var students: List<Student> = emptyList()
    private var selectedWeekStart: Calendar = mondayOfCurrentWeek()
    private var allTeacherGrades: List<Grade> = emptyList()
    private var selectedSubject: String = ""
    private var isSummaryMode: Boolean = false
    private var selectedSemesterName: String = ""
    private var manualSubjects: MutableList<String> = mutableListOf()
    private var semesters: MutableList<JournalSemester> = mutableListOf()
    private var catalogSubjects: List<SubjectForGroup> = emptyList()
    private var currentWeekDates: List<Pair<String, String>> = emptyList()
    private var currentGradesByCell: Map<String, Grade> = emptyMap()
    private var columnLabels: Map<String, String> = emptyMap()

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
    private val weekRangeFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_grade_journal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        adminRepository = AdminRepository()
        groupRepository = GroupRepository()
        gradeRepository = GradeRepository()
        absenceRepository = AbsenceRepository()
        journalColumnLabelRepository = JournalColumnLabelRepository()
        notificationRepository = NotificationRepository()
        userRepository = UserRepository()

        groupText = view.findViewById(R.id.groupText)
        weekLabel = view.findViewById(R.id.weekLabel)
        subjectDropdown = view.findViewById(R.id.subjectDropdown)
        semesterDropdown = view.findViewById(R.id.semesterDropdown)
        prevWeekBtn = view.findViewById(R.id.prevWeekBtn)
        nextWeekBtn = view.findViewById(R.id.nextWeekBtn)
        exportBtn = view.findViewById(R.id.exportBtn)
        teacherJournalBackButton = view.findViewById(R.id.teacherJournalBackButton)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        journalPageScroll = view.findViewById(R.id.journalPageScroll)
        journalScroll = view.findViewById(R.id.journalScroll)
        tableLayout = view.findViewById(R.id.tableLayout)
        configureJournalTable()
        journalScroll.isHorizontalScrollBarEnabled = true

        teacherJournalBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        groupName = arguments?.getString(ARG_GROUP_NAME).orEmpty()
        groupText.text = "Группа: $groupName"

        prevWeekBtn.setOnClickListener {
            shiftWeek(-7)
        }
        nextWeekBtn.setOnClickListener {
            shiftWeek(7)
        }
        exportBtn.setOnClickListener { showExportDialog() }

        loadInitialData()
    }

    override fun onResume() {
        super.onResume()

        loadInitialData()
    }

    private fun loadInitialData() {
        val teacherId = auth.currentUser?.uid
        if (teacherId == null) {
            showEmpty("Пользователь не авторизован")
            return
        }

        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val groupId = GroupRepository.groupNameToDocumentId(groupName)
            val groupStudents = groupRepository.getStudentsByGroup(groupName)
            teacherProfile = userRepository.getUser(teacherId)
            val teacherGrades = gradeRepository.getTeacherGrades(teacherId)
            val adminSubjects = adminRepository.getSubjectsForGroup(groupId)
            catalogSubjects = adminRepository.getSubjects().filter { it.groupId == groupId }
            manualSubjects = loadSavedSubjects(teacherId).toMutableList()
            val adminSemesters = adminRepository.getSemestersForGroup(groupId)
            val savedSemesters = loadSavedSemesters(teacherId)
            semesters = (adminSemesters.map { JournalSemester(it.name, it.startDate, it.endDate) } + savedSemesters)
                .distinctBy { it.name.lowercase(Locale.getDefault()) }
                .sortedBy { parseDate(it.startDate)?.time ?: Long.MAX_VALUE }
                .toMutableList()
            val subjectOptions = (adminSubjects + teacherGrades
                .map { it.subject.trim() }
                .filter { it.isNotBlank() }
                .distinct() + manualSubjects)
                .distinct()
                .sorted()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                progressBar.visibility = View.GONE
                students = groupStudents
                allTeacherGrades = teacherGrades

                val ctx = context ?: return@withContext
                val adapter = ArrayAdapter(
                    ctx,
                    android.R.layout.simple_dropdown_item_1line,
                    subjectOptions
                )
                subjectDropdown.setAdapter(adapter)
                subjectDropdown.setOnClickListener { subjectDropdown.showDropDown() }
                subjectDropdown.setOnItemClickListener { _, _, _, _ ->
                    selectedSubject = subjectDropdown.text?.toString().orEmpty().trim()
                    isSummaryMode = false
                    setupSemesterDropdown()
                    refreshJournal()
                }
                setupSemesterDropdown()

                if (selectedSubject.isBlank() && subjectOptions.isNotEmpty()) {
                    selectedSubject = subjectOptions.first()
                    subjectDropdown.setText(selectedSubject, false)
                } else if (selectedSubject.isNotBlank()) {
                    subjectDropdown.setText(selectedSubject, false)
                }

                if (students.isEmpty()) {
                    showEmpty("В группе пока нет учащихся")
                } else if (selectedSubject.isBlank()) {
                    showEmpty("Выберите предмет, чтобы открыть журнал")
                } else {
                    hideEmpty()
                    refreshJournal()
                }
            }
        }
    }

    private fun refreshJournal() {
        if (students.isEmpty()) {
            showEmpty("В группе пока нет учащихся")
            return
        }
        val subject = subjectDropdown.text?.toString().orEmpty().trim()
        if (subject.isBlank()) {
            showEmpty("Выберите предмет, чтобы открыть журнал")
            return
        }
        selectedSubject = subject

        val selectedSemester = getSelectedSemester()

        if (isSummaryMode) {
            updateWeekLabelSummary(selectedSemester)
            buildSummaryTable()
            hideEmpty()
            return
        }

        if (selectedSemester != null && !isWeekInsideSemester(selectedWeekStart, selectedSemester)) {
            selectedWeekStart = mondayOfDate(selectedSemester.startDate)
        }

        val weekDates = weekDates(selectedWeekStart)
        currentWeekDates = weekDates
        updateWeekLabel(weekDates, selectedSemester)

        val studentIds = students.map { it.id }.toSet()
        val dateSet = weekDates.map { it.first }.toSet()

        val gradesByCell = allTeacherGrades
            .asSequence()
            .filter { it.subject.equals(subject, ignoreCase = true) }
            .filter { it.studentId in studentIds }
            .filter { it.date in dateSet }
            .sortedByDescending { it.createdAt }
            .associateBy { "${it.studentId}|${it.date}" }
        currentGradesByCell = gradesByCell

        val teacherId = auth.currentUser?.uid
        if (teacherId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val dates = weekDates.map { it.first }
                val labels = withContext(Dispatchers.IO) {
                    journalColumnLabelRepository.getLabelsForWeek(teacherId, groupName, subject, dates)
                }
                if (!isAdded || view == null) return@launch
                columnLabels = labels
                buildJournalTable(weekDates, gradesByCell)
                hideEmpty()
            }
        } else {
            if (!isAdded || view == null) return
            buildJournalTable(weekDates, gradesByCell)
            hideEmpty()
        }
    }

    private fun hostContext(): android.content.Context? =
        context?.takeIf { isAdded && view != null }

    private fun updateWeekLabelSummary(semester: JournalSemester?) {
        weekLabel.text = "Итоги семестра"
    }

    private fun buildSummaryTable() {
        val ctx = hostContext() ?: return
        tableLayout.removeAllViews()

        val sortedStudents = students.sortedBy { it.fullName.trim().lowercase(Locale.getDefault()) }
        val subject = selectedSubject
        val semester = getSelectedSemester()

        val header = TableRow(ctx).apply {
            setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
        }
        header.addView(buildHeaderCellFixed(ctx, "№", JOURNAL_COL_NUMBER_DP))
        header.addView(buildHeaderCellFixed(ctx, "ФИО", JOURNAL_SUMMARY_NAME_DP))
        header.addView(buildHeaderCellFixed(ctx, "Итого", JOURNAL_SUMMARY_TOTAL_DP))
        tableLayout.addView(header)

        sortedStudents.forEachIndexed { index, student ->
            val row = TableRow(ctx)
            row.addView(buildNumberCell(ctx, index + 1))
            row.addView(buildStudentCellWide(ctx, student.fullName, JOURNAL_SUMMARY_NAME_DP))

            val avg = calculateSemesterAverage(student.id, subject, semester)
            row.addView(buildSemesterGradeCell(ctx, avg, JOURNAL_SUMMARY_TOTAL_DP))

            tableLayout.addView(row)
        }
    }

    private fun buildStudentCellWide(
        ctx: android.content.Context,
        fullName: String,
        widthDp: Int = JOURNAL_COL_NAME_DP
    ): TextView {
        return TextView(ctx).apply {
            text = shortName(fullName)
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 13f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            maxLines = 2
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_name)
            layoutParams = journalCellLayout(widthDp)
        }
    }

    private fun buildSemesterGradeCell(
        ctx: android.content.Context,
        avg: Double?,
        widthDp: Int = JOURNAL_COL_SUMMARY_DP
    ): TextView {
        val rounded = avg?.let { kotlin.math.round(it).toInt() }
        return TextView(ctx).apply {
            text = rounded?.toString() ?: "—"
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_grade)
            layoutParams = journalCellLayout(widthDp)
        }
    }

    private fun buildJournalTable(
        weekDates: List<Pair<String, String>>,
        gradesByCell: Map<String, Grade>
    ) {
        val ctx = hostContext() ?: return
        tableLayout.removeAllViews()

        val sortedStudents = students.sortedBy { it.fullName.trim().lowercase(Locale.getDefault()) }

        val header = TableRow(ctx).apply {
            setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
        }
        header.addView(buildHeaderCellFixed(ctx, "№", JOURNAL_COL_NUMBER_DP))
        header.addView(buildHeaderCellFixed(ctx, "ФИО", JOURNAL_COL_NAME_DP))
        weekDates.forEach { (fullDate, shortDate) ->
            val columnType = columnLabels[fullDate]
            val dateCell = buildHeaderCell(ctx, shortDate, columnType, fullDate)
            dateCell.setOnClickListener { showColumnTypeDialog(fullDate) }
            header.addView(dateCell)
        }
        tableLayout.addView(header)

        sortedStudents.forEachIndexed { index, student ->
            val row = TableRow(ctx)
            row.addView(buildNumberCell(ctx, index + 1))
            row.addView(buildStudentCell(ctx, student.fullName))

            weekDates.forEach { (fullDate, _) ->
                val key = "${student.id}|$fullDate"
                val grade = gradesByCell[key]
                val cell = buildGradeCell(ctx, grade)
                cell.setOnClickListener {
                    showGradeDialog(student, fullDate, grade)
                }
                row.addView(cell)
            }

            tableLayout.addView(row)
        }
    }

    private fun isLastWeekOfSemester(weekStart: Calendar, semester: JournalSemester?): Boolean {
        if (semester == null) return false
        val semEnd = parseDate(semester.endDate) ?: return false
        val nextWeekStart = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }.time
        return nextWeekStart.after(semEnd)
    }

    private fun calculateSemesterAverage(studentId: String, subject: String, semester: JournalSemester?): Double? {
        val start = semester?.let { parseDate(it.startDate) }
        val end = semester?.let { parseDate(it.endDate) }

        val grades = allTeacherGrades
            .filter { it.studentId == studentId }
            .filter { it.subject.equals(subject, ignoreCase = true) }
            .filter { grade ->
                if (start == null || end == null) return@filter true
                val gradeDate = parseDate(grade.date) ?: return@filter false
                !gradeDate.before(start) && !gradeDate.after(end)
            }

        return GradeJournalAverage.calculateFinalAverage(grades)
    }

    private fun buildSummaryHeaderCell(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(requireContext(), R.color.grade_excellent))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 12)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_journal_cell_header)
            minWidth = dp(52)
            minHeight = dp(48)
        }
    }

    private fun buildSummaryCell(avg: Double?): TextView {
        return TextView(requireContext()).apply {
            text = if (avg != null) String.format("%.1f", avg) else "—"
            setTextColor(
                if (avg != null && avg >= 7) ContextCompat.getColor(requireContext(), R.color.grade_excellent)
                else if (avg != null && avg >= 5) ContextCompat.getColor(requireContext(), R.color.grade_satisfactory)
                else if (avg != null) ContextCompat.getColor(requireContext(), R.color.grade_fail)
                else ContextCompat.getColor(requireContext(), android.R.color.white)
            )
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_journal_cell_grade)
            minWidth = dp(52)
            minHeight = dp(44)
        }
    }

    private fun showGradeDialog(student: Student, date: String, existingGrade: Grade?) {
        val subject = selectedSubject.ifBlank { subjectDropdown.text?.toString().orEmpty().trim() }
        if (subject.isBlank()) {
            Toast.makeText(requireContext(), "Сначала выберите предмет", Toast.LENGTH_SHORT).show()
            return
        }

        val typeOptions = mutableListOf("Обычная", JournalColumnLabel.TYPE_CONTROL, JournalColumnLabel.TYPE_LAB, JournalColumnLabel.TYPE_PRACTICE, "Н (неявка)")
        if (existingGrade != null) typeOptions.add("Очистить")
        val currentType = existingGrade?.type?.takeIf { JournalColumnLabel.isLessonType(it) } ?: ""
        val initialSelection = when {
            currentType.isEmpty() -> 0
            typeOptions.indexOf(currentType) >= 0 -> typeOptions.indexOf(currentType)
            else -> 0
        }

        val title = "${student.fullName}\n$date  •  $subject"
        var selectedChoiceIndex = initialSelection

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(
                typeOptions.toTypedArray(),
                initialSelection
            ) { _, which -> selectedChoiceIndex = which }
            .setPositiveButton("Далее") { typeDialog, _ ->
                typeDialog.dismiss()
                val choice = typeOptions[selectedChoiceIndex]

                when {
                    choice == "Н (неявка)" -> showAddAbsenceFromJournal(student, date, subject, existingGrade)
                    choice == "Очистить" -> {
                        if (existingGrade != null) deleteGrade(existingGrade.id)
                    }
                    else -> {
                        val selectedType = if (choice == "Обычная") "" else choice
                        val gradeValues = (10 downTo 1).map { it.toString() }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Отметка")
                            .setItems(gradeValues.toTypedArray()) { _, which ->
                                val value = gradeValues[which].toIntOrNull() ?: return@setItems
                                saveOrUpdateGrade(student, date, subject, value, existingGrade, selectedType)
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddAbsenceFromJournal(student: Student, date: String, subject: String, existingGrade: Grade?) {
        val user = auth.currentUser ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val teacherAsUser = withContext(Dispatchers.IO) {
                resolveTeacherUser(user.uid)
            }
            val studentAsUser = User(
                id = student.id,
                fullName = student.fullName,
                group = groupName,
                role = "student"
            )
            val subjectOptionsList = (allTeacherGrades.map { it.subject }.filter { it.isNotBlank() } + subject)
                .distinct()
                .sorted()

            val dialog = AddAbsenceDialogFragment.newInstance(
                currentUser = teacherAsUser,
                students = listOf(studentAsUser),
                subjectOptions = subjectOptionsList,
                editingAbsence = null,
                prefillStudentId = student.id,
                prefillStudentName = student.fullName,
                prefillStudentGroup = groupName,
                prefillSubject = subject,
                prefillDate = date
            )
            dialog.setOnAbsenceSavedListener { absence, isEdit ->
            if (isEdit) return@setOnAbsenceSavedListener
            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val absenceId = absenceRepository.addAbsence(absence)
                    val grade = Grade(
                        id = existingGrade?.id.orEmpty(),
                        studentId = absence.studentId,
                        studentName = absence.studentName,
                        subject = absence.subject,
                        date = absence.date,
                        value = Grade.VALUE_ABSENCE,
                        type = "",
                        teacherId = absence.createdBy,
                        teacherName = absence.createdByName,
                        comment = existingGrade?.comment ?: "",
                        createdAt = existingGrade?.createdAt ?: System.currentTimeMillis()
                    )
                    if (existingGrade != null) {
                        gradeRepository.updateGrade(existingGrade.id, grade)
                    } else {
                        gradeRepository.addGrade(grade)
                    }
                    try {
                        notificationRepository.createAbsenceNotification(
                            studentId = absence.studentId,
                            studentName = absence.studentName,
                            subject = absence.subject,
                            date = absence.date,
                            hours = absence.hours,
                            createdByName = absence.createdByName,
                            absenceId = absenceId,
                            isExcused = absence.isExcused
                        )
                    } catch (_: Exception) { }
                    allTeacherGrades = gradeRepository.getTeacherGrades(absence.createdBy)
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        refreshJournal()
                        Toast.makeText(requireContext(), "Пропуск добавлен, в журнале выставлено Н", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            }
            if (!isAdded) return@launch
            dialog.show(parentFragmentManager, "AddAbsenceFromJournal")
        }
    }

    private suspend fun resolveTeacherUser(userId: String): User {
        val profile = teacherProfile ?: userRepository.getUser(userId)?.also { teacherProfile = it }
        if (profile != null) {
            return profile.copy(role = profile.role.ifBlank { "teacher" })
        }
        val fb = auth.currentUser
        return User(
            id = userId,
            fullName = fb?.displayName?.takeIf { it.isNotBlank() } ?: "Куратор",
            role = "teacher"
        )
    }

    private fun teacherDisplayName(): String =
        teacherProfile?.fullName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Куратор"

    private fun saveOrUpdateGrade(
        student: Student,
        date: String,
        subject: String,
        value: Int,
        existingGrade: Grade?,
        lessonType: String = ""
    ) {
        val currentUser = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE

        val typeValue = if (lessonType.isBlank()) "Обычная" else lessonType
        val semesterValue = resolveSemesterNumberForDate(date)
        val grade = Grade(
            id = existingGrade?.id.orEmpty(),
            studentId = student.id,
            studentName = student.fullName,
            subject = subject,
            value = value,
            date = date,
            type = typeValue,
            teacherId = currentUser.uid,
            teacherName = teacherDisplayName(),
            comment = existingGrade?.comment ?: "",
            semester = semesterValue,
            createdAt = existingGrade?.createdAt ?: System.currentTimeMillis()
        )

        CoroutineScope(Dispatchers.IO).launch {
            val success = if (existingGrade != null) {
                gradeRepository.updateGrade(existingGrade.id, grade)
            } else {
                val gradeId = gradeRepository.addGrade(grade)
                if (value != Grade.VALUE_ABSENCE) {
                    try {
                        notificationRepository.createGradeNotification(
                            studentId = student.id,
                            studentName = student.fullName,
                            subject = subject,
                            grade = value,
                            teacherName = grade.teacherName,
                            gradeId = gradeId
                        )
                    } catch (_: Exception) { }
                }
                true
            }

            if (success) {
                allTeacherGrades = gradeRepository.getTeacherGrades(currentUser.uid)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (success) {
                    refreshJournal()
                } else {
                    Toast.makeText(requireContext(), "Не удалось сохранить отметку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resolveSemesterNumberForDate(dateString: String): Int {
        val gradeDate = parseDate(dateString)
        if (gradeDate != null) {
            val semesterByDate = semesters.firstOrNull { sem ->
                val start = parseDate(sem.startDate) ?: return@firstOrNull false
                val end = parseDate(sem.endDate) ?: return@firstOrNull false
                !gradeDate.before(start) && !gradeDate.after(end)
            }?.name
            val parsed = semesterByDate?.let { name ->
                Regex("(\\d+)").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            if (parsed != null) return parsed
        }
        val selectedParsed = Regex("(\\d+)").find(selectedSemesterName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return selectedParsed ?: 1
    }

    private fun deleteGrade(gradeId: String) {
        val teacherId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val success = gradeRepository.deleteGrade(gradeId)
            if (success) {
                allTeacherGrades = gradeRepository.getTeacherGrades(teacherId)
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (success) {
                    refreshJournal()
                } else {
                    Toast.makeText(requireContext(), "Не удалось удалить отметку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateWeekLabel(weekDates: List<Pair<String, String>>) {
        if (weekDates.isEmpty()) {
            weekLabel.text = "Неделя"
            return
        }
        val start = weekRangeFormat.format(parseDate(weekDates.first().first) ?: Date())
        val end = weekRangeFormat.format(parseDate(weekDates.last().first) ?: Date())
        weekLabel.text = "Неделя: $start - $end"
    }

    private fun updateWeekLabel(weekDates: List<Pair<String, String>>, semester: JournalSemester?) {
        if (weekDates.isEmpty()) {
            weekLabel.text = "Неделя"
            return
        }
        val start = weekRangeFormat.format(parseDate(weekDates.first().first) ?: Date())
        val end = weekRangeFormat.format(parseDate(weekDates.last().first) ?: Date())
        weekLabel.text = "$start - $end"
    }

    private fun weekDates(monday: Calendar): List<Pair<String, String>> {
        return (0..4).map { dayOffset ->
            val date = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dayOffset) }.time
            val full = dateFormat.format(date)
            val short = shortDateFormat.format(date)
            full to short
        }
    }

    private fun configureJournalTable() {
        tableLayout.isStretchAllColumns = false
        tableLayout.isShrinkAllColumns = false
    }

    private fun journalCellLayout(widthDp: Int, heightDp: Int = JOURNAL_ROW_HEIGHT_DP): TableRow.LayoutParams {
        return TableRow.LayoutParams(dp(widthDp), dp(heightDp))
    }

    private fun buildHeaderCellFixed(ctx: android.content.Context, text: String, widthDp: Int): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_header)
            layoutParams = journalCellLayout(widthDp)
            maxLines = 1
        }
    }

    private fun buildHeaderCell(
        ctx: android.content.Context,
        text: String,
        columnType: String?,
        fullDate: String?
    ): TextView {
        return TextView(ctx).apply {
            this.text = if (columnType.isNullOrBlank()) text else "$text\n$columnType"
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(4, 6, 4, 6)
            setLineSpacing(0f, 1f)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_header)
            layoutParams = journalCellLayout(JOURNAL_COL_GRADE_DP)
            isClickable = fullDate != null
            isFocusable = fullDate != null
        }
    }

    private fun buildStudentCell(ctx: android.content.Context, fullName: String): TextView {
        return TextView(ctx).apply {
            text = shortName(fullName)
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 12f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            maxLines = 2
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_name)
            layoutParams = journalCellLayout(JOURNAL_COL_NAME_DP)
        }
    }

    private fun buildNumberCell(ctx: android.content.Context, number: Int): TextView {
        return TextView(ctx).apply {
            text = number.toString()
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_name)
            layoutParams = journalCellLayout(JOURNAL_COL_NUMBER_DP)
            maxLines = 1
        }
    }

    private fun buildGradeCell(ctx: android.content.Context, grade: Grade?): TextView {
        return TextView(ctx).apply {
            val type = grade?.type?.takeIf { JournalColumnLabel.isLessonType(it) }
            text = when {
                grade == null -> "—"
                grade.value == Grade.VALUE_ABSENCE -> if (type != null) "$type Н" else "Н"
                type != null -> "$type ${grade.value}"
                else -> grade.value.toString()
            }
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 12f
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_journal_cell_grade)
            layoutParams = journalCellLayout(JOURNAL_COL_GRADE_DP)
        }
    }

    private fun displayStudentName(fullName: String): String =
        fullName.replace(Regex("\\s*\\(Староста\\)\\s*", RegexOption.IGNORE_CASE), " ").trim()

    private fun shortName(fullName: String): String {
        val clean = displayStudentName(fullName)
        val parts = clean.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 3 -> "${parts[0]} ${parts[1].first()}. ${parts[2].first()}."
            parts.size == 2 -> "${parts[0]} ${parts[1].first()}."
            else -> clean
        }
    }

    private fun showEmpty(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        emptyText.text = message
        journalScroll.visibility = View.GONE
    }

    private fun hideEmpty() {
        emptyStateLayout.visibility = View.GONE
        journalScroll.visibility = View.VISIBLE
    }

    private fun parseDate(value: String): Date? {
        return try {
            dateFormat.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showAddSubjectDialog() {
        val input = MaterialAutoCompleteTextView(requireContext()).apply {
            hint = "Название предмета"
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить предмет")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val subject = input.text?.toString().orEmpty().trim()
                if (subject.isBlank()) {
                    Toast.makeText(requireContext(), "Введите название предмета", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveSubject(subject)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun semestersForSelectedSubject(): List<JournalSemester> {
        val subject = catalogSubjects.firstOrNull {
            it.name.equals(selectedSubject.trim(), ignoreCase = true)
        } ?: return semesters
        val linkedNames = subject.semesterNames
        if (linkedNames.isEmpty()) return emptyList()
        return semesters.filter { sem ->
            linkedNames.any { it.equals(sem.name, ignoreCase = true) }
        }
    }

    private fun setupSemesterDropdown() {
        val availableSemesters = semestersForSelectedSubject()
        val semesterNames = availableSemesters.map { it.name }
        semesterDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, semesterNames)
        )
        semesterDropdown.setOnClickListener { semesterDropdown.showDropDown() }
        semesterDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position !in semesterNames.indices) return@setOnItemClickListener
            selectedSemesterName = semesterNames[position]
            isSummaryMode = false
            val selected = availableSemesters.getOrNull(position)
                ?: getSelectedSemester()
            if (selected != null && !isWeekInsideSemester(selectedWeekStart, selected)) {
                selectedWeekStart = mondayOfDate(selected.startDate)
            }
            refreshJournal()
        }

        if (semesterNames.isNotEmpty()) {
            val semesterForToday = getSemesterForCurrentDate()
            val preferred = semestersForSelectedSubject().firstOrNull { it.name == semesterForToday?.name }?.name
            selectedSemesterName = when {
                preferred != null -> preferred
                semesterNames.contains(selectedSemesterName) -> selectedSemesterName
                else -> semesterNames.first()
            }
            semesterDropdown.setText(selectedSemesterName, false)
        } else {
            selectedSemesterName = ""
            semesterDropdown.setText("", false)
            if (selectedSubject.isNotBlank()) {
                Toast.makeText(
                    requireContext(),
                    "К предмету не привязаны семестры. Назначьте их в учебном справочнике.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveSubject(subject: String) {
        val teacherId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val docId = subjectDocId(teacherId, groupName)
            var success = false
            try {
                db.collection("journal_subjects")
                    .document(docId)
                    .set(
                        mapOf(
                            "teacherId" to teacherId,
                            "groupName" to groupName,
                            "subjects" to (manualSubjects + subject).distinct().sorted()
                        )
                    )
                    .await()
                success = true
                manualSubjects = (manualSubjects + subject).distinct().sorted().toMutableList()
            } catch (_: Exception) {
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (success) {
                    val mergedSubjects = (allTeacherGrades.map { it.subject } + manualSubjects)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    subjectDropdown.setAdapter(
                        ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            mergedSubjects
                        )
                    )
                    selectedSubject = subject
                    subjectDropdown.setText(subject, false)
                    Toast.makeText(requireContext(), "Предмет добавлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось добавить предмет", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadSavedSubjects(teacherId: String): List<String> {
        return try {
            val doc = db.collection("journal_subjects")
                .document(subjectDocId(teacherId, groupName))
                .get()
                .await()
            (doc.get("subjects") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun loadSavedSemesters(teacherId: String): List<JournalSemester> {
        return try {
            val doc = db.collection("journal_semesters")
                .document(subjectDocId(teacherId, groupName))
                .get()
                .await()
            (doc.get("semesters") as? List<*>)?.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val name = map["name"] as? String ?: return@mapNotNull null
                val start = map["startDate"] as? String ?: return@mapNotNull null
                val end = map["endDate"] as? String ?: return@mapNotNull null
                JournalSemester(name, start, end)
            }?.sortedBy { parseDate(it.startDate)?.time ?: Long.MAX_VALUE } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun subjectDocId(teacherId: String, group: String): String {
        val safeGroup = group.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .trim('_')
            .ifBlank { "group" }
        return "${teacherId}_$safeGroup"
    }

    private fun averageGradeOrNull(grades: List<Grade>): Int? {
        val withoutAbsence = grades.filter { it.value != Grade.VALUE_ABSENCE }
        if (withoutAbsence.isEmpty()) return null
        val avg = withoutAbsence.map { it.value }.average()
        return avg.roundToInt()
    }

    private fun getSelectedSemester(): JournalSemester? {
        if (selectedSemesterName.isBlank()) return null
        return semesters.firstOrNull { it.name == selectedSemesterName }
    }

    private fun shiftWeek(days: Int) {
        val sem = getSelectedSemester()

        if (isSummaryMode && days < 0) {
            isSummaryMode = false
            if (sem != null) {
                selectedWeekStart = mondayOfDate(sem.endDate)
            }
            refreshJournal()
            return
        }

        if (isSummaryMode && days > 0) {
            Toast.makeText(requireContext(), "Это последняя страница семестра", Toast.LENGTH_SHORT).show()
            return
        }

        val candidate = (selectedWeekStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, days)
        }

        if (sem != null && !isWeekInsideSemester(candidate, sem)) {
            if (days > 0 && isLastWeekOfSemester(selectedWeekStart, sem)) {
                isSummaryMode = true
                refreshJournal()
            } else {
                Toast.makeText(requireContext(), "Выбранная неделя вне границ семестра", Toast.LENGTH_SHORT).show()
            }
            return
        }

        selectedWeekStart = candidate
        refreshJournal()
    }

    private fun isWeekInsideSemester(weekStart: Calendar, semester: JournalSemester): Boolean {
        val semStart = parseDate(semester.startDate) ?: return true
        val semEnd = parseDate(semester.endDate) ?: return true
        val weekStartDate = weekStart.time
        val weekEndDate = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 4) }.time
        return !weekEndDate.before(semStart) && !weekStartDate.after(semEnd)
    }

    private fun isDateInSemester(date: Calendar, semester: JournalSemester): Boolean {
        val start = parseDate(semester.startDate) ?: return false
        val end = parseDate(semester.endDate) ?: return false
        val t = date.time
        return !t.before(start) && !t.after(end)
    }

    private fun getSemesterForCurrentDate(): JournalSemester? {
        val today = Calendar.getInstance()
        return semesters.firstOrNull { isDateInSemester(today, it) }
    }

    private fun mondayOfDate(rawDate: String): Calendar {
        val parsed = parseDate(rawDate) ?: return mondayOfCurrentWeek()
        val cal = Calendar.getInstance().apply { time = parsed }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val shift = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
        cal.add(Calendar.DAY_OF_MONTH, shift)
        return cal
    }

    private fun showColumnTypeDialog(fullDate: String) {
        val subject = subjectDropdown.text?.toString().orEmpty().trim()
        if (subject.isBlank()) {
            Toast.makeText(requireContext(), "Выберите предмет", Toast.LENGTH_SHORT).show()
            return
        }
        val teacherId = auth.currentUser?.uid ?: return

        val typeOptions = arrayOf(
            JournalColumnLabel.TYPE_CONTROL,
            JournalColumnLabel.TYPE_LAB,
            JournalColumnLabel.TYPE_PRACTICE,
            "Убрать метку"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Тип занятия — $fullDate")
            .setItems(typeOptions) { _, whichType ->
                val action = typeOptions[whichType]
                progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = if (action == "Убрать метку") {
                        withContext(Dispatchers.IO) {
                            journalColumnLabelRepository.clearLabel(teacherId, groupName, subject, fullDate)
                        }
                    } else {
                        withContext(Dispatchers.IO) {
                            journalColumnLabelRepository.setLabel(teacherId, groupName, subject, fullDate, action)
                        }
                    }
                    if (ok) {
                        columnLabels = withContext(Dispatchers.IO) {
                            journalColumnLabelRepository.getLabelsForWeek(
                                teacherId, groupName, subject, currentWeekDates.map { it.first }
                            )
                        }
                    }
                    progressBar.visibility = View.GONE
                    if (!isAdded || view == null) return@launch
                    if (ok) {
                        buildJournalTable(currentWeekDates, currentGradesByCell)
                        Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showExportDialog() {
        if (students.isEmpty()) {
            Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
            return
        }
        val teacherId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val freshGrades = withContext(Dispatchers.IO) {
                gradeRepository.getTeacherGrades(teacherId)
            }
            allTeacherGrades = freshGrades
            progressBar.visibility = View.GONE
            showExportDialogWithFilters()
        }
    }

    private fun showExportDialogWithFilters() {
        val filterView = layoutInflater.inflate(R.layout.dialog_export_filter, null)
        val radioSelectedStudent = filterView.findViewById<RadioButton>(R.id.radioSelectedStudent)
        val studentDropdownLayout = filterView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.studentDropdownLayout)
        val studentDropdown = filterView.findViewById<AutoCompleteTextView>(R.id.studentDropdown)
        val subjectDropdownFilter = filterView.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)
        val semesterDropdownFilter = filterView.findViewById<AutoCompleteTextView>(R.id.semesterDropdown)

        val studentNames = students
            .sortedBy { displayStudentName(it.fullName).lowercase(Locale.getDefault()) }
            .map { displayStudentName(it.fullName) }
        val studentAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        studentDropdown.setAdapter(studentAdapter)
        studentDropdown.setOnClickListener { studentDropdown.showDropDown() }

        val subjectOptions = (allTeacherGrades.map { it.subject.trim() }.filter { it.isNotBlank() } + manualSubjects + listOf(selectedSubject.trim()).filter { it.isNotBlank() })
            .distinct().sorted()
        if (subjectOptions.isEmpty()) {
            Toast.makeText(requireContext(), "Нет предметов для отчёта", Toast.LENGTH_SHORT).show()
            return
        }
        val subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectOptions)
        subjectDropdownFilter.setAdapter(subjectAdapter)
        subjectDropdownFilter.setOnClickListener { subjectDropdownFilter.showDropDown() }
        val defaultSubject = when {
            selectedSubject.isNotBlank() && subjectOptions.any { it.equals(selectedSubject, ignoreCase = true) } -> selectedSubject
            else -> subjectOptions.first()
        }
        subjectDropdownFilter.setText(defaultSubject, false)

        val semesterOptions = semesters.map { it.name }
        if (semesterOptions.isEmpty()) {
            Toast.makeText(requireContext(), "Нет семестров для отчёта", Toast.LENGTH_SHORT).show()
            return
        }
        val semesterAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, semesterOptions)
        semesterDropdownFilter.setAdapter(semesterAdapter)
        semesterDropdownFilter.setOnClickListener { semesterDropdownFilter.showDropDown() }
        val defaultSemester = when {
            selectedSemesterName.isNotBlank() && semesterOptions.any { it == selectedSemesterName } -> selectedSemesterName
            else -> semesterOptions.first()
        }
        semesterDropdownFilter.setText(defaultSemester, false)

        radioSelectedStudent.setOnCheckedChangeListener { _, isChecked ->
            studentDropdownLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Параметры отчёта")
            .setView(filterView)
            .setPositiveButton("Сформировать PDF") { _, _ ->
                val studentScope = if (filterView.findViewById<RadioButton>(R.id.radioAllStudents).isChecked) {
                    StudentScope.ALL
                } else {
                    StudentScope.SELECTED
                }
                val selectedStudentId = when (studentScope) {
                    StudentScope.ALL -> null
                    StudentScope.SELECTED -> {
                        val name = studentDropdown.text?.toString()?.trim()
                        if (name.isNullOrBlank()) {
                            Toast.makeText(requireContext(), "Выберите учащегося", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        students.find {
                            displayStudentName(it.fullName).equals(name, ignoreCase = true)
                        }?.id
                            ?: run {
                                Toast.makeText(requireContext(), "Учащийся не найден", Toast.LENGTH_SHORT).show()
                                return@setPositiveButton
                            }
                    }
                }
                val subjectFilter = subjectDropdownFilter.text?.toString()?.trim()
                if (subjectFilter.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Выберите предмет", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val semesterFilter = semesterDropdownFilter.text?.toString()?.trim()
                if (semesterFilter.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Выберите семестр", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                exportReport(
                    ExportPeriod.SEMESTER,
                    semesterFilter,
                    subjectFilter,
                    studentScope,
                    selectedStudentId
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun exportReport(
        period: ExportPeriod,
        selectedSemesterNameForExport: String?,
        subjectForExport: String?,
        studentScope: StudentScope,
        selectedStudentId: String?
    ) {
        val studentIds = when (studentScope) {
            StudentScope.ALL -> students.map { it.id }.toSet()
            StudentScope.SELECTED -> {
                val id = selectedStudentId ?: run {
                    Toast.makeText(requireContext(), "Выберите учащегося", Toast.LENGTH_SHORT).show()
                    return
                }
                setOf(id)
            }
        }
        var filtered = allTeacherGrades.filter { it.studentId in studentIds }
        var allForFinal = allTeacherGrades.filter { it.studentId in studentIds }

        val periodLabel = when (period) {
            ExportPeriod.SEMESTER -> {
                val semName = selectedSemesterNameForExport?.takeIf { it.isNotBlank() }
                val sem = semName?.let { name -> semesters.firstOrNull { it.name == name } }
                if (sem == null) {
                    Toast.makeText(requireContext(), "Выберите семестр из списка", Toast.LENGTH_SHORT).show()
                    return
                }
                val start = parseDate(sem.startDate) ?: run {
                    Toast.makeText(requireContext(), "Некорректная дата начала семестра", Toast.LENGTH_SHORT).show()
                    return
                }
                val end = parseDate(sem.endDate) ?: run {
                    Toast.makeText(requireContext(), "Некорректная дата конца семестра", Toast.LENGTH_SHORT).show()
                    return
                }
                filtered = filtered.filter { grade ->
                    val date = parseDate(grade.date) ?: return@filter false
                    !date.before(start) && !date.after(end)
                }
                allForFinal = allForFinal.filter { grade ->
                    val date = parseDate(grade.date) ?: return@filter false
                    !date.before(start) && !date.after(end)
                }
                sem.name
            }
            ExportPeriod.ALL_TIME -> "Все время"
        }

        val subj = subjectForExport?.trim().orEmpty()
        if (subj.isBlank()) {
            Toast.makeText(requireContext(), "Выберите предмет", Toast.LENGTH_SHORT).show()
            return
        }
        filtered = filtered.filter { it.subject.trim().equals(subj, ignoreCase = true) }
        allForFinal = allForFinal.filter { it.subject.trim().equals(subj, ignoreCase = true) }
        val subjectLabel = subj

        filtered = filtered.filter { !it.isAbsence() }
        allForFinal = allForFinal.filter { !it.isAbsence() }

        if (filtered.isEmpty()) {
            Toast.makeText(requireContext(), "Нет отметок для выбранных параметров", Toast.LENGTH_SHORT).show()
            return
        }

        filtered = filtered.sortedWith(
            compareBy<Grade> { it.subject.lowercase(Locale.getDefault()) }
                .thenBy { it.studentName.lowercase(Locale.getDefault()) }
                .thenByDescending { it.createdAt }
        )
        allForFinal = allForFinal.sortedWith(
            compareBy<Grade> { it.subject.lowercase(Locale.getDefault()) }
                .thenBy { it.studentName.lowercase(Locale.getDefault()) }
                .thenByDescending { it.createdAt }
        )

        val teacherName = auth.currentUser?.displayName
            ?: auth.currentUser?.email
            ?: filtered.firstOrNull()?.teacherName
            ?: "Преподаватель"

        exportToPdf(filtered, allForFinal, periodLabel, subjectLabel, teacherName)
    }

    private fun exportToPdf(
        grades: List<Grade>,
        allGradesForFinal: List<Grade>,
        periodLabel: String,
        subjectLabel: String,
        teacherName: String
    ) {
        try {
            val forAvg = grades.filter { it.value != Grade.VALUE_ABSENCE }
            val average = if (forAvg.isEmpty()) 0.0 else forAvg.map { it.value }.average()
            val subjectsCount = grades.map { it.subject }.distinct().size
            val studentsCount = grades.map { it.studentId }.distinct().size
            val title = "Сведения успеваемости группы $groupName"
            val pdf = StatisticsExporter.createTeacherGradesReportPdfDocument(
                title = title,
                groupName = groupName,
                periodLabel = periodLabel,
                subjectLabel = subjectLabel,
                grades = grades,
                allGradesForFinal = allGradesForFinal,
                averageGrade = average,
                studentsCount = studentsCount,
                subjectsCount = subjectsCount,
                teacherName = teacherName
            )
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileName = "journal_${groupName}_$timestamp".replace(".", "_")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }
            val uri = requireContext().contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            if (uri == null) {
                pdf.close()
                Toast.makeText(requireContext(), "Не удалось создать PDF", Toast.LENGTH_SHORT).show()
                return
            }
            requireContext().contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            pdf.close()
            Toast.makeText(requireContext(), "PDF сохранен в Документы", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка экспорта PDF", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_GROUP_NAME = "group_name"
        private const val JOURNAL_COL_NUMBER_DP = 56
        private const val JOURNAL_COL_NAME_DP = 132
        private const val JOURNAL_COL_GRADE_DP = 92
        private const val JOURNAL_COL_SUMMARY_DP = 72
        private const val JOURNAL_SUMMARY_NAME_DP = 200
        private const val JOURNAL_SUMMARY_TOTAL_DP = 104
        private const val JOURNAL_ROW_HEIGHT_DP = 44

        fun newInstance(groupName: String): TeacherGradeJournalFragment {
            return TeacherGradeJournalFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_NAME, groupName)
                }
            }
        }

        private fun mondayOfCurrentWeek(): Calendar {
            val now = Calendar.getInstance()
            val day = now.get(Calendar.DAY_OF_WEEK)
            val shift = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
            now.add(Calendar.DAY_OF_MONTH, shift)
            return now
        }
    }

    private data class JournalSemester(
        val name: String,
        val startDate: String,
        val endDate: String
    )

    private enum class ExportPeriod { SEMESTER, ALL_TIME }
    private enum class SubjectScope { CURRENT, ALL }
    private enum class StudentScope { ALL, SELECTED }
}
