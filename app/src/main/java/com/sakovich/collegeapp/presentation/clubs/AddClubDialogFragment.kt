package com.sakovich.collegeapp.presentation.clubs

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Club
import com.sakovich.collegeapp.data.models.ClubLeaderEntry
import com.sakovich.collegeapp.data.models.ClubType
import com.sakovich.collegeapp.data.repositories.ClubLeaderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AddClubDialogFragment : DialogFragment() {

    private var editingClub: Club? = null
    private var teacherId: String = ""
    private var teacherName: String = ""
    private var groupId: String = ""
    private var groupName: String = ""
    private var onSaved: ((Club) -> Unit)? = null
    private var currentLeaders: List<ClubLeaderEntry> = emptyList()
    private val clubLeaderRepository = ClubLeaderRepository()

    companion object {
        private const val KEY_TEACHER_ID = "teacherId"
        private const val KEY_TEACHER_NAME = "teacherName"
        private const val KEY_GROUP_ID = "groupId"
        private const val KEY_GROUP_NAME = "groupName"
        private const val KEY_INITIAL_TYPE = "initialType"

        fun newInstance(
            teacherId: String,
            teacherName: String,
            editingClub: Club? = null,
            groupId: String = "",
            groupName: String = "",
            initialType: ClubType? = null
        ): AddClubDialogFragment {
            return AddClubDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_TEACHER_ID, teacherId)
                    putString(KEY_TEACHER_NAME, teacherName)
                    putString(KEY_GROUP_ID, groupId)
                    putString(KEY_GROUP_NAME, groupName)
                    initialType?.let { putString(KEY_INITIAL_TYPE, when (it) { ClubType.SECTION -> "Секция"; ClubType.ELECTIVE -> "Факультатив"; else -> "Кружок" }) }
                }
                this.editingClub = editingClub
                this.teacherId = teacherId
                this.teacherName = teacherName
                this.groupId = groupId
                this.groupName = groupName
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_club, null)

        val nameEditText = view.findViewById<EditText>(R.id.nameEditText)
        val descriptionEditText = view.findViewById<EditText>(R.id.descriptionEditText)
        val typeDropdown = view.findViewById<AutoCompleteTextView>(R.id.typeDropdown)
        val leaderDropdown = view.findViewById<AutoCompleteTextView>(R.id.leaderDropdown)
        val nextSessionDateEditText = view.findViewById<EditText>(R.id.nextSessionDateEditText)
        val nextSessionTimeEditText = view.findViewById<EditText>(R.id.nextSessionTimeEditText)
        val locationEditText = view.findViewById<EditText>(R.id.locationEditText)
        val maxParticipantsEditText = view.findViewById<EditText>(R.id.maxParticipantsEditText)

        val dayMon = view.findViewById<CheckBox>(R.id.dayMon)
        val dayTue = view.findViewById<CheckBox>(R.id.dayTue)
        val dayWed = view.findViewById<CheckBox>(R.id.dayWed)
        val dayThu = view.findViewById<CheckBox>(R.id.dayThu)
        val dayFri = view.findViewById<CheckBox>(R.id.dayFri)
        val daySat = view.findViewById<CheckBox>(R.id.daySat)
        val startTimeEditText = view.findViewById<TextInputEditText>(R.id.startTimeEditText)
        val endTimeEditText = view.findViewById<TextInputEditText>(R.id.endTimeEditText)

        val typeItems = listOf("Кружок", "Секция", "Факультатив")
        typeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, typeItems))
        val initialTypeStr = arguments?.getString(KEY_INITIAL_TYPE)
        typeDropdown.setText(initialTypeStr ?: "Кружок", false)
        typeDropdown.setOnClickListener { typeDropdown.showDropDown() }
        typeDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) typeDropdown.showDropDown()
        }
        leaderDropdown.setOnClickListener { leaderDropdown.showDropDown() }
        leaderDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) leaderDropdown.showDropDown()
        }

        fun typeFromText(s: String): ClubType = when (s.trim()) {
            "Секция" -> ClubType.SECTION
            "Факультатив" -> ClubType.ELECTIVE
            else -> ClubType.CLUB
        }
        val ownerGroupId = arguments?.getString(KEY_GROUP_ID).orEmpty().ifBlank { groupId }
        val ownerGroupName = arguments?.getString(KEY_GROUP_NAME).orEmpty().ifBlank { groupName }

        fun loadLeadersForType(type: ClubType) {
            lifecycleScope.launch {
                val list = withContext(Dispatchers.IO) {
                    clubLeaderRepository.getByType(type, ownerGroupId, ownerGroupName)
                }
                currentLeaders = list
                val names = list.map { it.teacherName.ifBlank { it.teacherId } }
                leaderDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names.ifEmpty { listOf("Нет руководителей в справочнике") }))
            }
        }
        typeDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                loadLeadersForType(typeFromText(s?.toString() ?: ""))
                leaderDropdown.setText("", false)
            }
        })

        fun showTimePicker(field: TextInputEditText, defaultHour: Int, defaultMinute: Int) {
            TimePickerDialog(requireContext(), { _, hour, minute ->
                field.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
            }, defaultHour, defaultMinute, true).show()
        }
        startTimeEditText.setOnClickListener {
            val (h, m) = parseTime(startTimeEditText.text.toString()) ?: (14 to 0)
            showTimePicker(startTimeEditText, h, m)
        }
        endTimeEditText.setOnClickListener {
            val (h, m) = parseTime(endTimeEditText.text.toString()) ?: (16 to 0)
            showTimePicker(endTimeEditText, h, m)
        }

        editingClub?.let { club ->
            nameEditText.setText(club.name)
            descriptionEditText.setText(club.description)
            typeDropdown.setText(
                when (club.type) {
                    ClubType.CLUB -> "Кружок"
                    ClubType.SECTION -> "Секция"
                    ClubType.ELECTIVE -> "Факультатив"
                }, false
            )
            nextSessionDateEditText.setText(club.nextSessionDate)
            nextSessionTimeEditText.setText(club.nextSessionTime)
            locationEditText.setText(club.location)
            maxParticipantsEditText.setText(club.maxParticipants.toString())
            parseScheduleToUi(club.schedule, dayMon, dayTue, dayWed, dayThu, dayFri, daySat, startTimeEditText, endTimeEditText)
        }

        val nameLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.nameLayout)
        val typeLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.typeLayout)
        val leaderLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.leaderLayout)
        val startTimeLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.startTimeLayout)
        val endTimeLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.endTimeLayout)
        val locationLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.locationLayout)

        fun validateForm(): Boolean {
            nameLayout.error = null
            typeLayout.error = null
            leaderLayout.error = null
            startTimeLayout.error = null
            endTimeLayout.error = null
            locationLayout.error = null
            var hasError = false
            if (nameEditText.text.toString().trim().isEmpty()) {
                nameLayout.error = "Введите название"
                hasError = true
            }
            val typeStr = typeDropdown.text.toString().trim()
            if (typeStr.isEmpty() || typeStr !in typeItems) {
                typeLayout.error = "Выберите тип"
                hasError = true
            }
            if (leaderDropdown.text.toString().trim().isEmpty()) {
                leaderLayout.error = "Выберите или введите руководителя"
                hasError = true
            }
            val anyDay = dayMon.isChecked || dayTue.isChecked || dayWed.isChecked || dayThu.isChecked || dayFri.isChecked || daySat.isChecked
            val startTime = startTimeEditText.text.toString().trim()
            val endTime = endTimeEditText.text.toString().trim()
            if (!anyDay || startTime.isEmpty() || endTime.isEmpty()) {
                startTimeLayout.error = "Выберите дни и укажите время начала и окончания"
                hasError = true
            }
            if (locationEditText.text.toString().trim().isEmpty()) {
                locationLayout.error = "Укажите аудиторию или место"
                hasError = true
            }
            return !hasError
        }

        val title = if (editingClub == null) "Создать" else "Редактировать"
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("$title запись")
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            loadLeadersForType(typeFromText(typeDropdown.text.toString()))
            editingClub?.let { club ->
                leaderDropdown.setText(club.teacherName.ifBlank { club.teacherId }, false)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!validateForm()) return@setOnClickListener
                val ownerId = arguments?.getString(KEY_TEACHER_ID).orEmpty()
                val ownerName = arguments?.getString(KEY_TEACHER_NAME).orEmpty()
                val ownerGroupId = arguments?.getString(KEY_GROUP_ID).orEmpty()
                val ownerGroupName = arguments?.getString(KEY_GROUP_NAME).orEmpty()
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()
                val typeStr = typeDropdown.text.toString()
                val type = when (typeStr) {
                    "Секция" -> ClubType.SECTION
                    "Факультатив" -> ClubType.ELECTIVE
                    else -> ClubType.CLUB
                }
                val schedule = buildScheduleFromUi(dayMon, dayTue, dayWed, dayThu, dayFri, daySat, startTimeEditText, endTimeEditText)
                val startTime = startTimeEditText.text.toString().trim().ifBlank { endTimeEditText.text.toString().trim() }
                val nextFromSchedule = ClubScheduleHelper.computeNextFromSchedule(schedule)
                val nextSessionDate = normalizeDate(nextSessionDateEditText.text.toString().trim())
                    .ifBlank { nextFromSchedule?.first.orEmpty() }
                val nextSessionTime = startTime.ifBlank { nextFromSchedule?.second.orEmpty() }
                val location = locationEditText.text.toString().trim()
                val maxParticipants = maxParticipantsEditText.text.toString().trim().toIntOrNull() ?: 30
                val old = editingClub
                val selectedLeaderName = leaderDropdown.text.toString().trim()
                val selectedLeader = currentLeaders.find { it.teacherName == selectedLeaderName || (it.teacherName.isBlank() && it.teacherId == selectedLeaderName) }
                val resultTeacherId = old?.teacherId?.takeIf { it.isNotBlank() } ?: ownerId
                val resultTeacherName = when {
                    selectedLeader != null -> selectedLeader.teacherName
                    selectedLeaderName.isNotBlank() -> selectedLeaderName
                    old?.teacherName?.isNotBlank() == true -> old.teacherName
                    else -> ownerName
                }
                val resultGroupId = old?.groupId?.takeIf { it.isNotBlank() } ?: ownerGroupId
                val resultGroupName = old?.groupName?.takeIf { it.isNotBlank() } ?: ownerGroupName
                val result = Club(
                    id = old?.id ?: "",
            name = name,
            description = description,
                    type = type,
                    teacherId = resultTeacherId,
                    teacherName = resultTeacherName,
                    groupId = resultGroupId,
                    groupName = resultGroupName,
            schedule = schedule,
                    nextSessionDate = nextSessionDate,
                    nextSessionTime = nextSessionTime,
            location = location,
            maxParticipants = maxParticipants,
                    participantIds = old?.participantIds ?: emptyList(),
                    participantNames = old?.participantNames ?: emptyList(),
                    isActive = old?.isActive ?: true,
                    createdAt = old?.createdAt ?: java.util.Date()
                )
                onSaved?.invoke(result)
                dialog.dismiss()
            }
        }
        return dialog
    }

    fun setOnSavedListener(listener: (Club) -> Unit) {
        onSaved = listener
    }

    private fun normalizeDate(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 8) {
            "${digits.substring(0, 2)}.${digits.substring(2, 4)}.${digits.substring(4, 8)}"
        } else {
            raw
        }
    }

    private fun parseTime(s: String): Pair<Int, Int>? {
        val trimmed = s.trim()
        val regex = Regex("(\\d{1,2}):(\\d{2})")
        val m = regex.find(trimmed) ?: return null
        val hour = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val minute = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: return null
        return hour to minute
    }

    private fun buildScheduleFromUi(
        mon: CheckBox, tue: CheckBox, wed: CheckBox, thu: CheckBox, fri: CheckBox, sat: CheckBox,
        startTime: TextInputEditText, endTime: TextInputEditText
    ): String {
        val days = listOfNotNull(
            if (mon.isChecked) "Пн" else null,
            if (tue.isChecked) "Вт" else null,
            if (wed.isChecked) "Ср" else null,
            if (thu.isChecked) "Чт" else null,
            if (fri.isChecked) "Пт" else null,
            if (sat.isChecked) "Сб" else null
        )
        val start = startTime.text.toString().trim()
        val end = endTime.text.toString().trim()
        return when {
            days.isEmpty() -> ""
            start.isBlank() || end.isBlank() -> days.joinToString(", ")
            else -> "${days.joinToString(", ")} $start–$end"
        }
    }

    private fun parseScheduleToUi(
        schedule: String?,
        mon: CheckBox, tue: CheckBox, wed: CheckBox, thu: CheckBox, fri: CheckBox, sat: CheckBox,
        startTime: TextInputEditText, endTime: TextInputEditText
    ) {
        val s = schedule?.trim() ?: return
        val dayNames = listOf("Пн" to mon, "Вт" to tue, "Ср" to wed, "Чт" to thu, "Пт" to fri, "Сб" to sat)
        dayNames.forEach { (name, box) -> box.isChecked = s.contains(name) }
        val timeRange = Regex("(\\d{1,2}:\\d{2})\\s*[–-]\\s*(\\d{1,2}:\\d{2})").find(s)
        if (timeRange != null) {
            startTime.setText(timeRange.groupValues[1])
            endTime.setText(timeRange.groupValues[2])
        }
    }
}
