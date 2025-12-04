package com.sakovich.collegeapp.presentation.grades

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade

class GradesAdapter(private val grades: List<Grade>) :
    RecyclerView.Adapter<GradesAdapter.GradeViewHolder>() {

    class GradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectText: TextView = itemView.findViewById(R.id.subjectText)
        val gradeValueText: TextView = itemView.findViewById(R.id.gradeValueText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val typeText: TextView = itemView.findViewById(R.id.typeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val grade = grades[position]

        holder.subjectText.text = grade.subject
        holder.gradeValueText.text = grade.value.toString()
        holder.dateText.text = grade.date
        holder.typeText.text = grade.type

        // Разный цвет для оценок
        val color = when (grade.value) {
            5 -> R.color.grade_5
            4 -> R.color.grade_4
            3 -> R.color.grade_3
            else -> R.color.grade_2
        }
        holder.gradeValueText.setTextColor(
            ContextCompat.getColor(holder.itemView.context, color)
        )
    }

    override fun getItemCount() = grades.size
}