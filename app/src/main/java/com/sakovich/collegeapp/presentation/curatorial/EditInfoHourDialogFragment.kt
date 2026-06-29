package com.sakovich.collegeapp.presentation.curatorial

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.ScheduleRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class EditInfoHourDialogFragment : DialogFragment() {

    private var scheduleId: String = ""
    private var groupName: String = ""
    private var teacherName: String = ""
    private var teacherId: String = ""
    private var initialSchedule: ScheduleItem? = null

    private val scheduleRepository = ScheduleRepository()
    private val userRepository = UserRepository()
    private val calendar = Calendar.getInstance()

    private var selectedDate: String = ""
    private var students: List<User> = emptyList()
    private val selectedIds = mutableSetOf<String>()
    private var groupSchedules: List<ScheduleItem> = emptyList()
    private var semesters: List<AdminSemesterTemplate> = emptyList()
    private lateinit var timeDropdown: android.widget.AutoCompleteTextView
    private lateinit var timeLayout: TextInputLayout
    private lateinit var dateLayout: TextInputLayout
    private lateinit var dateInput: TextInputEditText
    private lateinit var studentsListContainer: LinearLayout

    companion object {
        fun newInstance(scheduleId: String, groupName: String, teacherName: String, teacherId: String, schedule: ScheduleItem): EditInfoHourDialogFragment {
            return EditInfoHourDialogFragment().apply {
                this.scheduleId = scheduleId
                this.groupName = groupName
                this.teacherName = teacherName
                this.teacherId = teacherId
                this.initialSchedule = schedule
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_add_info_hour, null)
        dateInput = view.findViewById(R.id.dateInput)
        timeDropdown = view.findViewById(R.id.timeDropdown)
        val topicInput = view.findViewById<TextInputEditText>(R.id.topicInput)
        val roomInput = view.findViewById<TextInputEditText>(R.id.roomInput)
        studentsListContainer = view.findViewById(R.id.studentsListContainer)

        val schedule = initialSchedule

        selectedDate = schedule?.date.takeUnless { it.isNullOrBlank() } ?: String.format(
            "%02d.%02d.%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )
        dateInput.setText(selectedDate)
        dateInput.setOnClickListener { showDatePicker(dateInput) }

        topicInput.setText(schedule?.subject ?: "")
        roomInput.setText(schedule?.room ?: "")

        selectedIds.clear()
        schedule?.assignedStudentIds?.let { selectedIds.addAll(it) }

        dateLayout = view.findViewById(R.id.dateLayout)
        timeLayout = view.findViewById(R.id.timeLayout)

        timeDropdown.isFocusable = false
        timeDropdown.isClickable = false
        timeDropdown.isEnabled = false
        timeLayout.isEnabled = false

        loadData(schedule?.time)

        val isCuratorial = schedule?.type == ScheduleType.CURATOR_HOUR
        val title = if (isCuratorial) "Редактировать кураторский час" else "Редактировать информационный час"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val topic = topicInput.text?.toString()?.trim().orEmpty()
                val room = roomInput.text?.toString()?.trim().orEmpty()
                val time = timeDropdown.text?.toString()?.trim().orEmpty()
                if (topic.isEmpty() || room.isEmpty() || time.isEmpty()) {
                    Toast.makeText(requireContext(), "Заполните тему, аудиторию и выберите время.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (selectedDate.isEmpty()) {
                    dateLayout.error = "Выберите дату"
                    return@setOnClickListener
                }
                if (!CuratorialHourDialogSupport.isDateAllowed(selectedDate, semesters)) {
                    dateLayout.error = CuratorialHourDialogSupport.SEMESTER_DATE_ERROR
                    return@setOnClickListener
                }
                dateLayout.error = null
                if (!CuratorialHourDialogSupport.isTimeSlotAvailable(groupSchedules, groupName, selectedDate, time, scheduleId)) {
                    timeLayout.error = "На это время уже есть занятие"
                    return@setOnClickListener
                }
                timeLayout.error = null
                val day = getDayNameFromDate(selectedDate)
                val ids = selectedIds.toList()
                val scheduleType = schedule?.type ?: ScheduleType.INFO_HOUR
                val updated = ScheduleItem(
                    day = day,
                    date = selectedDate,
                    time = time,
                    subject = topic,
                    teacherName = teacherName,
                    room = room,
                    type = scheduleType,
                    group = groupName,
                    createdBy = schedule?.createdBy?.ifBlank { teacherId } ?: teacherId,
                    createdByRole = schedule?.createdByRole?.ifBlank { "teacher" } ?: "teacher",
                    assignedStudentIds = ids
                )
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        scheduleRepository.updateSchedule(scheduleId, updated, teacherId)
                    }
                    val ctx = context ?: return@launch
                    if (ok) {
                        val msg = if (scheduleType == ScheduleType.CURATOR_HOUR) "Кураторский час обновлён." else "Информационный час обновлён."
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        parentFragmentManager.setFragmentResult(CuratorialInfoHoursFragment.REQUEST_INFO_ADDED, Bundle())
                        if (isAdded) dialog.dismiss()
                    } else {
                        Toast.makeText(ctx, "Не удалось сохранить изменения.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return dialog
    }

    private fun loadData(preferredTime: String?) {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val loaded = CuratorialHourDialogSupport.loadData(groupName)
                val list = userRepository.getStudentsForGroup(groupName)
                loaded to list
            }
            if (!isAdded) return@launch
            semesters = data.first.semesters
            groupSchedules = data.first.groupSchedules
            students = InfoHourStudentsView.sortBySurname(data.second)
            InfoHourStudentsView.bind(studentsListContainer, students, selectedIds) { user, checked ->
                if (checked) selectedIds.add(user.id) else selectedIds.remove(user.id)
            }
            CuratorialHourDialogSupport.applyDateValidation(dateLayout, selectedDate, semesters)
            refreshTimeSlots(preferredTime = preferredTime)
        }
    }

    private fun showDatePicker(dateInput: TextInputEditText) {
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

    private fun refreshTimeSlots(preferredTime: String? = null) {
        CuratorialHourDialogSupport.applyTimeSlotDropdown(
            context = requireContext(),
            timeDropdown = timeDropdown,
            timeLayout = timeLayout,
            schedules = groupSchedules,
            groupName = groupName,
            selectedDate = selectedDate,
            semesters = semesters,
            excludeScheduleId = scheduleId,
            preferredTime = preferredTime
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
