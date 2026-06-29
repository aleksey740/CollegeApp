package com.sakovich.collegeapp.presentation.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.utils.DrawableUtils

class StudentsAdapter(
    private val students: List<Student>,
    private val onStudentClick: (Student) -> Unit,
    private val onGradesClick: ((Student) -> Unit)? = null,
    private val mode: Int = MODE_INFO
) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

    companion object {
        const val MODE_INFO = 0
        const val MODE_GRADES = 1
    }

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val positionText: TextView = itemView.findViewById(R.id.positionText)
        val studentNameText: TextView = itemView.findViewById(R.id.studentNameText)
        val headmanBadge: TextView = itemView.findViewById(R.id.headmanBadge)
        val actionButton: LinearLayout = itemView.findViewById(R.id.actionButton)
        val addGradeButton: TextView = itemView.findViewById(R.id.addGradeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        val displayName = student.fullName.removeSuffix(" (Староста)").trim()

        holder.positionText.text = getInitials(displayName)
        DrawableUtils.setViewBackgroundColorHex(
            holder.positionText,
            DrawableUtils.colorForName(displayName)
        )
        holder.studentNameText.text = displayName
        holder.headmanBadge.visibility = if (student.isHeadman) View.VISIBLE else View.GONE

        when (mode) {
            MODE_GRADES -> {
                holder.addGradeButton.text = "📝"
                holder.actionButton.visibility = View.VISIBLE
                holder.actionButton.setOnClickListener {
                    onGradesClick?.invoke(student) ?: onStudentClick(student)
                }
            }
            MODE_INFO -> {
                holder.actionButton.visibility = View.GONE
            }
        }

        val openStudent = View.OnClickListener { onStudentClick(student) }
        holder.itemView.setOnClickListener(openStudent)
        holder.studentNameText.setOnClickListener(openStudent)
        holder.positionText.setOnClickListener(openStudent)
    }

    override fun getItemCount() = students.size

    private fun getInitials(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+"))
        return when {
            parts.size >= 2 -> "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "??"
        }
    }
}
