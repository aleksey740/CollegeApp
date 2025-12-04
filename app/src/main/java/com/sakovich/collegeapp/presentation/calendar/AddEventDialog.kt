package com.sakovich.collegeapp.presentation.calendar

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sakovich.collegeapp.data.models.Event
import com.sakovich.collegeapp.data.models.EventType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.databinding.DialogAddEventBinding
import java.util.Calendar
import java.util.Date

class AddEventDialog : DialogFragment() {

    private var _binding: DialogAddEventBinding? = null
    private val binding get() = _binding!!

    private var onEventAddedListener: ((Event) -> Unit)? = null
    private var currentUser: User? = null

    private val calendar = Calendar.getInstance()
    private var selectedDate = Date()
    private var selectedStartTime = "10:00"
    private var selectedEndTime = "11:30"

    companion object {
        fun newInstance(user: User?): AddEventDialog {
            return AddEventDialog().apply {
                // Сохраняем пользователя через свойство вместо аргументов
                this.currentUser = user
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddEventBinding.inflate(layoutInflater)

        setupUI()
        setupClickListeners()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить событие")
            .setView(binding.root)
            .setPositiveButton("Добавить") { dialog, which ->
                if (validateForm()) {
                    createEvent()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()
    }

    private fun setupUI() {
        // Настраиваем спиннер типов событий
        val eventTypes = EventType.values().map { getEventTypeDisplayName(it) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, eventTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.eventTypeSpinner.adapter = adapter

        // Устанавливаем текущую дату и время по умолчанию
        updateDateText()
        updateTimeText()

        // Заполняем группу по умолчанию из данных пользователя
        binding.eventGroupEditText.setText(currentUser?.groupName ?: "")
    }

    private fun setupClickListeners() {
        // Выбор даты
        binding.dateInput.setOnClickListener {
            showDatePicker()
        }

        // Выбор времени начала
        binding.startTimeInput.setOnClickListener {
            showTimePicker(true)
        }

        // Выбор времени окончания
        binding.endTimeInput.setOnClickListener {
            showTimePicker(false)
        }
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                selectedDate = calendar.time
                updateDateText()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
        datePicker.show()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val timePicker = TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val timeString = String.format("%02d:%02d", hour, minute)
                if (isStartTime) {
                    selectedStartTime = timeString
                } else {
                    selectedEndTime = timeString
                }
                updateTimeText()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun updateDateText() {
        binding.dateInput.setText(android.text.format.DateFormat.format("dd.MM.yyyy", selectedDate).toString())
    }

    private fun updateTimeText() {
        binding.startTimeInput.setText(selectedStartTime)
        binding.endTimeInput.setText(selectedEndTime)
    }

    private fun getEventTypeDisplayName(type: EventType): String {
        return when (type) {
            EventType.LECTURE -> "Лекция"
            EventType.PRACTICE -> "Практика"
            EventType.EXAM -> "Экзамен"
            EventType.MEETING -> "Собрание"
            EventType.HOLIDAY -> "Выходной"
            EventType.OTHER -> "Другое"
        }
    }

    private fun getEventTypeFromDisplayName(displayName: String): EventType {
        return when (displayName) {
            "Лекция" -> EventType.LECTURE
            "Практика" -> EventType.PRACTICE
            "Экзамен" -> EventType.EXAM
            "Собрание" -> EventType.MEETING
            "Выходной" -> EventType.HOLIDAY
            else -> EventType.OTHER
        }
    }

    private fun validateForm(): Boolean {
        if (binding.eventTitleEditText.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Введите название события", Toast.LENGTH_SHORT).show()
            return false
        }

        if (binding.eventLocationEditText.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Введите место проведения", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedStartTime >= selectedEndTime) {
            Toast.makeText(requireContext(), "Время окончания должно быть позже времени начала", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun createEvent() {
        val title = binding.eventTitleEditText.text.toString().trim()
        val description = binding.eventDescriptionEditText.text.toString().trim()
        val location = binding.eventLocationEditText.text.toString().trim()
        val subject = binding.eventSubjectEditText.text.toString().trim()
        val groupName = binding.eventGroupEditText.text.toString().trim()
        val selectedType = binding.eventTypeSpinner.selectedItem as String
        val eventType = getEventTypeFromDisplayName(selectedType)

        val event = Event(
            title = title,
            description = description,
            date = selectedDate,
            startTime = selectedStartTime,
            endTime = selectedEndTime,
            type = eventType,
            subject = subject,
            teacherId = currentUser?.id ?: "",
            teacherName = currentUser?.fullName ?: "Неизвестно",
            groupId = currentUser?.groupId ?: "",
            groupName = groupName.ifEmpty { currentUser?.groupName ?: "Общее" },
            location = location,
            createdAt = Date(),
            createdBy = currentUser?.id ?: ""
        )

        onEventAddedListener?.invoke(event)
    }

    fun setOnEventAddedListener(listener: (Event) -> Unit) {
        this.onEventAddedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}