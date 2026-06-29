package com.sakovich.collegeapp.presentation.curatorial

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.ScheduleRepository
import com.sakovich.collegeapp.utils.ScheduleTimeSlotRules
import com.sakovich.collegeapp.utils.SemesterStatsHelper
import java.util.Calendar
import java.util.Locale

object CuratorialHourDialogSupport {

    const val SEMESTER_DATE_ERROR = "Дата вне периода семестра"
    const val ALL_SLOTS_OCCUPIED = "На выбранную дату все слоты заняты"

    data class LoadedData(
        val semesters: List<AdminSemesterTemplate>,
        val groupSchedules: List<ScheduleItem>
    )

    suspend fun loadData(groupName: String): LoadedData {
        val groupId = GroupRepository.groupNameToDocumentId(groupName)
        return LoadedData(
            semesters = AdminRepository().getSemestersForGroup(groupId),
            groupSchedules = ScheduleRepository().getScheduleForGroupFromServerNoOrder(groupName)
        )
    }

    fun isDateAllowed(date: String, semesters: List<AdminSemesterTemplate>): Boolean =
        SemesterStatsHelper.isDateWithinAnySemester(date, semesters)

    fun suggestInitialDate(semesters: List<AdminSemesterTemplate>): String {
        val today = formatDate(Calendar.getInstance())
        if (isDateAllowed(today, semesters)) return today

        val todayMillis = SemesterStatsHelper.parseDate(today)?.time ?: return today
        val upcomingStart = semesters
            .mapNotNull { semester ->
                SemesterStatsHelper.parseDate(semester.startDate)?.let { it to semester }
            }
            .filter { it.first.time >= todayMillis }
            .minByOrNull { it.first.time }
            ?.first
        if (upcomingStart != null) return formatDate(upcomingStart)

        val active = semesters.firstOrNull { semester ->
            val start = SemesterStatsHelper.parseDate(semester.startDate) ?: return@firstOrNull false
            val end = SemesterStatsHelper.parseDate(semester.endDate) ?: return@firstOrNull false
            SemesterStatsHelper.isDateInRange(today, start, end)
        }
        if (active != null) return today

        return semesters
            .mapNotNull { SemesterStatsHelper.parseDate(it.startDate) }
            .minByOrNull { it.time }
            ?.let { formatDate(it) }
            ?: today
    }

    fun applyDateValidation(dateLayout: TextInputLayout, date: String, semesters: List<AdminSemesterTemplate>): Boolean {
        if (!isDateAllowed(date, semesters)) {
            dateLayout.error = SEMESTER_DATE_ERROR
            return false
        }
        dateLayout.error = null
        return true
    }

    fun applyTimeSlotDropdown(
        context: Context,
        timeDropdown: AutoCompleteTextView,
        timeLayout: TextInputLayout,
        schedules: List<ScheduleItem>,
        groupName: String,
        selectedDate: String,
        semesters: List<AdminSemesterTemplate>,
        excludeScheduleId: String? = null,
        preferredTime: String? = null
    ) {
        if (!isDateAllowed(selectedDate, semesters)) {
            timeDropdown.setAdapter(null)
            timeDropdown.setText("", false)
            timeDropdown.isEnabled = false
            timeLayout.isEnabled = false
            timeLayout.helperText = null
            return
        }

        val used = ScheduleTimeSlotRules.usedSlotsOnDate(schedules, selectedDate, groupName, excludeScheduleId)
        val available = ScheduleTimeSlotRules.availableTimeOptions(used)
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, available)
        timeDropdown.setAdapter(adapter)
        timeDropdown.isClickable = available.isNotEmpty()
        timeDropdown.isEnabled = available.isNotEmpty()
        val current = preferredTime ?: timeDropdown.text?.toString()?.trim().orEmpty()
        val pick = when {
            current in available -> current
            available.isNotEmpty() -> available.first()
            else -> ""
        }
        timeDropdown.setText(pick, false)
        timeLayout.isEnabled = available.isNotEmpty()
        timeLayout.helperText = if (available.isEmpty()) ALL_SLOTS_OCCUPIED else null
        timeLayout.error = null
    }

    fun isTimeSlotAvailable(
        schedules: List<ScheduleItem>,
        groupName: String,
        date: String,
        time: String,
        excludeScheduleId: String? = null
    ): Boolean {
        val used = ScheduleTimeSlotRules.usedSlotsOnDate(schedules, date, groupName, excludeScheduleId)
        return ScheduleTimeSlotRules.isSlotAvailable(time, used)
    }

    private fun formatDate(calendar: Calendar): String =
        String.format(
            Locale.getDefault(),
            "%02d.%02d.%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )

    private fun formatDate(date: java.util.Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        return formatDate(calendar)
    }
}
