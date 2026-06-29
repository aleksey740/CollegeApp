package com.sakovich.collegeapp.presentation.curatorial

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.data.repositories.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AddCuratorialHourDialogFragment : DialogFragment() {

    private var groupName: String = ""
    private var teacherName: String = ""
    private var teacherId: String = ""
    private val scheduleRepository = ScheduleRepository()
    private val calendar = Calendar.getInstance()
    private var selectedDate = ""
    private var groupSchedules: List<ScheduleItem> = emptyList()
    private var semesters: List<AdminSemesterTemplate> = emptyList()
    private lateinit var timeDropdown: android.widget.AutoCompleteTextView
    private lateinit var timeLayout: TextInputLayout
    private lateinit var dateLayout: TextInputLayout
    private lateinit var dateInput: com.google.android.material.textfield.TextInputEditText

    companion object {
        fun newInstance(groupName: String, teacherName: String, teacherId: String): AddCuratorialHourDialogFragment {
            return AddCuratorialHourDialogFragment().apply {
                this.groupName = groupName
                this.teacherName = teacherName
                this.teacherId = teacherId
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_add_curatorial_hour, null)
        dateInput = view.findViewById(R.id.dateInput)
        timeDropdown = view.findViewById(R.id.timeDropdown)
        val topicInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.topicInput)
        val roomInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.roomInput)

        dateLayout = view.findViewById(R.id.dateLayout)
        timeLayout = view.findViewById(R.id.timeLayout)
        val topicLayout = view.findViewById<TextInputLayout>(R.id.topicLayout)
        val roomLayout = view.findViewById<TextInputLayout>(R.id.roomLayout)

        selectedDate = String.format(
            "%02d.%02d.%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )
        dateInput.setText(selectedDate)
        dateInput.setOnClickListener { showDatePicker(dateInput) }

        timeDropdown.isFocusable = false
        timeDropdown.isClickable = false
        timeDropdown.isEnabled = false
        timeLayout.isEnabled = false

        loadDataAndRefreshTimeSlots()

        fun validateForm(): Boolean {
            dateLayout.error = null
            timeLayout.error = null
            topicLayout.error = null
            roomLayout.error = null
            var hasError = false
            if (selectedDate.isEmpty()) {
                dateLayout.error = "Выберите дату"
                hasError = true
            } else if (!CuratorialHourDialogSupport.isDateAllowed(selectedDate, semesters)) {
                dateLayout.error = CuratorialHourDialogSupport.SEMESTER_DATE_ERROR
                hasError = true
            }
            val time = timeDropdown.text?.toString()?.trim().orEmpty()
            if (time.isEmpty()) {
                timeLayout.error = "Выберите пару или урок"
                hasError = true
            } else if (!CuratorialHourDialogSupport.isTimeSlotAvailable(groupSchedules, groupName, selectedDate, time)) {
                timeLayout.error = "На это время уже есть занятие"
                hasError = true
            }
            val topic = topicInput.text?.toString()?.trim().orEmpty()
            if (topic.isEmpty()) {
                topicLayout.error = "Введите тему"
                hasError = true
            }
            val room = roomInput.text?.toString()?.trim().orEmpty()
            if (room.isEmpty()) {
                roomLayout.error = "Укажите аудиторию"
                hasError = true
            }
            return !hasError
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить кураторский час")
            .setView(view)
            .setPositiveButton("Добавить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!validateForm()) return@setOnClickListener
                val topic = topicInput.text?.toString()?.trim().orEmpty()
                val room = roomInput.text?.toString()?.trim().orEmpty()
                val time = timeDropdown.text?.toString()?.trim().orEmpty()
                val day = getDayNameFromDate(selectedDate)
                val schedule = ScheduleItem(
                    day = day,
                    date = selectedDate,
                    time = time,
                    subject = topic,
                    teacherName = teacherName,
                    room = room,
                    type = ScheduleType.CURATOR_HOUR,
                    group = groupName,
                    createdBy = teacherId,
                    createdByRole = "teacher"
                )
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            scheduleRepository.addSchedule(schedule)
                        }
                        val ctx = context ?: return@launch
                        Toast.makeText(ctx, "Кураторский час добавлен в расписание.", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.setFragmentResult(CuratorialInfoHoursFragment.REQUEST_CURATORIAL_ADDED, Bundle())
                        if (isAdded) dialog.dismiss()
                    } catch (e: Exception) {
                        val ctx = context ?: return@launch
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return dialog
    }

    private fun showDatePicker(dateInput: com.google.android.material.textfield.TextInputEditText) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate = String.format("%02d.%02d.%04d", dayOfMonth, month + 1, year)
                dateInput.setText(selectedDate)
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                CuratorialHourDialogSupport.applyDateValidation(dateLayout, selectedDate, semesters)
                refreshTimeSlots()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadDataAndRefreshTimeSlots() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                CuratorialHourDialogSupport.loadData(groupName)
            }
            if (!isAdded) return@launch
            semesters = data.semesters
            groupSchedules = data.groupSchedules
            selectedDate = CuratorialHourDialogSupport.suggestInitialDate(semesters)
            if (::dateInput.isInitialized) {
                dateInput.setText(selectedDate)
            }
            val parts = selectedDate.split(".")
            if (parts.size == 3) {
                calendar.set(parts[2].toIntOrNull() ?: calendar.get(Calendar.YEAR), (parts[1].toIntOrNull() ?: 1) - 1, parts[0].toIntOrNull() ?: 1)
            }
            CuratorialHourDialogSupport.applyDateValidation(dateLayout, selectedDate, semesters)
            refreshTimeSlots()
        }
    }

    private fun refreshTimeSlots() {
        CuratorialHourDialogSupport.applyTimeSlotDropdown(
            context = requireContext(),
            timeDropdown = timeDropdown,
            timeLayout = timeLayout,
            schedules = groupSchedules,
            groupName = groupName,
            selectedDate = selectedDate,
            semesters = semesters
        )
    }

    private fun getDayNameFromDate(dateStr: String): String {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull() ?: return "Понедельник"
                val month = (parts[1].toIntOrNull() ?: 1) - 1
                val year = parts[2].toIntOrNull() ?: 2025
                val c = Calendar.getInstance()
                c.set(year, month, day)
                when (c.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Понедельник"
                    Calendar.TUESDAY -> "Вторник"
                    Calendar.WEDNESDAY -> "Среда"
                    Calendar.THURSDAY -> "Четверг"
                    Calendar.FRIDAY -> "Пятница"
                    Calendar.SATURDAY -> "Суббота"
                    else -> "Понедельник"
                }
            } else "Понедельник"
        } catch (_: Exception) { "Понедельник" }
    }
}
