package com.sakovich.collegeapp.presentation.teacher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.presentation.grades.GradesAdapter
import com.sakovich.collegeapp.utils.StatisticsExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StudentGradesFragment : Fragment() {

    private lateinit var gradesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var studentInfoText: TextView
    private lateinit var averageGradeValue: TextView
    private lateinit var totalGradesValue: TextView
    private lateinit var fabAddGrade: FloatingActionButton
    private lateinit var exportButton: Button

    private lateinit var gradeRepository: GradeRepository
    private lateinit var gradesAdapter: GradesAdapter

    private val gradesList = mutableListOf<Grade>()

    private var studentId: String = ""
    private var studentName: String = ""
    private var groupName: String = ""

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
        return inflater.inflate(R.layout.fragment_student_grades, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gradeRepository = GradeRepository()

        studentId = arguments?.getString("studentId") ?: ""
        studentName = arguments?.getString("studentName") ?: ""
        groupName = arguments?.getString("groupName") ?: ""

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadGrades()
    }

    override fun onResume() {
        super.onResume()
        loadGrades()
    }

    private fun initViews(view: View) {
        gradesRecyclerView = view.findViewById(R.id.gradesRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        studentInfoText = view.findViewById(R.id.studentInfoText)
        averageGradeValue = view.findViewById(R.id.averageGradeValue)
        totalGradesValue = view.findViewById(R.id.totalGradesValue)
        fabAddGrade = view.findViewById(R.id.fabAddGrade)
        exportButton = view.findViewById(R.id.exportButton)

        studentInfoText.text = studentName
        exportButton.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        gradesAdapter = GradesAdapter(
            grades = gradesList,
            canEdit = true,
            onGradeClick = { grade -> showGradeDetails(grade) },
            onGradeLongClick = { grade -> showGradeActionDialog(grade) }
        )
        gradesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        gradesRecyclerView.adapter = gradesAdapter
    }

    private fun setupClickListeners() {
        fabAddGrade.setOnClickListener {
            openAddGradeFragment()
        }

        exportButton.setOnClickListener {
            exportToPdf()
        }
    }

    private fun loadGrades() {
        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val grades = gradeRepository.getStudentGrades(studentId)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    gradesList.clear()
                    gradesList.addAll(grades)
                    gradesAdapter.updateGrades(grades)

                    if (grades.isEmpty()) {
                        emptyStateLayout.visibility = View.VISIBLE
                        emptyText.text = "Отметок пока нет"
                        averageGradeValue.text = "—"
                        totalGradesValue.text = "0"
                        exportButton.visibility = View.GONE
                    } else {
                        emptyStateLayout.visibility = View.GONE
                        exportButton.visibility = View.VISIBLE
                        updateStatistics()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                    emptyText.text = "Ошибка загрузки"
                }
            }
        }
    }

    private fun updateStatistics() {
        totalGradesValue.text = gradesList.size.toString()

        if (gradesList.isNotEmpty()) {
            val average = gradesList.map { it.value }.average()
            averageGradeValue.text = "%.1f".format(average)
        } else {
            averageGradeValue.text = "—"
        }
    }

    private fun showGradeDetails(grade: Grade) {
        val details = """
            📚 Предмет: ${grade.subject}
            📊 Отметка: ${grade.value}
            📋 Тип: ${grade.typeDisplayLabel()}
            📅 Дата: ${grade.date}
            👨‍🏫 Преподаватель: ${grade.teacherName}
            💬 Комментарий: ${grade.comment.ifEmpty { "—" }}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 Детали отметки")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showGradeActionDialog(grade: Grade) {
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_menu, null)
        val titleText = menuView.findViewById<android.widget.TextView>(R.id.menuTitleText)
        val editBtn = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton1)
        val deleteBtn = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton2)
        menuView.findViewById<View>(R.id.actionButton3).visibility = View.GONE
        menuView.findViewById<View>(R.id.actionButton4).visibility = View.GONE

        titleText.text = "${grade.subject} • ${grade.value}/10\n${grade.date}"
        editBtn.text = "Редактировать"
        deleteBtn.text = "Удалить"

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(menuView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        editBtn.setOnClickListener { dialog.dismiss(); openEditGradeFragment(grade) }
        deleteBtn.setOnClickListener { dialog.dismiss(); showDeleteConfirmation(grade) }
        dialog.show()
    }

    private fun showDeleteConfirmation(grade: Grade) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🗑️ Удаление отметки")
            .setMessage("Вы уверены, что хотите удалить отметку?\n\n📚 ${grade.subject}\n📊 ${grade.value}\n📅 ${grade.date}")
            .setPositiveButton("Удалить") { _, _ ->
                deleteGrade(grade)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteGrade(grade: Grade) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = gradeRepository.deleteGrade(grade.id)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (success) {
                        Snackbar.make(requireView(), "✅ Отметка удалена", Snackbar.LENGTH_SHORT).show()
                        loadGrades()
                    } else {
                        Snackbar.make(requireView(), "❌ Ошибка удаления", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Snackbar.make(requireView(), "❌ Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openAddGradeFragment() {
        val addGradeFragment = AddGradeFragment.newInstance(
            studentId = studentId,
            studentName = studentName,
            groupName = groupName
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, addGradeFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openEditGradeFragment(grade: Grade) {
        val editGradeFragment = AddGradeFragment.newInstanceForEdit(
            studentId = studentId,
            studentName = studentName,
            groupName = groupName,
            gradeId = grade.id
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editGradeFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun exportToPdf() {
        if (gradesList.isEmpty()) {
            Toast.makeText(requireContext(), "Нет отметок для экспорта", Toast.LENGTH_SHORT).show()
            return
        }

        val average = if (gradesList.isNotEmpty()) {
            gradesList.map { it.value }.average()
        } else 0.0

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Отметки_${studentName.replace(" ", "_")}_${dateFormat.format(Date())}.pdf"

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

        val average = if (gradesList.isNotEmpty()) {
            gradesList.map { it.value }.average()
        } else 0.0

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfDocument = StatisticsExporter.createStudentGradesPdfDocument(
                    studentName = studentName,
                    groupName = groupName,
                    grades = gradesList,
                    averageGrade = average
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

    companion object {
        fun newInstance(studentId: String, studentName: String, groupName: String): StudentGradesFragment {
            val fragment = StudentGradesFragment()
            val args = Bundle()
            args.putString("studentId", studentId)
            args.putString("studentName", studentName)
            args.putString("groupName", groupName)
            fragment.arguments = args
            return fragment
        }
    }
}
