package com.sakovich.collegeapp.presentation.grades

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.google.android.material.button.MaterialButton
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.DrawableUtils
import com.sakovich.collegeapp.utils.StatisticsExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class GradesFragment : Fragment() {

    private lateinit var gradesRecyclerView: RecyclerView
    private lateinit var averageGradeValue: TextView
    private lateinit var totalGradesValue: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var emptyHintText: TextView
    private lateinit var subjectFilterLayout: TextInputLayout
    private lateinit var subjectFilterDropdown: AutoCompleteTextView
    private lateinit var semesterFilterLayout: TextInputLayout
    private lateinit var semesterFilterDropdown: AutoCompleteTextView
    private lateinit var exportButton: MaterialButton
    private lateinit var gradesBackButton: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var gradeRepository: GradeRepository
    private lateinit var userRepository: UserRepository
    private lateinit var gradesAdapter: GradesAdapter

    private val gradesList = mutableListOf<Grade>()
    private var currentSubjectFilter: String? = null

    private var currentSemesterFilter: Int? = null

    private var catalogTeacherBySubject: Map<String, String> = emptyMap()

    private var semesterRanges: Map<Int, Pair<Date, Date>> = emptyMap()

    private val semesterOptionLabels = listOf(
        "Все семестры", "1 семестр", "2 семестр", "3 семестр", "4 семестр",
        "5 семестр", "6 семестр", "7 семестр", "8 семестр"
    )

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                savePdfToUri(uri)
            }
        } else {
            progressBar.visibility = View.GONE
            exportButton.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_grades, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        gradeRepository = GradeRepository()
        userRepository = UserRepository()

        initViews(view)
        setupBackButton()
        setupRecyclerView()
        setupSubjectFilter()
        setupSemesterFilter()
        setupExportButton()
        loadGrades()
    }

    private fun setupBackButton() {
        gradesBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        loadGrades()
    }

    private fun initViews(view: View) {
        gradesRecyclerView = view.findViewById(R.id.gradesRecyclerView)
        averageGradeValue = view.findViewById(R.id.averageGradeValue)
        totalGradesValue = view.findViewById(R.id.totalGradesValue)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        emptyHintText = view.findViewById(R.id.emptyHintText)
        subjectFilterLayout = view.findViewById(R.id.subjectFilterLayout)
        subjectFilterDropdown = view.findViewById(R.id.subjectFilterDropdown)
        semesterFilterLayout = view.findViewById(R.id.semesterFilterLayout)
        semesterFilterDropdown = view.findViewById(R.id.semesterFilterDropdown)
        exportButton = view.findViewById(R.id.exportButton)
        gradesBackButton = view.findViewById(R.id.gradesBackButton)
    }

    private fun setupRecyclerView() {
        gradesAdapter = GradesAdapter(
            grades = gradesList,
            canEdit = false,
            onGradeClick = { grade -> showGradeDetails(grade) },
            onGradeLongClick = null
        )
        gradesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        gradesRecyclerView.adapter = gradesAdapter
    }

    private suspend fun buildCatalogTeacherMap(grades: List<Grade>, userId: String): Map<String, String> {
        val user = userRepository.getUser(userId) ?: return emptyMap()
        val groupId = user.groupId.takeIf { it.isNotBlank() }
            ?: GroupRepository.groupNameToDocumentId(user.groupName.ifBlank { user.group })
        if (groupId.isBlank()) return emptyMap()
        val adminRepo = AdminRepository()
        val subjects = grades.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct()
        return subjects.associateWith { subject ->
            adminRepo.getCatalogTeacherNamesForSubject(subject, groupId).joinToString(", ")
        }
    }

    private suspend fun loadSemesterRanges(userId: String): Map<Int, Pair<Date, Date>> {
        val user = userRepository.getUser(userId) ?: return emptyMap()
        val groupId = user.groupId.takeIf { it.isNotBlank() }
            ?: GroupRepository.groupNameToDocumentId(user.groupName.ifBlank { user.group })
        if (groupId.isBlank()) return emptyMap()
        val templates = AdminRepository().getSemestersForGroup(groupId)
        return templates.mapNotNull { sem ->
            val num = Regex("(\\d+)").find(sem.name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val start = parseDateSafe(sem.startDate) ?: return@mapNotNull null
            val end = parseDateSafe(sem.endDate) ?: return@mapNotNull null
            num to (start to end)
        }.toMap()
    }

    private fun parseDateSafe(value: String): Date? {
        if (value.isBlank()) return null
        return try {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeSemester(grade: Grade): Grade {
        val date = parseDateSafe(grade.date)
        if (date != null && semesterRanges.isNotEmpty()) {
            val semByDate = semesterRanges.entries.firstOrNull { (_, range) ->
                !date.before(range.first) && !date.after(range.second)
            }?.key
            if (semByDate != null && semByDate != grade.semester) {
                return grade.copy(semester = semByDate)
            }
        }
        return grade
    }

    private fun loadGrades() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState("Войдите в аккаунт для просмотра отметок")
            return
        }
        if (!isAdded) return

        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        gradesRecyclerView.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val grades = withContext(Dispatchers.IO) {
                    gradeRepository.getStudentGrades(currentUser.uid)
                }
                if (!isAdded) return@launch

                semesterRanges = withContext(Dispatchers.IO) {
                    loadSemesterRanges(currentUser.uid)
                }
                if (!isAdded) return@launch

                val gradesWithoutAbsences = grades
                    .filter { isVisibleGrade(it) }
                    .map { normalizeSemester(it) }
                val catalogMap = withContext(Dispatchers.IO) {
                    buildCatalogTeacherMap(gradesWithoutAbsences, currentUser.uid)
                }
                if (!isAdded) return@launch

                progressBar.visibility = View.GONE
                gradesList.clear()
                gradesList.addAll(gradesWithoutAbsences)
                catalogTeacherBySubject = catalogMap
                setupSubjectFilter()
                setupSemesterFilter()
                filterGrades()
            } catch (e: Exception) {
                if (!isAdded) return@launch
                progressBar.visibility = View.GONE
                showEmptyState("Ошибка загрузки отметок")
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

    private fun filterGrades() {
        var filtered = gradesList.toList()

        if (currentSubjectFilter != null) {
            filtered = filtered.filter { it.subject.equals(currentSubjectFilter, ignoreCase = true) }
        }

        if (currentSemesterFilter != null) {
            filtered = filtered.filter { it.semester == currentSemesterFilter }
        }

        gradesAdapter.updateGrades(filtered)
        updateStatistics(filtered)

        if (filtered.isEmpty()) {
            if (gradesList.isEmpty()) {
                showEmptyState("У вас пока нет отметок")
            } else {
                showEmptyState("Нет отметок по выбранным фильтрам")
            }
        } else {
            hideEmptyState()
        }
    }

    private fun setupSubjectFilter() {
        val subjects = gradesList.map { it.subject }.distinct().sorted()
        val subjectsWithAll = mutableListOf("Все предметы")
        subjectsWithAll.addAll(subjects)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            subjectsWithAll
        )
        subjectFilterDropdown.setAdapter(adapter)

        subjectFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentSubjectFilter = if (position == 0) null else subjects[position - 1]
            subjectFilterDropdown.setText(subjectsWithAll[position], false)
            filterGrades()
        }
    }

    private fun setupSemesterFilter() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            semesterOptionLabels
        )
        semesterFilterDropdown.setAdapter(adapter)

        val idx = when (val s = currentSemesterFilter) {
            null -> 0
            in 1..8 -> s
            else -> 0
        }
        if (idx == 0) currentSemesterFilter = null
        semesterFilterDropdown.setText(semesterOptionLabels[idx], false)

        semesterFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentSemesterFilter = if (position == 0) null else position
            semesterFilterDropdown.setText(semesterOptionLabels[position], false)
            filterGrades()
        }
    }

    private fun showGradeDetails(grade: Grade) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_grade_details, null)
        val subjectTv = dialogView.findViewById<TextView>(R.id.detailSubject)
        val statusBadge = dialogView.findViewById<TextView>(R.id.detailStatusBadge)
        val dateTv = dialogView.findViewById<TextView>(R.id.detailDate)
        val typeTv = dialogView.findViewById<TextView>(R.id.detailType)
        val scoreTv = dialogView.findViewById<TextView>(R.id.detailScore)
        val scoreIcon = dialogView.findViewById<TextView>(R.id.detailScoreIcon)
        val teacherTv = dialogView.findViewById<TextView>(R.id.detailTeacher)
        val commentBlock = dialogView.findViewById<View>(R.id.detailCommentBlock)
        val commentTv = dialogView.findViewById<TextView>(R.id.detailComment)

        subjectTv.text = grade.subject

        val catalogTeacher = catalogTeacherBySubject[grade.subject]?.takeIf { it.isNotBlank() }
        val teacherDisplay = catalogTeacher ?: grade.teacherName.ifBlank { "—" }
        teacherTv.text = teacherDisplay

        val gradeColor = if (grade.isAbsence()) {
            "#64748B"
        } else when {
            grade.value >= 9 -> "#10B981"
            grade.value >= 7 -> "#A78BFA"
            grade.value >= 5 -> "#F59E0B"
            grade.value >= 3 -> "#F97316"
            else -> "#EF4444"
        }
        DrawableUtils.setViewBackgroundColorHex(statusBadge, gradeColor)

        if (grade.isAbsence()) {
            statusBadge.text = "Н (неявка)"
            scoreTv.text = "Н"
            scoreIcon.text = "📋"
            scoreIcon.setBackgroundResource(R.drawable.bg_icon_gray)
        } else {
            statusBadge.text = "Отметка: ${grade.value}"
            scoreTv.text = grade.value.toString()
            val (emoji, iconBg) = when {
                grade.value >= 9 -> "🌟" to R.drawable.bg_icon_green
                grade.value >= 7 -> "✨" to R.drawable.bg_icon_purple
                grade.value >= 5 -> "📝" to R.drawable.bg_icon_orange
                grade.value >= 3 -> "⚠️" to R.drawable.bg_icon_orange
                else -> "❌" to R.drawable.bg_icon_red
            }
            scoreIcon.text = emoji
            scoreIcon.setBackgroundResource(iconBg)
        }
        scoreTv.setTextColor(Color.parseColor(gradeColor))

        dateTv.text = grade.date
        typeTv.text = "Тип: ${grade.typeDisplayLabel()}"

        val cmt = grade.comment?.trim().orEmpty()
        if (cmt.isNotEmpty()) {
            commentBlock.visibility = View.VISIBLE
            commentTv.visibility = View.VISIBLE
            commentTv.text = cmt
        } else {
            commentBlock.visibility = View.GONE
            commentTv.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateStatistics(filtered: List<Grade>) {
        val forAvg = filtered.filter { !it.isAbsence() }
        if (filtered.isEmpty()) {
            averageGradeValue.text = "—"
            averageGradeValue.setTextColor(Color.parseColor("#94A3B8"))
            totalGradesValue.text = "0"
            return
        }
        val average = if (forAvg.isEmpty()) null else forAvg.map { it.value.toDouble() }.average()
        averageGradeValue.text = if (average == null) "—" else "%.1f".format(average)
        if (average == null) {
            averageGradeValue.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            val avgColor = when {
                average >= 9.0 -> "#10B981"
                average >= 7.0 -> "#A78BFA"
                average >= 5.0 -> "#F59E0B"
                average >= 3.0 -> "#F97316"
                else -> "#EF4444"
            }
            averageGradeValue.setTextColor(Color.parseColor(avgColor))
        }
        totalGradesValue.text = filtered.size.toString()
    }

    private fun showEmptyState(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        emptyText.text = message
        val hideHint = message.contains("фильтрам", ignoreCase = true) ||
            message.contains("Войдите", ignoreCase = true) ||
            message.contains("Ошибка", ignoreCase = true)
        emptyHintText.visibility = if (hideHint) View.GONE else View.VISIBLE
        gradesRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
        gradesRecyclerView.visibility = View.VISIBLE
    }

    private fun setupExportButton() {
        exportButton.setOnClickListener {
            if (gradesList.isEmpty()) {
                Toast.makeText(requireContext(), "Нет отметок для экспорта", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            exportToPdf()
        }
    }

    private fun exportToPdf() {
        val filteredGrades = gradesAdapter.getCurrentGrades()
        if (filteredGrades.isEmpty()) {
            Toast.makeText(requireContext(), "Нет отметок для экспорта", Toast.LENGTH_SHORT).show()
            return
        }

        val forAvg = filteredGrades.filter { !it.isAbsence() }
        val average = if (forAvg.isNotEmpty()) {
            forAvg.map { it.value.toDouble() }.average()
        } else 0.0

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Мои_отметки_${dateFormat.format(Date())}.pdf"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            savePdfLauncher.launch(intent)
            pendingPdfData = GradesPdfData(filteredGrades, average)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private data class GradesPdfData(
        val grades: List<Grade>,
        val averageGrade: Double
    )

    private var pendingPdfData: GradesPdfData? = null

    private fun savePdfToUri(uri: Uri) {
        val data = pendingPdfData ?: return
        pendingPdfData = null
        if (!isAdded) return

        val appContext = requireContext().applicationContext
        progressBar.visibility = View.VISIBLE
        exportButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfDocument = withContext(Dispatchers.IO) {
                    StatisticsExporter.createMyGradesPdfDocument(
                        grades = data.grades,
                        averageGrade = data.averageGrade
                    )
                }
                val success = withContext(Dispatchers.IO) {
                    StatisticsExporter.savePdfToUri(appContext, pdfDocument, uri)
                }
                if (!isAdded) return@launch

                progressBar.visibility = View.GONE
                exportButton.isEnabled = true
                val message = if (success) {
                    "✅ PDF отчёт успешно сохранён!"
                } else {
                    "❌ Ошибка сохранения PDF отчёта"
                }
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (!isAdded) return@launch
                progressBar.visibility = View.GONE
                exportButton.isEnabled = true
                Toast.makeText(appContext, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
