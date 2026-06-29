package com.sakovich.collegeapp.presentation.curatorial

import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.utils.PersonNameFormat
import java.util.Locale

internal object InfoHourStudentsView {

    fun sortBySurname(students: List<User>): List<User> =
        students.sortedBy { it.fullName.trim().substringBefore(" ").lowercase(Locale.getDefault()) }

    fun bind(
        container: LinearLayout,
        students: List<User>,
        selectedIds: MutableSet<String>,
        onChecked: (User, Boolean) -> Unit
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        students.forEach { user ->
            val row = inflater.inflate(R.layout.item_info_hour_student, container, false)
            val checkBox = row.findViewById<CheckBox>(R.id.checkBox)
            val nameText = row.findViewById<TextView>(R.id.nameText)
            nameText.text = PersonNameFormat.shortFio(user.fullName).ifBlank { user.email }
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = selectedIds.contains(user.id)
            checkBox.setOnCheckedChangeListener { _, isChecked -> onChecked(user, isChecked) }
            container.addView(row)
        }
    }
}
