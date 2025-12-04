package com.sakovich.collegeapp.presentation.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Student

class StudentsAdapter(
    private val students: List<Student>,
    private val onStudentClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentNameText: TextView = itemView.findViewById(R.id.studentNameText)
        val groupNameText: TextView = itemView.findViewById(R.id.groupNameText)
        val addGradeButton: TextView = itemView.findViewById(R.id.addGradeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]

        holder.studentNameText.text = student.fullName
        holder.groupNameText.text = student.groupName

        holder.addGradeButton.setOnClickListener {
            onStudentClick(student)
        }

        holder.itemView.setOnClickListener {
            onStudentClick(student)
        }
    }

    override fun getItemCount() = students.size
}