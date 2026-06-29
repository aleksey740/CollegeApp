package com.sakovich.collegeapp.presentation.schedule

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.databinding.DialogAddScheduleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sakovich.collegeapp.utils.ScheduleTypeLabels
import android.widget.AdapterView
import java.util.Calendar

class EditScheduleDialogFragment : DialogFragment() {

    private var _binding: DialogAddScheduleBinding? = null
    private val binding get() = _binding!!

    private var onScheduleUpdatedListener: ((ScheduleItem) -> Unit)? = null
    private var scheduleItem: ScheduleItem? = null
    private var lockGroupField: Boolean = false
    private var groupId: String = ""
    private var subjectOptions: List<String> = emptyList()
    private var teacherOptions: List<String> = emptyList()
    private var teacherNamesBySubject: Map<String, List<String>> = emptyMap()
    private var allTeacherNamesBySubject: Map<String, List<String>> = emptyMap()

    private val calendar = Calendar.getInstance()
    private var selectedDate = ""

    private val pairNumbers = listOf("1-я пара", "2-я пара", "3-я пара", "4-я пара", "5-я пара")
    private val lessonNumbers = listOf(
        "1-й урок", "2-й урок", "3-й урок", "4-й урок", "5-й урок", "6-й урок",
        "7-й урок", "8-й урок", "9-й урок", "10-й урок", "11-й урок"
    )

    companion object {
        fun newInstance(
            schedule: ScheduleItem,
            lockGroup: Boolean = false,
            groupId: String = "",
            subjectOptions: List<String> = emptyList(),
            teacherOptions: List<String> = emptyList(),
            teacherNamesBySubject: Map<String, List<String>> = emptyMap()
        ): EditScheduleDialogFragment {
            return EditScheduleDialogFragment().apply {
                this.scheduleItem = schedule
                this.lockGroupField = lockGroup
                this.groupId = groupId
                this.subjectOptions = subjectOptions
                this.teacherOptions = teacherOptions
                this.teacherNamesBySubject = teacherNamesBySubject
                this.allTeacherNamesBySubject = teacherNamesBySubject
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddScheduleBinding.inflate(layoutInflater)

        setupUI()
        setupClickListeners()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("✏️ Редактировать занятие")
            .setView(binding.root)
            .setPositiveButton("Сохранить") { dialog, which ->
                if (validateForm()) {
                    updateSchedule()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()
    }

    private fun setupUI() {
        val pairAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, pairNumbers)
        binding.pairNumberDropdown.setAdapter(pairAdapter)
        val lessonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, lessonNumbers)
        binding.lessonNumberDropdown.setAdapter(lessonAdapter)

        binding.pairOrLessonRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isPair = (checkedId == R.id.radioPair)
            binding.pairLayout.visibility = if (isPair) View.VISIBLE else View.GONE
            binding.lessonLayout.visibility = if (isPair) View.GONE else View.VISIBLE
        }
        binding.pairLayout.visibility = View.VISIBLE
        binding.lessonLayout.visibility = View.GONE

        binding.groupOrSubgroupRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isSubgroup = (checkedId == R.id.radioSubgroups)
            binding.subgroupLayout.visibility = if (isSubgroup) View.VISIBLE else View.GONE
        }

        updateTeacherDropdown(teacherOptions)
        val adapter2 = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, teacherOptions)
        binding.teacherDropdown2.setAdapter(adapter2)
        binding.teacherDropdown2.setOnClickListener { if (teacherOptions.isNotEmpty()) binding.teacherDropdown2.showDropDown() }

        scheduleItem?.let { schedule ->
            val subjectView = binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)
            subjectView.setText(schedule.subject, false)
            setupSubjectDropdown(subjectView)
            val forSubject = teacherNamesBySubject[schedule.subject]
            if (!forSubject.isNullOrEmpty()) updateTeacherDropdown(forSubject)
            binding.teacherDropdown.setText(schedule.teacherName, false)
            binding.roomEditText.setText(schedule.room)
            if (schedule.isSubgroup) {
                binding.radioSubgroups.isChecked = true
                binding.subgroupLayout.visibility = View.VISIBLE
                binding.teacherDropdown2.setText(schedule.teacherName2, false)
                binding.roomEditText2.setText(schedule.room2)
            } else {
                binding.radioWholeGroup.isChecked = true
                binding.subgroupLayout.visibility = View.GONE
            }
            binding.groupEditText.setText(schedule.group)
            if (lockGroupField) {
                binding.groupEditText.isEnabled = false
                binding.groupEditText.isFocusable = false
            }

