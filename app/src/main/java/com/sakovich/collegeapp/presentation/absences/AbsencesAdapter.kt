package com.sakovich.collegeapp.presentation.absences

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.AbsenceReason
import com.sakovich.collegeapp.data.models.getAbsenceReasonDisplayName
import com.sakovich.collegeapp.utils.AbsenceFormat
import com.sakovich.collegeapp.utils.DrawableUtils

class AbsencesAdapter(
    private var absences: List<Absence>,
    private val showStudentName: Boolean = false,
    private val canManage: Boolean = false,
    private val canModifyItem: (Absence) -> Boolean = { true },
    private val onAbsenceClick: ((Absence) -> Unit)? = null,
    private val onAbsenceLongClick: ((Absence) -> Unit)? = null
) : RecyclerView.Adapter<AbsencesAdapter.AbsenceViewHolder>() {

    class AbsenceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusBar: View = itemView.findViewById(R.id.statusBar)
        val subjectText: TextView = itemView.findViewById(R.id.subjectText)
        val hoursText: TextView = itemView.findViewById(R.id.hoursText)
        val studentText: TextView = itemView.findViewById(R.id.studentText)
        val reasonText: TextView = itemView.findViewById(R.id.reasonText)
        val excusedText: TextView = itemView.findViewById(R.id.excusedText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val commentText: TextView = itemView.findViewById(R.id.commentText)
        val createdByText: TextView = itemView.findViewById(R.id.createdByText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbsenceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_absence, parent, false)
        return AbsenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AbsenceViewHolder, position: Int) {
        val absence = absences[position]

        holder.subjectText.text = absence.subject
        holder.hoursText.text = "${absence.hours} ч."
        holder.dateText.text = absence.date

        if (showStudentName) {
            holder.studentText.text = "👤 ${absence.studentName}"
            holder.studentText.visibility = View.VISIBLE
        } else {
            holder.studentText.visibility = View.GONE
        }

        holder.reasonText.text = getAbsenceReasonDisplayName(absence.reason)

        val reasonColor = when (absence.reason) {
            AbsenceReason.WITHOUT_REASON -> "#EF4444"
            AbsenceReason.SICK -> "#8B5CF6"
            AbsenceReason.FAMILY -> "#F59E0B"
            AbsenceReason.OFFICIAL -> "#10B981"
            AbsenceReason.OTHER -> "#8B5CF6"
        }
        DrawableUtils.setViewBackgroundColorHex(holder.reasonText, reasonColor)

        if (absence.isExcused) {
            holder.excusedText.text = "✅ Уваж."
            holder.excusedText.setTextColor(android.graphics.Color.parseColor("#34D399"))
            holder.statusBar.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
            holder.hoursText.setTextColor(android.graphics.Color.parseColor("#34D399"))
        } else {
            holder.excusedText.text = "❌ Н/У"
            holder.excusedText.setTextColor(android.graphics.Color.parseColor("#F87171"))
            holder.statusBar.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
            holder.hoursText.setTextColor(android.graphics.Color.parseColor("#F87171"))
        }

        if (absence.comment.isNotEmpty()) {
            holder.commentText.text = "💬 ${absence.comment}"
            holder.commentText.visibility = View.VISIBLE
        } else {
            holder.commentText.visibility = View.GONE
        }

        holder.createdByText.text = AbsenceFormat.creatorLineWithIcon(
            absence.createdByRole,
            absence.createdByName
        )

        val canModify = canManage && canModifyItem(absence)
        holder.itemView.setOnClickListener {
            if (canModify && onAbsenceLongClick != null) {
                onAbsenceLongClick.invoke(absence)
            } else {
                onAbsenceClick?.invoke(absence)
            }
        }
    }

    override fun getItemCount() = absences.size

    fun updateAbsences(newAbsences: List<Absence>) {
        this.absences = newAbsences
        notifyDataSetChanged()
    }

    fun getCurrentAbsences(): List<Absence> {
        return absences
    }
}
