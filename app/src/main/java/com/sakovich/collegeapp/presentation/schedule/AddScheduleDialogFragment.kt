package com.sakovich.collegeapp.presentation.schedule

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import com.sakovich.collegeapp.utils.ScheduleTimeSlotRules
import com.sakovich.collegeapp.utils.ScheduleTypeLabels
import java.util.Calendar

class AddScheduleDialogFragment : DialogFragment() {

    private var _binding: DialogAddScheduleBinding? = null
    private val binding get() = _binding!!

    private var onScheduleAddedListener: ((ScheduleItem) -> Unit)? = null
    private var defaultGroup: String = ""
    private var groupId: String = ""
    private var subjectOptions: List<String> = emptyList()
    private var teacherNames: List<String> = emptyList()
    private var teacherNamesBySubject: Map<String, List<String>> = emptyMap()
    private var allTeacherNamesBySubject: Map<String, List<String>> = emptyMap()
    private var initialDate: String = ""
    private var usedTimeSlots: List<String> = emptyList()
    private var datesWithLunch: Set<String> = emptySet()

    private val calendar = Calendar.getInstance()
    private var selectedDate = ""
    private var currentSubjectTeachers: List<String> = emptyList()

    private val pairNumbers = listOf("1-я пара", "2-я пара", "3-я пара", "4-я пара", "5-я пара")
    private val lessonNumbers = listOf(
        "1-й урок", "2-й урок", "3-й урок", "4-й урок", "5-й урок", "6-й урок",
        "7-й урок", "8-й урок", "9-й урок", "10-й урок", "11-й урок"
    )

    private fun blockedSlots(): Set<String> = ScheduleTimeSlotRules.blockedSlots(usedTimeSlots)

    private fun selectedType(): ScheduleType {
        val name = binding.typeSpinner.selectedItem as? String ?: return ScheduleType.LECTURE
        return ScheduleTypeLabels.fromDisplayName(name)
    }

    private fun isLunchMode(): Boolean = selectedType() == ScheduleType.LUNCH

