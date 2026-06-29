package com.sakovich.collegeapp.presentation.statistics

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.data.models.StudentStatistics
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.StatisticsExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TeacherStatisticsFragment : Fragment() {

    private lateinit var semesterFilterLayout: TextInputLayout
    private lateinit var semesterFilterDropdown: AutoCompleteTextView
    private lateinit var statisticsTitleText: TextView
    private lateinit var countText: TextView
    private lateinit var countLabelText: TextView
    private lateinit var averageGradeText: TextView
    private lateinit var totalGradesText: TextView
    private lateinit var teacherStatisticsBackButton: ImageButton
    private lateinit var exportButton: Button
    private lateinit var dataLabelText: TextView
    private lateinit var dataRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var gradeRepository: GradeRepository

    private lateinit var statisticsAdapter: StatisticsAdapter

    private var currentGroupName: String = ""
    private var currentSemester: Int? = null
    private var displayedStatistics = mutableListOf<StudentStatistics>()

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
        return inflater.inflate(R.layout.fragment_teacher_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()
        groupRepository = GroupRepository()
        gradeRepository = GradeRepository()

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadStatistics()
    }

    private fun initViews(view: View) {
        semesterFilterLayout = view.findViewById(R.id.semesterFilterLayout)
        semesterFilterDropdown = view.findViewById(R.id.semesterFilterDropdown)
        statisticsTitleText = view.findViewById(R.id.statisticsTitleText)
        countText = view.findViewById(R.id.countText)
        countLabelText = view.findViewById(R.id.countLabelText)
        averageGradeText = view.findViewById(R.id.averageGradeText)
        totalGradesText = view.findViewById(R.id.totalGradesText)
        teacherStatisticsBackButton = view.findViewById(R.id.teacherStatisticsBackButton)
        exportButton = view.findViewById(R.id.exportButton)
        dataLabelText = view.findViewById(R.id.dataLabelText)
        dataRecyclerView = view.findViewById(R.id.dataRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyText)

        setupSemesterDropdown()

        teacherStatisticsBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSemesterDropdown() {
        val semesters = listOf("Все семестры", "1 семестр", "2 семестр", "3 семестр", "4 семестр", "5 семестр", "6 семестр", "7 семестр", "8 семестр")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, semesters)
        semesterFilterDropdown.setAdapter(adapter)
        semesterFilterDropdown.setText("Все семестры", false)

        semesterFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentSemester = if (position == 0) null else position
            loadStatistics()
        }
    }

    private fun setupRecyclerView() {
        statisticsAdapter = StatisticsAdapter(
            students = displayedStatistics,
            showGroup = false,
            onItemClick = { student ->
                showStudentDetails(student)
            }
        )
        dataRecyclerView.setupStatisticsListInScrollView()
        dataRecyclerView.adapter = statisticsAdapter
    }

    private fun setupClickListeners() {
        exportButton.setOnClickListener {
            showExportDialog()
        }
    }

    private fun loadStatistics() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("Пользователь не авторизован")
            return
        }

        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {

                val user = userRepository.getUser(currentUser.uid)
                if (user == null) {
                    withContext(Dispatchers.Main) {
                        showError("Не удалось загрузить данные пользователя")
                    }
                    return@launch
                }

                currentGroupName = user.groupName.ifEmpty { user.group }

                val students = groupRepository.getStudentsByGroup(currentGroupName)
                val statistics = mutableListOf<StudentStatistics>()

                for (student in students) {
                    val allGrades = gradeRepository.getStudentGrades(student.id)

                    val grades = if (currentSemester != null) {
                        allGrades.filter { it.semester == currentSemester }
                    } else {
                        allGrades
                    }
                    val forAvg = grades.filter { it.value != Grade.VALUE_ABSENCE }
                    val avgGrade = if (forAvg.isNotEmpty()) forAvg.map { it.value }.average() else 0.0

                    statistics.add(
                        StudentStatistics(
                            studentId = student.id,
                            studentName = student.fullName,
                            groupName = currentGroupName,
                            gradesCount = grades.size,
                            averageGrade = avgGrade,
                            grades = grades
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    displayedStatistics.clear()
                    displayedStatistics.addAll(statistics)
                    statisticsAdapter.updateData(statistics)
                    dataRecyclerView.post { dataRecyclerView.updateStatisticsListHeight() }
                    updateStatisticsUI(statistics)

                    if (statistics.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "В группе нет учащихся"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showError("Ошибка загрузки: ${e.message}")
                }
            }
        }
    }

    private fun updateStatisticsUI(statistics: List<StudentStatistics>) {
        val totalGrades = statistics.sumOf { it.gradesCount }
        val studentsWithGrades = statistics.filter { it.gradesCount > 0 }
        val avgGrade = if (studentsWithGrades.isNotEmpty()) {
            studentsWithGrades.map { it.averageGrade }.average()
        } else 0.0

        val semesterText = if (currentSemester != null) " ($currentSemester сем.)" else ""
        statisticsTitleText.text = "📈 Статистика $currentGroupName$semesterText"

        countText.text = statistics.size.toString()
        countLabelText.text = "Учащихся"

        averageGradeText.text = if (avgGrade > 0) "%.1f".format(avgGrade) else "—"
        totalGradesText.text = totalGrades.toString()

        dataLabelText.text = "👨‍🎓 Рейтинг учащихся"
    }

    private fun showExportDialog() {
        exportToPdf()
    }

    private fun exportToPdf() {
        if (displayedStatistics.isEmpty()) {
            Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
            return
        }

        val title = "Статистика группы $currentGroupName"

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Статистика_${currentGroupName}_${dateFormat.format(Date())}.pdf"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            savePdfLauncher.launch(intent)

            pendingPdfData = PdfData(title, displayedStatistics, false, currentGroupName, null, null)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private data class PdfData(
        val title: String,
        val students: List<StudentStatistics>,
        val showGroup: Boolean,
        val groupName: String?,
        val averageGrade: Double?,
        val totalGrades: Int?
    )

    private var pendingPdfData: PdfData? = null

    private fun savePdfToUri(uri: Uri) {
        val data = pendingPdfData ?: return
        pendingPdfData = null

        progressBar.visibility = View.VISIBLE
        exportButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfDocument = StatisticsExporter.createPdfDocument(
                    title = data.title,
                    students = data.students,
                    showGroup = data.showGroup,
                    groupName = data.groupName,
                    averageGrade = data.averageGrade,
                    totalGrades = data.totalGrades
                )

                val success = StatisticsExporter.savePdfToUri(requireContext(), pdfDocument, uri)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportButton.isEnabled = true

                    if (success) {
                        Toast.makeText(requireContext(), "✅ PDF отчёт успешно сохранён!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "❌ Ошибка сохранения PDF отчёта", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportButton.isEnabled = true
                    Toast.makeText(requireContext(), "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showStudentDetails(student: StudentStatistics) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_student_details, null)
        val titleText = dialogView.findViewById<TextView>(R.id.detailTitleText)
        val nameText = dialogView.findViewById<TextView>(R.id.detailNameText)
        val statusText = dialogView.findViewById<TextView>(R.id.detailStatusText)
        val gradesCountValue = dialogView.findViewById<TextView>(R.id.detailGradesCountValue)
        val averageValue = dialogView.findViewById<TextView>(R.id.detailAverageValue)

        val status = getGradeStatus(student.averageGrade)
        val statusColor = when {
            student.averageGrade >= 9 -> Color.parseColor("#22C55E")
            student.averageGrade >= 7 -> Color.parseColor("#3B82F6")
            student.averageGrade >= 5 -> Color.parseColor("#F59E0B")
            student.averageGrade > 0 -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#94A3B8")
        }

        titleText.text = "Детали учащегося"
        nameText.text = student.studentName
        gradesCountValue.text = student.gradesCount.toString()
        averageValue.text = "%.2f".format(student.averageGrade)
        statusText.text = status
        statusText.setTextColor(statusColor)
        averageValue.setTextColor(statusColor)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getGradeStatus(average: Double): String {
        return when {
            average >= 9 -> "Отлично 🌟"
            average >= 7 -> "Хорошо 👍"
            average >= 5 -> "Удовлетворительно 📝"
            average > 0 -> "Неудовлетворительно ⚠️"
            else -> "Нет отметок"
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = message
    }
}
