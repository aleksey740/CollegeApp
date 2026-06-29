package com.sakovich.collegeapp.presentation.teacher

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.repositories.GradeRepository
import com.sakovich.collegeapp.utils.DrawableUtils
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class AddGradeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var gradeRepository: GradeRepository
    private lateinit var notificationRepository: NotificationRepository
    private val db = Firebase.firestore

    private lateinit var titleText: TextView
    private lateinit var avatarText: TextView
    private lateinit var studentNameText: TextView
    private lateinit var groupNameText: TextView
    private lateinit var subjectEditText: TextInputEditText
    private lateinit var gradeDropdown: AutoCompleteTextView
    private lateinit var typeDropdown: AutoCompleteTextView
    private lateinit var commentEditText: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    private var currentStudentId: String = ""
    private var currentStudentName: String = ""
    private var currentGroupName: String = ""

    private var isEditMode: Boolean = false
    private var editGradeId: String = ""
    private var editGrade: Grade? = null

    private val grades = arrayOf("10", "9", "8", "7", "6", "5", "4", "3", "2", "1")

    private val types = arrayOf(
        "Экзамен",
        "Зачет",
        "Лабораторная",
        "Тест",
        "Курсовая",
        "Практическая работа"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_add_grade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        gradeRepository = GradeRepository()
        notificationRepository = NotificationRepository()

        initViews(view)

        currentStudentId = arguments?.getString("studentId") ?: ""
        currentStudentName = arguments?.getString("studentName") ?: "Учащийся"
        currentGroupName = arguments?.getString("groupName") ?: ""
        isEditMode = arguments?.getBoolean("isEditMode") ?: false
        editGradeId = arguments?.getString("gradeId") ?: ""

        updateStudentInfo()
        setupDropdowns()

        if (isEditMode && editGradeId.isNotEmpty()) {
            titleText.text = "✏️ Редактирование отметки"
            saveButton.text = "💾 Обновить отметку"
            loadGradeForEdit()
        }

        saveButton.setOnClickListener {
            saveGrade()
        }

        loadStudentData()
    }

    private fun initViews(view: View) {
        titleText = view.findViewById(R.id.titleText)
        avatarText = view.findViewById(R.id.avatarText)
        studentNameText = view.findViewById(R.id.studentNameText)
        groupNameText = view.findViewById(R.id.groupNameText)
        subjectEditText = view.findViewById(R.id.subjectEditText)
        gradeDropdown = view.findViewById(R.id.gradeDropdown)
        typeDropdown = view.findViewById(R.id.typeDropdown)
        commentEditText = view.findViewById(R.id.commentEditText)
        saveButton = view.findViewById(R.id.saveButton)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun updateStudentInfo() {
        studentNameText.text = currentStudentName
        groupNameText.text = "Группа: $currentGroupName"

        val initials = getInitials(currentStudentName)
        avatarText.text = initials

        DrawableUtils.setViewBackgroundColorHex(
            avatarText,
            DrawableUtils.colorForName(currentStudentName)
        )
    }

    private fun getInitials(fullName: String): String {
        val parts = fullName.trim().split(" ")
        return when {
            parts.size >= 2 -> "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "??"
        }
    }

    private fun loadGradeForEdit() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val grade = gradeRepository.getGradeById(editGradeId)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (grade != null) {
                        editGrade = grade
                        subjectEditText.setText(grade.subject)
                        commentEditText.setText(grade.comment)
                        gradeDropdown.setText(grade.value.toString(), false)
                        typeDropdown.setText(grade.type, false)
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Ошибка загрузки отметки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadStudentData() {
        if (currentStudentId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val document = db.collection("users")
                    .document(currentStudentId)
                    .get()
                    .await()

                if (document.exists()) {
                    val fullName = document.getString("fullName") ?: currentStudentName
                    val groupName = document.getString("groupName") ?: currentGroupName

                    requireActivity().runOnUiThread {
                        currentStudentName = fullName
                        currentGroupName = groupName
                        updateStudentInfo()
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun setupDropdowns() {

        val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, grades)
        gradeDropdown.setAdapter(gradeAdapter)
        gradeDropdown.setText("10", false)

        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        typeDropdown.setAdapter(typeAdapter)
        typeDropdown.setText(types[0], false)
    }

    private fun saveGrade() {
        val subject = subjectEditText.text.toString().trim()
        val gradeValueStr = gradeDropdown.text.toString()
        val gradeValue = gradeValueStr.toIntOrNull() ?: 10
        val type = typeDropdown.text.toString()
        val comment = commentEditText.text.toString().trim()

        if (subject.isEmpty()) {
            subjectEditText.error = "Введите название предмета"
            subjectEditText.requestFocus()
            return
        }

        if (currentStudentId.isEmpty()) {
            Toast.makeText(requireContext(), "Учащийся не выбран", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false
        saveButton.text = "Сохранение..."

        val currentUser = auth.currentUser
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

        val grade = Grade(
            id = if (isEditMode) editGradeId else "",
            studentId = currentStudentId,
            studentName = currentStudentName,
            subject = subject,
            value = gradeValue,
            date = date,
            type = type,
            teacherId = currentUser?.uid ?: "",
            teacherName = currentUser?.displayName ?: currentUser?.email ?: "Преподаватель",
            comment = comment,
            createdAt = if (isEditMode) (editGrade?.createdAt ?: System.currentTimeMillis()) else System.currentTimeMillis()
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = if (isEditMode) {
                    gradeRepository.updateGrade(editGradeId, grade)
                } else {
                    val gradeId = gradeRepository.addGrade(grade)

                    try {
                        notificationRepository.createGradeNotification(
                            studentId = currentStudentId,
                            studentName = currentStudentName,
                            subject = subject,
                            grade = gradeValue,
                            teacherName = grade.teacherName,
                            gradeId = gradeId
                        )
                    } catch (e: Exception) {

                    }
                    true
                }

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true
                    saveButton.text = if (isEditMode) "💾 Обновить отметку" else "💾 Сохранить отметку"

                    if (success) {
                        val message = if (isEditMode) "✅ Отметка обновлена!" else "✅ Отметка выставлена!"
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "❌ Ошибка сохранения", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true
                    saveButton.text = if (isEditMode) "💾 Обновить отметку" else "💾 Сохранить отметку"
                    Toast.makeText(requireContext(), "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        fun newInstance(studentId: String, studentName: String, groupName: String): AddGradeFragment {
            val fragment = AddGradeFragment()
            val args = Bundle()
            args.putString("studentId", studentId)
            args.putString("studentName", studentName)
            args.putString("groupName", groupName)
            args.putBoolean("isEditMode", false)
            fragment.arguments = args
            return fragment
        }

        fun newInstanceForEdit(
            studentId: String,
            studentName: String,
            groupName: String,
            gradeId: String
        ): AddGradeFragment {
            val fragment = AddGradeFragment()
            val args = Bundle()
            args.putString("studentId", studentId)
            args.putString("studentName", studentName)
            args.putString("groupName", groupName)
            args.putString("gradeId", gradeId)
            args.putBoolean("isEditMode", true)
            fragment.arguments = args
            return fragment
        }
    }
}
