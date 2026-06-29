package com.sakovich.collegeapp.presentation.absences

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.AbsenceReason
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.models.getAbsenceReasonDisplayName
import com.sakovich.collegeapp.data.repositories.AdminRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AddAbsenceDialogFragment : DialogFragment() {

    private var onAbsenceSavedListener: ((Absence, Boolean) -> Unit)? = null
    private var currentUser: User? = null
    private var students: List<User> = emptyList()
    private var subjectOptions: List<String> = emptyList()
    private var groupId: String = ""
    private var editingAbsence: Absence? = null
    private var prefillStudentId: String = ""
    private var prefillStudentName: String = ""
    private var prefillStudentGroup: String = ""
    private var prefillSubject: String = ""
    private var prefillDate: String = ""

    private lateinit var studentLayout: TextInputLayout
    private lateinit var subjectLayout: TextInputLayout
    private lateinit var dateLayout: TextInputLayout
    private lateinit var hoursLayout: TextInputLayout
    private lateinit var reasonLayout: TextInputLayout
    private lateinit var studentDropdown: AutoCompleteTextView
    private lateinit var subjectDropdown: AutoCompleteTextView
    private lateinit var dateInput: TextInputEditText
    private lateinit var hoursDropdown: AutoCompleteTextView
    private lateinit var reasonDropdown: AutoCompleteTextView
    private lateinit var excusedSwitch: SwitchMaterial
    private lateinit var commentInput: TextInputEditText

    private var selectedStudentId: String = ""
    private var selectedStudentName: String = ""
    private var selectedStudentGroup: String = ""
    private var selectedReason: AbsenceReason = AbsenceReason.WITHOUT_REASON
    private var selectedDate: Calendar = Calendar.getInstance()

    fun setOnAbsenceSavedListener(listener: (Absence, Boolean) -> Unit) {
        onAbsenceSavedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_absence, null)

        initViews(view)
        setupDropdowns()
        setupDatePicker()

        editingAbsence?.let { fillEditData(it) }
            ?: applyPrefill()

        val title = if (editingAbsence != null) "✏️ Редактировать пропуск" else "➕ Добавить пропуск"
        val positiveButton = if (editingAbsence != null) "Сохранить" else "Добавить"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(positiveButton, null)
            .setNegativeButton("Отмена", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saveAbsence()) dialog.dismiss()
            }
        }
        return dialog
    }

    private fun initViews(view: View) {
        studentLayout = view.findViewById(R.id.studentLayout)
        subjectLayout = view.findViewById(R.id.subjectLayout)
        dateLayout = view.findViewById(R.id.dateLayout)
        hoursLayout = view.findViewById(R.id.hoursLayout)
        reasonLayout = view.findViewById(R.id.reasonLayout)
        studentDropdown = view.findViewById(R.id.studentDropdown)
        subjectDropdown = view.findViewById(R.id.subjectDropdown)
        dateInput = view.findViewById(R.id.dateInput)
        hoursDropdown = view.findViewById(R.id.hoursDropdown)
        reasonDropdown = view.findViewById(R.id.reasonDropdown)
        excusedSwitch = view.findViewById(R.id.excusedSwitch)
        commentInput = view.findViewById(R.id.commentInput)

        updateDateText()
    }

    private fun setupDropdowns() {

        val studentNames = students.map { it.fullName.removeSuffix(" (Староста)").trim() }
        val studentAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        studentDropdown.setAdapter(studentAdapter)
        studentDropdown.setOnItemClickListener { _, _, position, _ ->
            val student = students[position]
            selectedStudentId = student.id
            selectedStudentName = student.fullName.removeSuffix(" (Староста)").trim()
            selectedStudentGroup = student.group
        }

        val hours = listOf("1", "2", "3", "4", "5", "6", "7", "8")
        val hoursAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, hours)
        hoursDropdown.setAdapter(hoursAdapter)

        applySubjectDropdownAdapter()
        reloadSubjectsForSelectedDate()

        val reasons = AbsenceReason.values().map { getAbsenceReasonDisplayName(it) }
        val reasonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, reasons)
        reasonDropdown.setAdapter(reasonAdapter)
        reasonDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedReason = AbsenceReason.values()[position]

            if (selectedReason == AbsenceReason.SICK || selectedReason == AbsenceReason.OFFICIAL) {
                excusedSwitch.isChecked = true
            }
        }
    }

    private fun applySubjectDropdownAdapter() {
        if (subjectOptions.isNotEmpty()) {
            val subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectOptions)
            subjectDropdown.setAdapter(subjectAdapter)
            subjectDropdown.inputType = android.text.InputType.TYPE_NULL
        } else {
            subjectDropdown.setAdapter(null)
            subjectDropdown.inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
    }

    private fun reloadSubjectsForSelectedDate() {
        if (groupId.isBlank()) return
        val dateStr = dateInput.text?.toString()?.trim().orEmpty()
        if (dateStr.isBlank()) return
        lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) {
                AdminRepository().getSubjectNamesForGroupOnDate(groupId, dateStr)
            }
            val keepCurrent = subjectDropdown.text?.toString()?.trim().orEmpty()
            val editingSubject = editingAbsence?.subject?.trim().orEmpty()
            val options = buildList {
                addAll(names)
                if (keepCurrent.isNotBlank() && keepCurrent !in this) add(keepCurrent)
                if (editingSubject.isNotBlank() && editingSubject !in this) add(editingSubject)
            }.distinct().sorted()
            subjectOptions = options
            applySubjectDropdownAdapter()
            if (keepCurrent.isNotBlank() && keepCurrent !in options) {
                subjectDropdown.setText("", false)
            }
        }
    }

    private fun setupDatePicker() {
        dateInput.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedDate.set(year, month, day)
                    updateDateText()
                    reloadSubjectsForSelectedDate()
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateDateText() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        dateInput.setText(dateFormat.format(selectedDate.time))
    }

    private fun fillEditData(absence: Absence) {
        selectedStudentId = absence.studentId
        selectedStudentName = absence.studentName
        selectedStudentGroup = absence.studentGroup
        selectedReason = absence.reason

        val studentIndex = students.indexOfFirst { it.id == absence.studentId }
        if (studentIndex >= 0) {
            studentDropdown.setText(students[studentIndex].fullName.removeSuffix(" (Староста)").trim(), false)
        } else {
            studentDropdown.setText(absence.studentName.removeSuffix(" (Староста)").trim(), false)
        }

        subjectDropdown.setText(absence.subject, false)
        hoursDropdown.setText(absence.hours.toString(), false)
        reasonDropdown.setText(getAbsenceReasonDisplayName(absence.reason), false)
        excusedSwitch.isChecked = absence.isExcused
        commentInput.setText(absence.comment)

        try {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            selectedDate.time = dateFormat.parse(absence.date) ?: Date()
            updateDateText()
        } catch (e: Exception) {

        }
    }

    private fun applyPrefill() {
        if (prefillStudentId.isEmpty() && prefillSubject.isEmpty()) return
        if (prefillStudentId.isNotEmpty()) {
            studentDropdown.setText(prefillStudentName.removeSuffix(" (Староста)").trim(), false)
            selectedStudentId = prefillStudentId
            selectedStudentName = prefillStudentName.removeSuffix(" (Староста)").trim()
            selectedStudentGroup = prefillStudentGroup
        }
        if (prefillSubject.isNotEmpty()) subjectDropdown.setText(prefillSubject, false)
        if (prefillDate.isNotEmpty()) {
            try {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                selectedDate.time = dateFormat.parse(prefillDate) ?: Date()
                dateInput.setText(prefillDate)
            } catch (_: Exception) { }
        }
    }

    private fun saveAbsence(): Boolean {
        val subject = subjectDropdown.text.toString().trim()
        val date = dateInput.text.toString().trim()
        val hoursStr = hoursDropdown.text.toString().trim()
        val reasonStr = reasonDropdown.text.toString().trim()
        val isExcused = excusedSwitch.isChecked
        val comment = commentInput.text.toString().trim()

        studentLayout.error = null
        subjectLayout.error = null
        dateLayout.error = null
        hoursLayout.error = null
        reasonLayout.error = null

        var hasError = false
        if (selectedStudentId.isEmpty()) {
            studentLayout.error = "Выберите учащегося"
            hasError = true
        }
        if (subject.isEmpty()) {
            subjectLayout.error = "Укажите предмет"
            hasError = true
        }
        if (date.isEmpty()) {
            dateLayout.error = "Укажите дату"
            hasError = true
        }
        if (hoursStr.isEmpty()) {
            hoursLayout.error = "Укажите количество часов"
            hasError = true
        }
        if (reasonStr.isEmpty()) {
            reasonLayout.error = "Выберите причину"
            hasError = true
        }
        if (hasError) return false

        val hours = hoursStr.toIntOrNull() ?: 2
        val absence = Absence(
            id = editingAbsence?.id ?: "",
            studentId = selectedStudentId,
            studentName = selectedStudentName,
            studentGroup = selectedStudentGroup,
            subject = subject,
            date = date,
            hours = hours,
            reason = selectedReason,
            comment = comment,
            createdBy = editingAbsence?.createdBy?.ifBlank { currentUser?.id.orEmpty() }
                ?: currentUser?.id.orEmpty(),
            createdByName = editingAbsence?.createdByName?.ifBlank { currentUser?.fullName.orEmpty() }
                ?: currentUser?.fullName.orEmpty(),
            createdByRole = editingAbsence?.createdByRole?.ifBlank { currentUser?.role.orEmpty() }
                ?: currentUser?.role.orEmpty(),
            isExcused = isExcused
        )

        onAbsenceSavedListener?.invoke(absence, editingAbsence != null)
        return true
    }

    companion object {
        fun newInstance(
            currentUser: User?,
            students: List<User>,
            subjectOptions: List<String> = emptyList(),
            groupId: String = "",
            editingAbsence: Absence? = null,
            prefillStudentId: String = "",
            prefillStudentName: String = "",
            prefillStudentGroup: String = "",
            prefillSubject: String = "",
            prefillDate: String = ""
        ): AddAbsenceDialogFragment {
            return AddAbsenceDialogFragment().apply {
                this.currentUser = currentUser
                this.students = students
                this.subjectOptions = subjectOptions
                this.groupId = groupId
                this.editingAbsence = editingAbsence
                this.prefillStudentId = prefillStudentId
                this.prefillStudentName = prefillStudentName
                this.prefillStudentGroup = prefillStudentGroup
                this.prefillSubject = prefillSubject
                this.prefillDate = prefillDate
            }
        }
    }
}
