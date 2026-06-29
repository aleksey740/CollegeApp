package com.sakovich.collegeapp.presentation.statistics

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.StudentStatistics
import com.sakovich.collegeapp.utils.DrawableUtils

class StatisticsAdapter(
    private var students: List<StudentStatistics>,
    private val showGroup: Boolean = false,
    private val onItemClick: ((StudentStatistics) -> Unit)? = null
) : RecyclerView.Adapter<StatisticsAdapter.StatisticsViewHolder>() {

    private val positionColors = listOf(
        "#FFD700",
        "#C0C0C0",
        "#CD7F32",
        "#8B5CF6",
        "#8B5CF6",
        "#10B981",
        "#F59E0B"
    )

    inner class StatisticsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val positionText: TextView = itemView.findViewById(R.id.positionText)
        val studentNameText: TextView = itemView.findViewById(R.id.studentNameText)
        val groupText: TextView = itemView.findViewById(R.id.groupText)
        val gradesCountText: TextView = itemView.findViewById(R.id.gradesCountText)
        val averageGradeText: TextView = itemView.findViewById(R.id.averageGradeText)

        fun bind(student: StudentStatistics, position: Int) {

            positionText.text = (position + 1).toString()
            val colorIndex = when (position) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> (position % 4) + 3
            }
            DrawableUtils.setViewBackgroundColor(
                positionText,
                Color.parseColor(positionColors[colorIndex])
            )

            studentNameText.text = student.studentName

            if (showGroup && student.groupName.isNotEmpty()) {
                groupText.visibility = View.VISIBLE
                groupText.text = student.groupName
            } else {
                groupText.visibility = View.GONE
            }

            val gradesWord = getGradesWord(student.gradesCount)
            gradesCountText.text = "📝 ${student.gradesCount} $gradesWord"

            if (student.gradesCount > 0) {
                averageGradeText.text = "%.1f".format(student.averageGrade)

                val gradeColor = when {
                    student.averageGrade >= 9 -> "#10B981"
                    student.averageGrade >= 7 -> "#A78BFA"
                    student.averageGrade >= 5 -> "#F59E0B"
                    else -> "#EF4444"
                }
                averageGradeText.setTextColor(Color.parseColor(gradeColor))
            } else {
                averageGradeText.text = "—"
                averageGradeText.setTextColor(Color.parseColor("#64748B"))
            }

            itemView.setOnClickListener {
                onItemClick?.invoke(student)
            }
        }

        private fun getGradesWord(count: Int): String {
            return when {
                count % 100 in 11..19 -> "отметок"
                count % 10 == 1 -> "отметка"
                count % 10 in 2..4 -> "отметки"
                else -> "отметок"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatisticsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_statistics, parent, false)
        return StatisticsViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatisticsViewHolder, position: Int) {
        holder.bind(students[position], position)
    }

    override fun getItemCount(): Int = students.size

    fun updateData(newStudents: List<StudentStatistics>) {
        students = newStudents.sortedByDescending { it.averageGrade }
        notifyDataSetChanged()
    }
}
