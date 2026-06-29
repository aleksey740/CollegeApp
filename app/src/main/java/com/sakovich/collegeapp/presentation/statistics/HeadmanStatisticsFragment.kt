package com.sakovich.collegeapp.presentation.statistics

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade
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

class HeadmanStatisticsFragment : Fragment() {

    private lateinit var semesterFilterLayout: TextInputLayout
    private lateinit var semesterFilterDropdown: AutoCompleteTextView
    private lateinit var groupNameText: TextView
    private lateinit var studentsCountText: TextView
    private lateinit var averageGradeText: TextView
    private lateinit var totalGradesText: TextView
    private lateinit var exportButton: Button
    private lateinit var headmanStatisticsBackButton: ImageButton
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var gradeRepository: GradeRepository

    private lateinit var statisticsAdapter: StatisticsAdapter

    private var currentGroupName: String = ""
    private var currentSemester: Int? = null
    private var studentStatistics = mutableListOf<StudentStatistics>()
    private var totalGrades = 0
    private var groupAverageGrade = 0.0

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
        return inflater.inflate(R.layout.fragment_headman_statistics, container, false)
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
        loadCurrentUserAndStatistics()
    }

    private fun initViews(view: View) {
        semesterFilterLayout = view.findViewById(R.id.semesterFilterLayout)
        semesterFilterDropdown = view.findViewById(R.id.semesterFilterDropdown)
        groupNameText = view.findViewById(R.id.groupNameText)
        studentsCountText = view.findViewById(R.id.studentsCountText)
        averageGradeText = view.findViewById(R.id.averageGradeText)
        totalGradesText = view.findViewById(R.id.totalGradesText)
        exportButton = view.findViewById(R.id.exportButton)
        headmanStatisticsBackButton = view.findViewById(R.id.headmanStatisticsBackButton)
        studentsRecyclerView = view.findViewById(R.id.studentsRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyText)

        setupSemesterDropdown()

        headmanStatisticsBackButton.setOnClickListener {
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
            loadCurrentUserAndStatistics()
        }
    }

    private fun setupRecyclerView() {
        statisticsAdapter = StatisticsAdapter(
            students = studentStatistics,
            showGroup = false,
            onItemClick = { student ->
                showStudentDetails(student)
            }
        )
        studentsRecyclerView.setupStatisticsListInScrollView()
        studentsRecyclerView.adapter = statisticsAdapter
    }

    private fun setupClickListeners() {
        exportButton.setOnClickListener {
            showExportDialog()
        }
    }

    private fun loadCurrentUserAndStatistics() {
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
                var allGrades = 0
                var totalSum = 0.0
                var studentsWithGrades = 0

                for (student in students) {
                    val allStudentGrades = gradeRepository.getStudentGrades(student.id)

                    val grades = if (currentSemester != null) {
                        allStudentGrades.filter { it.semester == currentSemester }
                    } else {
                        allStudentGrades
                    }
                    val forAvg = grades.filter { it.value != Grade.VALUE_ABSENCE }
                    val avgGrade = if (forAvg.isNotEmpty()) {
                        forAvg.map { it.value }.average()
                    } else 0.0

                    if (grades.isNotEmpty()) {
                        totalSum += avgGrade
                        studentsWithGrades++
                    }

                    allGrades += grades.size

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

                totalGrades = allGrades
                groupAverageGrade = if (studentsWithGrades > 0) {
                    totalSum / studentsWithGrades
                } else 0.0

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    val semesterText = if (currentSemester != null) " ($currentSemester сем.)" else ""
                    groupNameText.text = "Группа: $currentGroupName$semesterText"
                    studentsCountText.text = statistics.size.toString()
                    averageGradeText.text = if (groupAverageGrade > 0) "%.1f".format(groupAverageGrade) else "—"
                    totalGradesText.text = totalGrades.toString()

                    studentStatistics.clear()
                    studentStatistics.addAll(statistics)
                    statisticsAdapter.updateData(statistics)
                    studentsRecyclerView.post { studentsRecyclerView.updateStatisticsListHeight() }

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

    private fun showExportDialog() {
        exportToPdf()
    }

    private fun exportToPdf() {
        if (studentStatistics.isEmpty()) {
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
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePdfToUri(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        exportButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfDocument = StatisticsExporter.createPdfDocument(
                    title = "Статистика группы $currentGroupName",
                    students = studentStatistics,
                    showGroup = false,
                    groupName = currentGroupName,
                    averageGrade = groupAverageGrade,
                    totalGrades = totalGrades
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