    companion object {
        private const val KEY_USED_TIME_SLOTS = "usedTimeSlots"
        private const val KEY_DATES_WITH_LUNCH = "datesWithLunch"

        fun newInstance(
            defaultGroup: String,
            groupId: String = "",
            subjectOptions: List<String> = emptyList(),
            teacherNames: List<String> = emptyList(),
            teacherNamesBySubject: Map<String, List<String>> = emptyMap(),
            initialDate: String = "",
            usedTimeSlots: List<String> = emptyList(),
            datesWithLunch: Set<String> = emptySet()
        ): AddScheduleDialogFragment {
            return AddScheduleDialogFragment().apply {
                this.defaultGroup = defaultGroup
                this.groupId = groupId
                this.subjectOptions = subjectOptions
                this.teacherNames = teacherNames
                this.teacherNamesBySubject = teacherNamesBySubject
                this.allTeacherNamesBySubject = teacherNamesBySubject
                this.initialDate = initialDate
                this.usedTimeSlots = usedTimeSlots
                this.datesWithLunch = datesWithLunch
                arguments = (arguments ?: Bundle()).apply {
                    putStringArrayList(KEY_USED_TIME_SLOTS, ArrayList(usedTimeSlots))
                    putStringArrayList(KEY_DATES_WITH_LUNCH, ArrayList(datesWithLunch))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getStringArrayList(KEY_USED_TIME_SLOTS)?.let { usedTimeSlots = it }
        arguments?.getStringArrayList(KEY_DATES_WITH_LUNCH)?.let { datesWithLunch = it.toSet() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddScheduleBinding.inflate(layoutInflater)
        setupUI()
        setupClickListeners()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить занятие")
            .setView(binding.root)
            .setPositiveButton("Добавить", null)
            .setNegativeButton("Отмена", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateForm()) {
                    createSchedule()
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    private fun setupUI() {
        refreshTypeSpinner()
        binding.typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyScheduleTypeUi()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        refreshTimeDropdowns()
        binding.pairOrLessonRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isLunchMode()) return@setOnCheckedChangeListener
            val isPair = checkedId == R.id.radioPair
            binding.pairLayout.visibility = if (isPair) View.VISIBLE else View.GONE
            binding.lessonLayout.visibility = if (isPair) View.GONE else View.VISIBLE
        }

        binding.groupOrSubgroupRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isSubgroup = checkedId == R.id.radioSubgroups
            binding.subgroupLayout.visibility = if (isSubgroup) View.VISIBLE else View.GONE
            if (isSubgroup && currentSubjectTeachers.size >= 2) {
                binding.teacherDropdown.setText(currentSubjectTeachers.getOrNull(0).orEmpty(), false)
                binding.teacherDropdown2.setText(currentSubjectTeachers.getOrNull(1).orEmpty(), false)
                updateTeacherDropdown2(currentSubjectTeachers)
            } else if (!isSubgroup) {
                binding.teacherDropdown2.setText("", false)
                binding.roomEditText2.setText("")
            }
        }
        binding.subgroupLayout.visibility = View.GONE

        updateTeacherDropdown(teacherNames)
        updateTeacherDropdown2(teacherNames)

        binding.groupEditText.setText(defaultGroup)
        if (defaultGroup.isNotBlank()) {
            binding.groupEditText.isEnabled = false
            binding.groupEditText.isFocusable = false
        }

        setupSubjectDropdown()

        if (initialDate.isNotBlank()) {
            selectedDate = initialDate
            val parts = initialDate.split(".")
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
                val month = (parts[1].toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
                val year = parts[2].toIntOrNull() ?: calendar.get(Calendar.YEAR)
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
            }
        } else {
            selectedDate = String.format(
                "%02d.%02d.%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        }
        binding.dateInput.setText(selectedDate)
        applyScheduleTypeUi()
    }

    private fun refreshTypeSpinner() {
        val excludeLunch = selectedDate in datesWithLunch
        val types = ScheduleTypeLabels.displayNamesForAddDialog(excludeLunch)
        val typesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val previous = binding.typeSpinner.selectedItem as? String
        binding.typeSpinner.adapter = typesAdapter
        if (previous != null && previous in types) {
            val index = types.indexOf(previous)
            binding.typeSpinner.setSelection(index)
        }
    }

    private fun refreshTimeDropdowns() {
        val blocked = blockedSlots()
        val availableLessons = lessonNumbers.filter { it !in blocked }
        val lessonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, availableLessons)
        binding.lessonNumberDropdown.setAdapter(lessonAdapter)
        val currentLesson = binding.lessonNumberDropdown.text?.toString()?.trim().orEmpty()
        binding.lessonNumberDropdown.setText(
            when {
                currentLesson in availableLessons -> currentLesson
                availableLessons.isNotEmpty() -> availableLessons.first()
                else -> ""
            },
            false
        )

        if (!isLunchMode()) {
            val availablePairs = pairNumbers.filter { it !in blocked }
            val pairAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, availablePairs)
            binding.pairNumberDropdown.setAdapter(pairAdapter)
            val currentPair = binding.pairNumberDropdown.text?.toString()?.trim().orEmpty()
            binding.pairNumberDropdown.setText(
                when {
                    currentPair in availablePairs -> currentPair
                    availablePairs.isNotEmpty() -> availablePairs.first()
                    else -> ""
                },
                false
            )
        }
    }

    private fun applyScheduleTypeUi() {
        val lunch = isLunchMode()
        val detailVisibility = if (lunch) View.GONE else View.VISIBLE

        binding.subjectLayout.visibility = detailVisibility
        binding.groupOrSubgroupRadioGroup.visibility = detailVisibility
        binding.teacherLayout.visibility = detailVisibility
        binding.roomLayout.visibility = detailVisibility
        binding.subgroupLayout.visibility = if (lunch) View.GONE else binding.subgroupLayout.visibility
        binding.pairOrLessonRadioGroup.visibility = detailVisibility
        binding.pairLayout.visibility = View.GONE

        if (lunch) {
            binding.radioLesson.isChecked = true
            binding.lessonLayout.visibility = View.VISIBLE
            refreshTimeDropdowns()
        } else {
            val isPair = binding.radioPair.isChecked
            binding.pairLayout.visibility = if (isPair) View.VISIBLE else View.GONE
            binding.lessonLayout.visibility = if (isPair) View.GONE else View.VISIBLE
            refreshTimeDropdowns()
        }
    }

    private fun updateTeacherDropdown(names: List<String>) {
        val list = names.ifEmpty { teacherNames }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)
        binding.teacherDropdown.setAdapter(adapter)
        binding.teacherDropdown.setOnClickListener { if (list.isNotEmpty()) binding.teacherDropdown.showDropDown() }
    }

    private fun updateTeacherDropdown2(names: List<String>) {
        val list = names.ifEmpty { teacherNames }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)
        binding.teacherDropdown2.setAdapter(adapter)
        binding.teacherDropdown2.setOnClickListener { if (list.isNotEmpty()) binding.teacherDropdown2.showDropDown() }
    }

    private fun setupSubjectDropdown() {
        val subjectDropdown = binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)
        if (subjectOptions.isEmpty()) return
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectOptions)
        subjectDropdown.setAdapter(adapter)
        subjectDropdown.setOnClickListener { subjectDropdown.showDropDown() }
        subjectDropdown.setOnItemClickListener { _, _, position, _ ->
            val subject = subjectOptions.getOrNull(position) ?: return@setOnItemClickListener
            val forSubject = teacherNamesBySubject[subject] ?: emptyList()
            currentSubjectTeachers = forSubject.ifEmpty { teacherNames }
            updateTeacherDropdown(currentSubjectTeachers)
            updateTeacherDropdown2(currentSubjectTeachers)
            if (currentSubjectTeachers.size == 1) {
                binding.teacherDropdown.setText(currentSubjectTeachers.single(), false)
            } else if (currentSubjectTeachers.size >= 2 && binding.radioSubgroups.isChecked) {
                binding.teacherDropdown.setText(currentSubjectTeachers[0], false)
                binding.teacherDropdown2.setText(currentSubjectTeachers[1], false)
            }
        }
    }

    private fun reloadSubjectsForSelectedDate() {
        if (groupId.isBlank() || selectedDate.isBlank()) return
        val keepSubject = binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)
            .text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            val options = withContext(Dispatchers.IO) {
                AdminRepository().getSubjectNamesForGroupOnDate(groupId, selectedDate)
            }
            subjectOptions = options
            teacherNamesBySubject = allTeacherNamesBySubject.filterKeys { it in options }
            setupSubjectDropdown()
            if (keepSubject.isNotBlank() && keepSubject !in options) {
                binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown).setText("", false)
            }
        }
    }

    private fun setupClickListeners() {
        binding.dateInput.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate = String.format("%02d.%02d.%04d", dayOfMonth, month + 1, year)
                binding.dateInput.setText(selectedDate)
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                refreshTypeSpinner()
                refreshTimeDropdowns()
                applyScheduleTypeUi()
                reloadSubjectsForSelectedDate()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
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

    private fun validateForm(): Boolean {
        binding.dateLayout.error = null
        binding.pairNumberLayout.error = null
        binding.lessonNumberLayout.error = null
        binding.subjectLayout.error = null
        binding.teacherLayout.error = null
        binding.roomLayout.error = null
        binding.teacherLayout2.error = null
        binding.roomLayout2.error = null
        binding.groupLayout.error = null

        var hasError = false
        if (selectedDate.isEmpty()) {
            binding.dateLayout.error = "Выберите дату занятия"
            hasError = true
        }

        val lunch = isLunchMode()
        if (lunch && selectedDate in datesWithLunch) {
            binding.dateLayout.error = "На эту дату обед уже добавлен"
            hasError = true
        }

        val timeSlot = if (lunch || !binding.radioPair.isChecked) {
            binding.lessonNumberDropdown.text.toString().trim()
        } else {
            binding.pairNumberDropdown.text.toString().trim()
        }
        val blocked = blockedSlots()

        if (timeSlot.isEmpty()) {
            if (lunch || !binding.radioPair.isChecked) {
                binding.lessonNumberLayout.error = "Выберите номер урока"
            } else {
                binding.pairNumberLayout.error = "Выберите номер пары"
            }
            hasError = true
        } else if (timeSlot in blocked) {
            if (lunch || !binding.radioPair.isChecked) {
                binding.lessonNumberLayout.error = "Этот слот уже занят"
            } else {
                binding.pairNumberLayout.error = "Этот слот уже занят"
            }
            hasError = true
        }

        if (!lunch) {
            val subject = getSubjectText()
            if (subject.isEmpty()) {
                binding.subjectLayout.error = if (subjectOptions.isEmpty()) "Добавьте предметы в Админ → Каталоги" else "Выберите предмет"
                hasError = true
            }
            if (binding.teacherDropdown.text.toString().trim().isEmpty()) {
                binding.teacherLayout.error = "Выберите или введите преподавателя"
                hasError = true
            }
            if (binding.roomEditText.text.toString().trim().isEmpty()) {
                binding.roomLayout.error = "Введите номер аудитории"
                hasError = true
            }
            if (binding.groupEditText.text.toString().trim().isEmpty()) {
                binding.groupLayout.error = "Укажите группу"
                hasError = true
            }
            if (binding.radioSubgroups.isChecked) {
                if (binding.teacherDropdown2.text.toString().trim().isEmpty()) {
                    binding.teacherLayout2.error = "Выберите преподавателя для подгруппы 2"
                    hasError = true
                }
                if (binding.roomEditText2.text.toString().trim().isEmpty()) {
                    binding.roomLayout2.error = "Введите аудиторию для подгруппы 2"
                    hasError = true
                }
            }
        } else if (defaultGroup.isBlank() && binding.groupEditText.text.toString().trim().isEmpty()) {
            binding.groupLayout.error = "Укажите группу"
            hasError = true
        }

        return !hasError
    }

    private fun getSubjectText(): String {
        return binding.root.findViewById<AutoCompleteTextView>(R.id.subjectDropdown)?.text?.toString()?.trim() ?: ""
    }

    private fun createSchedule() {
        val day = getDayNameFromDate(selectedDate)
        val lunch = isLunchMode()
        val type = selectedType()
        val time = if (lunch || !binding.radioPair.isChecked) {
            binding.lessonNumberDropdown.text.toString().trim()
        } else {
            binding.pairNumberDropdown.text.toString().trim()
        }
        val group = binding.groupEditText.text.toString().trim().ifBlank { defaultGroup }

        val schedule = if (lunch) {
            ScheduleItem(
                day = day,
                date = selectedDate,
                time = time,
                subject = "Обед",
                teacherName = "",
                room = "",
                type = type,
                group = group
            )
        } else {
            ScheduleItem(
                day = day,
                date = selectedDate,
                time = time,
                subject = getSubjectText(),
                teacherName = binding.teacherDropdown.text.toString().trim(),
                room = binding.roomEditText.text.toString().trim(),
                isSubgroup = binding.radioSubgroups.isChecked,
                teacherName2 = if (binding.radioSubgroups.isChecked) binding.teacherDropdown2.text.toString().trim() else "",
                room2 = if (binding.radioSubgroups.isChecked) binding.roomEditText2.text.toString().trim() else "",
                type = type,
                group = group
            )
        }

        onScheduleAddedListener?.invoke(schedule)
    }

    fun setOnScheduleAddedListener(listener: (ScheduleItem) -> Unit) {
        onScheduleAddedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
