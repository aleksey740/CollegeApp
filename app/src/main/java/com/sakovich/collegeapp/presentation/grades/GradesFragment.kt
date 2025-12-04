package com.sakovich.collegeapp.presentation.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade

class GradesFragment : Fragment() {

    private lateinit var gradesRecyclerView: RecyclerView
    private lateinit var averageGradeText: TextView
    private lateinit var totalGradesText: TextView
    private val gradesList = mutableListOf<Grade>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_grades, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gradesRecyclerView = view.findViewById(R.id.gradesRecyclerView)
        averageGradeText = view.findViewById(R.id.averageGradeText)
        totalGradesText = view.findViewById(R.id.totalGradesText)

        setupRecyclerView()
        loadTestData()
        updateStatistics()
    }

    private fun setupRecyclerView() {
        gradesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        gradesRecyclerView.adapter = GradesAdapter(gradesList)
    }

    private fun loadTestData() {
        gradesList.clear()
        gradesList.addAll(listOf(
            Grade(subject = "Математика", value = 5, date = "2024-01-15", type = "Экзамен"),
            Grade(subject = "Физика", value = 4, date = "2024-01-10", type = "Зачет"),
            Grade(subject = "Программирование", value = 5, date = "2024-01-08", type = "Лабораторная"),
            Grade(subject = "Базы данных", value = 3, date = "2024-01-05", type = "Экзамен"),
            Grade(subject = "Английский язык", value = 5, date = "2024-01-03", type = "Тест"),
            Grade(subject = "История", value = 4, date = "2024-01-02", type = "Экзамен"),
            Grade(subject = "Физкультура", value = 5, date = "2024-01-01", type = "Зачет")
        ))
        gradesRecyclerView.adapter?.notifyDataSetChanged()
    }

    private fun updateStatistics() {
        if (gradesList.isNotEmpty()) {
            val average = gradesList.map { it.value }.average()
            averageGradeText.text = "Средний балл: ${"%.2f".format(average)}"
            totalGradesText.text = "Всего оценок: ${gradesList.size}"
        } else {
            averageGradeText.text = "Средний балл: -"
            totalGradesText.text = "Всего оценок: 0"
        }
    }
}