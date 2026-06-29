package com.sakovich.collegeapp.presentation.grades

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Grade

class GradesAdapter(
    private var grades: List<Grade>,
    private val canEdit: Boolean = false,
    private val onGradeClick: ((Grade) -> Unit)? = null,
    private val onGradeLongClick: ((Grade) -> Unit)? = null
) : RecyclerView.Adapter<GradesAdapter.GradeViewHolder>() {

    class GradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectText: TextView = itemView.findViewById(R.id.subjectText)
        val typeText: TextView = itemView.findViewById(R.id.typeText)
        val semesterChipText: TextView = itemView.findViewById(R.id.semesterChipText)
        val gradeValueText: TextView = itemView.findViewById(R.id.gradeValueText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val grade = grades[position]

        holder.subjectText.text = grade.subject
        val typeLabel = grade.typeDisplayLabel()
        if (typeLabel != "Обычная") {
            holder.typeText.text = typeLabel
            holder.typeText.visibility = View.VISIBLE
        } else {
            holder.typeText.visibility = View.GONE
        }
        holder.semesterChipText.text = "${grade.semester} семестр"

        holder.gradeValueText.text = if (grade.isAbsence()) "Н" else grade.value.toString()

        val gradeColor = when {
            grade.isAbsence() -> "#64748B"
            grade.value >= 9 -> "#10B981"
            grade.value >= 7 -> "#A78BFA"
            grade.value >= 5 -> "#F59E0B"
            grade.value >= 3 -> "#F97316"
            else -> "#EF4444"
        }
        val badge = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(gradeColor))
        }
        holder.gradeValueText.background = badge
        holder.dateText.text = grade.date

        holder.itemView.setOnClickListener {
            if (canEdit && onGradeLongClick != null) {
                onGradeLongClick.invoke(grade)
            } else {
                onGradeClick?.invoke(grade)
            }
        }
    }

    override fun getItemCount() = grades.size

    fun updateGrades(newGrades: List<Grade>) {
        this.grades = newGrades
        notifyDataSetChanged()
    }

    fun getCurrentGrades(): List<Grade> = grades
}
