package com.sakovich.collegeapp.presentation.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.presentation.teacher.adapters.StudentsAdapter  // 👈 ДОБАВЬТЕ ЭТОТ ИМПОРТ

class TeacherStudentsFragment : Fragment() {

    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var groupNameText: TextView
    private val studentsList = mutableListOf<Student>()
    private var groupId: String = ""
    private var groupName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_students, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        studentsRecyclerView = view.findViewById(R.id.studentsRecyclerView)
        groupNameText = view.findViewById(R.id.groupNameText)

        // Получаем данные о группе
        groupId = arguments?.getString("groupId") ?: ""
        groupName = arguments?.getString("groupName") ?: "Группа"

        groupNameText.text = "Группа: $groupName"

        setupRecyclerView()
        loadTestStudentsData()
    }

    private fun setupRecyclerView() {
        studentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        studentsRecyclerView.adapter = StudentsAdapter(studentsList) { student ->
            // Переход к форме выставления оценки
            val addGradeFragment = AddGradeFragment.newInstance(
                studentId = student.id,
                studentName = student.fullName,
                groupName = groupName
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, addGradeFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadTestStudentsData() {
        studentsList.clear()
        studentsList.addAll(listOf(
            Student(id = "1", fullName = "Иванов Алексей Петрович", groupName = groupName),
            Student(id = "2", fullName = "Петрова Мария Сергеевна", groupName = groupName),
            Student(id = "3", fullName = "Сидоров Дмитрий Иванович", groupName = groupName),
            Student(id = "4", fullName = "Кузнецова Анна Владимировна", groupName = groupName),
            Student(id = "5", fullName = "Смирнов Артем Олегович", groupName = groupName)
        ))

        studentsRecyclerView.adapter?.notifyDataSetChanged()
    }

    companion object {
        fun newInstance(groupId: String, groupName: String): TeacherStudentsFragment {
            val fragment = TeacherStudentsFragment()
            val args = Bundle()
            args.putString("groupId", groupId)
            args.putString("groupName", groupName)
            fragment.arguments = args
            return fragment
        }
    }
}