            selectedDate = schedule.date
            binding.dateInput.setText(selectedDate)

            val isLesson = schedule.type == ScheduleType.LUNCH || schedule.time.contains("урок")
            if (isLesson) {
                binding.radioLesson.isChecked = true
                binding.pairLayout.visibility = View.GONE
                binding.lessonLayout.visibility = View.VISIBLE
                binding.lessonNumberDropdown.setText(schedule.time, false)
            } else {
                binding.radioPair.isChecked = true
                binding.pairLayout.visibility = View.VISIBLE
                binding.lessonLayout.visibility = View.GONE
                binding.pairNumberDropdown.setText(schedule.time, false)
            }

            val types = ScheduleTypeLabels.regularScheduleTypes.map { ScheduleTypeLabels.displayName(it) }
            val typesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
            typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.typeSpinner.adapter = typesAdapter

            val typeName = ScheduleTypeLabels.displayName(schedule.type)
            val typeIndex = types.indexOf(typeName)
            if (typeIndex >= 0) {
                binding.typeSpinner.setSelection(typeIndex)
            }

            binding.typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    applyScheduleTypeUi()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            applyScheduleTypeUi()
        }
    }

    private fun selectedType(): ScheduleType {
        val name = binding.typeSpinner.selectedItem as? String ?: return scheduleItem?.type ?: ScheduleType.LECTURE
        return ScheduleTypeLabels.fromDisplayName(name)
    }

    private fun isLunchMode(): Boolean = selectedType() == ScheduleType.LUNCH

    private fun applyScheduleTypeUi() {
        val lunch = isLunchMode()
        val detailVisibility = if (lunch) View.GONE else View.VISIBLE
        binding.subjectLayout.visibility = detailVisibility
        binding.groupOrSubgroupRadioGroup.visibility = detailVisibility
        binding.teacherLayout.visibility = detailVisibility
        binding.roomLayout.visibility = detailVisibility
        binding.pairOrLessonRadioGroup.visibility = detailVisibility
        binding.pairLayout.visibility = View.GONE
        if (lunch) {
            binding.radioLesson.isChecked = true
            binding.lessonLayout.visibility = View.VISIBLE
        } else {
            val isPair = binding.radioPair.isChecked
            binding.pairLayout.visibility = if (isPair) View.VISIBLE else View.GONE
            binding.lessonLayout.visibility = if (isPair) View.GONE else View.VISIBLE
        }
    }

    private fun updateTeacherDropdown(names: List<String>) {
        val list = names.ifEmpty { teacherOptions }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)
        binding.teacherDropdown.setAdapter(adapter)
        binding.teacherDropdown.setOnClickListener { if (list.isNotEmpty()) binding.teacherDropdown.showDropDown() }
    }

    private fun setupSubjectDropdown(subjectView: AutoCompleteTextView = binding.root.findViewById(R.id.subjectDropdown)) {
        if (subjectOptions.isEmpty()) return
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectOptions)
        subjectView.setAdapter(adapter)
        subjectView.setOnClickListener { subjectView.showDropDown() }
        subjectView.setOnItemClickListener { _, _, position, _ ->
            val subject = subjectOptions.getOrNull(position) ?: return@setOnItemClickListener
            val forSubject = teacherNamesBySubject[subject]
            if (!forSubject.isNullOrEmpty()) {
                updateTeacherDropdown(forSubject)
                if (forSubject.size == 1) binding.teacherDropdown.setText(forSubject.single(), false)
                else binding.teacherDropdown.setText("", false)
            } else {
                updateTeacherDropdown(teacherOptions)
                binding.teacherDropdown.setText("", false)
            }
        }
    }

    private fun reloadSubjectsForSelectedDate() {
        if (groupId.isBlank() || selectedDate.isBlank()) return
        val subjectView = binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)
        val keepSubject = subjectView.text?.toString()?.trim().orEmpty()
        val scheduleSubject = scheduleItem?.subject?.trim().orEmpty()
        lifecycleScope.launch {
            val options = withContext(Dispatchers.IO) {
                AdminRepository().getSubjectNamesForGroupOnDate(groupId, selectedDate)
            }
            val merged = buildList {
                addAll(options)
                if (keepSubject.isNotBlank() && keepSubject !in this) add(keepSubject)
                if (scheduleSubject.isNotBlank() && scheduleSubject !in this) add(scheduleSubject)
            }.distinct().sorted()
            subjectOptions = merged
            teacherNamesBySubject = allTeacherNamesBySubject.filterKeys { it in merged }
            setupSubjectDropdown(subjectView)
            if (keepSubject.isNotBlank() && keepSubject !in options && keepSubject != scheduleSubject) {
                subjectView.setText("", false)
            }
        }
    }

    private fun setupClickListeners() {
        binding.dateInput.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        var day = calendar.get(Calendar.DAY_OF_MONTH)
        if (selectedDate.isNotEmpty()) {
            val dateParts = selectedDate.split(".")
            if (dateParts.size == 3) {
                day = dateParts[0].toIntOrNull() ?: day
                month = (dateParts[1].toIntOrNull() ?: (month + 1)) - 1
                year = dateParts[2].toIntOrNull() ?: year
            }
        }
        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, dayOfMonth ->
                selectedDate = String.format("%02d.%02d.%04d", dayOfMonth, selectedMonth + 1, selectedYear)
                binding.dateInput.setText(selectedDate)
                reloadSubjectsForSelectedDate()
            },
            year,
            month,
            day
        ).show()
    }

    private fun getDayNameFromDate(dateStr: String): String {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull() ?: return "Понедельник"
                val m = (parts[1].toIntOrNull() ?: 1) - 1
                val y = parts[2].toIntOrNull() ?: 2025
                val c = Calendar.getInstance()
                c.set(y, m, d)
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

    private fun validateForm(): Boolean {
        val lunch = isLunchMode()
        if (!lunch) {
            val subject = binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)?.text?.toString()?.trim() ?: ""
            if (subject.isEmpty()) {
                Toast.makeText(requireContext(), if (subjectOptions.isEmpty()) "Введите предмет" else "Выберите предмет", Toast.LENGTH_SHORT).show()
                return false
            }
            if (binding.teacherDropdown.text.toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Выберите или введите преподавателя", Toast.LENGTH_SHORT).show()
                return false
            }
            if (binding.roomEditText.text.toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Введите номер аудитории", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        if (binding.groupEditText.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Введите группу", Toast.LENGTH_SHORT).show()
            return false
        }

        val isPair = !lunch && binding.radioPair.isChecked
        if (isPair) {
            if (binding.pairNumberDropdown.text.toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Выберите номер пары", Toast.LENGTH_SHORT).show()
                return false
            }
        } else {
            if (binding.lessonNumberDropdown.text.toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Выберите номер урока", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        if (!lunch && binding.radioSubgroups.isChecked) {
            if (binding.teacherDropdown2.text.toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Выберите преподавателя для подгруппы 2", Toast.LENGTH_SHORT).show()
                return false
            }
            if (binding.roomEditText2.text.toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Введите аудиторию для подгруппы 2", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    private fun updateSchedule() {
        scheduleItem?.let { originalSchedule ->
            val date = selectedDate.ifBlank { originalSchedule.date }
            val day = getDayNameFromDate(date)
            val lunch = isLunchMode()
            val time = if (lunch || !binding.radioPair.isChecked) {
                binding.lessonNumberDropdown.text.toString().trim()
            } else {
                binding.pairNumberDropdown.text.toString().trim()
            }.ifBlank { originalSchedule.time }
            val type = selectedType()
            val group = binding.groupEditText.text.toString().trim()

            val updatedSchedule = if (lunch) {
                originalSchedule.copy(
                    day = day,
                    date = date,
                    time = time,
                    subject = "Обед",
                    teacherName = "",
                    room = "",
                    isSubgroup = false,
                    teacherName2 = "",
                    room2 = "",
                    type = type,
                    group = group
                )
            } else {
                val subject = binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)?.text?.toString()?.trim() ?: ""
                val teacher = binding.teacherDropdown.text.toString().trim()
                val room = binding.roomEditText.text.toString().trim()
                val isSubgroup = binding.radioSubgroups.isChecked
                val teacher2 = if (isSubgroup) binding.teacherDropdown2.text.toString().trim() else ""
                val room2 = if (isSubgroup) binding.roomEditText2.text.toString().trim() else ""
                originalSchedule.copy(
                    day = day,
                    date = date,
                    time = time,
                    subject = subject,
                    teacherName = teacher,
                    room = room,
                    isSubgroup = isSubgroup,
                    teacherName2 = teacher2,
                    room2 = room2,
                    type = type,
                    group = group
                )
            }

            onScheduleUpdatedListener?.invoke(updatedSchedule)
        }
    }

    fun setOnScheduleUpdatedListener(listener: (ScheduleItem) -> Unit) {
        this.onScheduleUpdatedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
