package com.sakovich.collegeapp.presentation.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.data.repositories.GradeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddGradeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var gradeRepository: GradeRepository

    private lateinit var studentNameText: TextView
    private lateinit var groupNameText: TextView
    private lateinit var subjectSpinner: Spinner
    private lateinit var gradeSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var commentEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var currentStudent: Student
    private lateinit var currentGroupName: String

    // Списки для Spinner
    private val subjects = arrayOf("Математика", "Физика", "Программирование", "Базы данных", "Английский язык")
    private val grades = arrayOf("5", "4", "3", "2")
    private val types = arrayOf("Экзамен", "Зачет", "Лабораторная", "Тест", "Курсовая")

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

        // Инициализация View
        initViews(view)

        // Получаем данные студента - ИСПРАВЛЕННАЯ ВЕРСИЯ
        val studentId = arguments?.getString("studentId") ?: ""
        val studentName = arguments?.getString("studentName") ?: "Студент"
        currentGroupName = arguments?.getString("groupName") ?: ""

        // Создаем объект студента
        currentStudent = Student(id = studentId, fullName = studentName)

        // Заполняем данные
        studentNameText.text = "Студент: $studentName"
        groupNameText.text = "Группа: $currentGroupName"

        // Настраиваем Spinner
        setupSpinners()

        // Обработчик сохранения
        saveButton.setOnClickListener {
            saveGrade()
        }
    }

    private fun initViews(view: View) {
        studentNameText = view.findViewById(R.id.studentNameText)
        groupNameText = view.findViewById(R.id.groupNameText)
        subjectSpinner = view.findViewById(R.id.subjectSpinner)
        gradeSpinner = view.findViewById(R.id.gradeSpinner)
        typeSpinner = view.findViewById(R.id.typeSpinner)
        commentEditText = view.findViewById(R.id.commentEditText)
        saveButton = view.findViewById(R.id.saveButton)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupSpinners() {
        // Настройка Spinner для предметов
        val subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subjects)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter

        // Настройка Spinner для оценок
        val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, grades)
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gradeSpinner.adapter = gradeAdapter

        // Настройка Spinner для типов работ
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter
    }

    private fun saveGrade() {
        val subject = subjectSpinner.selectedItem as String
        val gradeValue = (gradeSpinner.selectedItem as String).toInt()
        val type = typeSpinner.selectedItem as String
        val comment = commentEditText.text.toString()

        // Валидация
        if (comment.isEmpty()) {
            commentEditText.error = "Введите комментарий"
            return
        }

        // Показываем прогресс
        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false

        // Создаем оценку
        val currentUser = auth.currentUser
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val grade = Grade(
            studentId = currentStudent.id,
            studentName = currentStudent.fullName,
            subject = subject,
            value = gradeValue,
            date = date,
            type = type,
            teacherId = currentUser?.uid ?: "",
            teacherName = currentUser?.email ?: "Преподаватель",
            comment = comment
        )

        // Сохраняем в Firestore
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gradeId = gradeRepository.addGrade(grade)

                // Возвращаемся в UI поток для обновления интерфейса
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true

                    Toast.makeText(
                        requireContext(),
                        "Оценка успешно выставлена!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Возвращаемся назад
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true

                    Toast.makeText(
                        requireContext(),
                        "Ошибка: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
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
            fragment.arguments = args
            return fragment
        }
    }
}