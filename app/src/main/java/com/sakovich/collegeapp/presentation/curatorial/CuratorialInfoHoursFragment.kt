package com.sakovich.collegeapp.presentation.curatorial

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.ScheduleRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.ContentOwnershipRules
import com.sakovich.collegeapp.utils.ScheduleSortHelper
import com.sakovich.collegeapp.utils.SemesterStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class CuratorialInfoHoursFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private val scheduleRepository = ScheduleRepository()
    private var teacherGroupName: String = ""
    private var teacherName: String = ""

    private lateinit var hoursRecyclerView: RecyclerView
    private lateinit var emptyContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var addCuratorialButton: MaterialButton
    private lateinit var addInfoHourButton: MaterialButton
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var adapter: CuratorialHoursAdapter
    private lateinit var statTotalValue: TextView
    private lateinit var statCuratorialValue: TextView
    private lateinit var statInfoValue: TextView
    private lateinit var dateFilterButton: MaterialButton
    private lateinit var curatorialBackButton: ImageButton

    private var allHours: List<ScheduleItem> = emptyList()
    private var currentFilter: ScheduleType? = null
    private var filterDateSingle: String? = null
    private var filterDateFrom: String? = null
    private var filterDateTo: String? = null
    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_curatorial_info_hours, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hoursRecyclerView = view.findViewById(R.id.hoursRecyclerView)
        emptyContainer = view.findViewById(R.id.emptyContainer)
        progressBar = view.findViewById(R.id.progressBar)
        addCuratorialButton = view.findViewById(R.id.addCuratorialButton)
        addInfoHourButton = view.findViewById(R.id.addInfoHourButton)
        filterChipGroup = view.findViewById(R.id.filterChipGroup)
        statTotalValue = view.findViewById(R.id.statTotalValue)
        statCuratorialValue = view.findViewById(R.id.statCuratorialValue)
        statInfoValue = view.findViewById(R.id.statInfoValue)
        dateFilterButton = view.findViewById(R.id.dateFilterButton)
        curatorialBackButton = view.findViewById(R.id.curatorialBackButton)

        adapter = CuratorialHoursAdapter(onItemClick = { openDetailsDialog(it) })
        hoursRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        hoursRecyclerView.isNestedScrollingEnabled = false
        hoursRecyclerView.adapter = adapter

        addCuratorialButton.setOnClickListener { openAddCuratorialDialog() }
        addInfoHourButton.setOnClickListener { openAddInfoHourDialog() }

        curatorialBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        filterChipGroup.check(R.id.filterAll)
        filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.filterCuratorial) -> ScheduleType.CURATOR_HOUR
                checkedIds.contains(R.id.filterInfo) -> ScheduleType.INFO_HOUR
                else -> null
            }
            applyFilter()
        }

        dateFilterButton.text = "Все даты"
        dateFilterButton.setOnClickListener { showDateFilterDialog() }

        childFragmentManager.setFragmentResultListener(REQUEST_CURATORIAL_ADDED, viewLifecycleOwner) { _, _ ->
            loadScheduleAndShowHours()
        }
        childFragmentManager.setFragmentResultListener(REQUEST_INFO_ADDED, viewLifecycleOwner) { _, _ ->
            loadScheduleAndShowHours()
        }
    }

    override fun onResume() {
        super.onResume()
        loadScheduleAndShowHours()
    }

    companion object {
        const val REQUEST_CURATORIAL_ADDED = "curatorial_added"
        const val REQUEST_INFO_ADDED = "info_added"
    }

    private fun loadScheduleAndShowHours() {
        val uid = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = userRepository.getUser(uid)
                val groupName = user?.groupName?.takeIf { it.isNotBlank() } ?: user?.group?.takeIf { it.isNotBlank() } ?: ""
                val name = user?.fullName ?: ""
                teacherGroupName = groupName
                teacherName = name

                val schedule = if (groupName.isNotBlank()) {
                    scheduleRepository.getScheduleForGroupFromServerNoOrder(groupName)
                } else {
                    scheduleRepository.getAllScheduleFromServer()
                }
                val hoursOnly = schedule
                    .filter { it.type == ScheduleType.CURATOR_HOUR || it.type == ScheduleType.INFO_HOUR }
                    .filter { groupName.isBlank() || it.group == groupName || it.teacherName == name }
                    .let { sortHoursByDateDescending(it) }
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    currentUser = user
                    allHours = hoursOnly
                    updateStats()
                    applyFilter()
                    if (allHours.isEmpty()) {
                        emptyContainer.visibility = View.VISIBLE
                    } else {
                        emptyContainer.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    allHours = emptyList()
                    updateStats()
                    applyFilter()
                    if (adapter.itemCount == 0) {
                        emptyContainer.visibility = View.VISIBLE
                    }
                    Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStats() {
        val total = allHours.size
        val curatorial = allHours.count { it.type == ScheduleType.CURATOR_HOUR }
        val info = allHours.count { it.type == ScheduleType.INFO_HOUR }
        statTotalValue.text = total.toString()
        statCuratorialValue.text = curatorial.toString()
        statInfoValue.text = info.toString()
    }

    private fun applyFilter() {
        var filtered = when (currentFilter) {
            ScheduleType.CURATOR_HOUR -> allHours.filter { it.type == ScheduleType.CURATOR_HOUR }
            ScheduleType.INFO_HOUR -> allHours.filter { it.type == ScheduleType.INFO_HOUR }
            else -> allHours
        }
        filterDateSingle?.let { date ->
            filtered = filtered.filter { it.date == date }
        }
        if (filterDateFrom != null && filterDateTo != null) {
            val from = parseFilterDate(filterDateFrom!!)
            val to = parseFilterDate(filterDateTo!!)
            if (from != null && to != null) {
                filtered = filtered.filter { item ->
                    val itemDate = parseFilterDate(item.date) ?: return@filter false
                    !itemDate.before(from) && !itemDate.after(to)
                }
            }
        }
        adapter.setItems(sortHoursByDateDescending(filtered))
        if (filtered.isEmpty() && allHours.isNotEmpty()) {
            emptyContainer.visibility = View.VISIBLE
        } else if (filtered.isEmpty()) {
            emptyContainer.visibility = View.VISIBLE
        } else {
            emptyContainer.visibility = View.GONE
        }
    }

    private fun showDateFilterDialog() {
        val options = arrayOf("Выбрать дату", "Период с — по", "Все даты")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Фильтр по дате")
            .setItems(options) { _, which ->
                when (which) {
                    2 -> {
                        filterDateSingle = null
                        filterDateFrom = null
                        filterDateTo = null
                        dateFilterButton.text = "Все даты"
                        applyFilter()
                    }
                    1 -> pickDateRangeStart()
                    else -> pickSingleDate()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun pickSingleDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                filterDateSingle = formatFilterDate(dayOfMonth, month, year)
                filterDateFrom = null
                filterDateTo = null
                dateFilterButton.text = filterDateSingle
                applyFilter()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickDateRangeStart() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                filterDateFrom = formatFilterDate(dayOfMonth, month, year)
                pickDateRangeEnd()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickDateRangeEnd() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                filterDateSingle = null
                filterDateTo = formatFilterDate(dayOfMonth, month, year)
                dateFilterButton.text = "${filterDateFrom} — ${filterDateTo}"
                applyFilter()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatFilterDate(day: Int, month: Int, year: Int): String =
        String.format(Locale.getDefault(), "%02d.%02d.%04d", day, month + 1, year)

    private fun sortHoursByDateDescending(items: List<ScheduleItem>): List<ScheduleItem> {
        if (items.size <= 1) return items
        val peersByDateGroup = items.groupBy { "${it.date}|${it.group}" }
        return items.sortedWith(
            compareByDescending<ScheduleItem> {
                SemesterStatsHelper.parseDate(it.date)?.time ?: Long.MIN_VALUE
            }.thenByDescending { item ->
                val peers = peersByDateGroup["${item.date}|${item.group}"] ?: listOf(item)
                ScheduleSortHelper.sortKey(item, peers)
            }
        )
    }

    private fun parseFilterDate(value: String): Calendar? {
        val parts = value.split(".")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = (parts[1].toIntOrNull() ?: return null) - 1
        val year = parts[2].toIntOrNull() ?: return null
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun openAddCuratorialDialog() {
        if (teacherGroupName.isBlank()) {
            Toast.makeText(requireContext(), "Группа не указана.", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AddCuratorialHourDialogFragment.newInstance(
            groupName = teacherGroupName,
            teacherName = teacherName,
            teacherId = auth.currentUser?.uid ?: ""
        )
        dialog.show(childFragmentManager, "AddCuratorialHour")
        view?.post {
            (childFragmentManager.findFragmentByTag("AddCuratorialHour") as? AddCuratorialHourDialogFragment)?.dialog?.setOnDismissListener {
                loadScheduleAndShowHours()
            }
        }
    }

    private fun openAddInfoHourDialog() {
        if (teacherGroupName.isBlank()) {
            Toast.makeText(requireContext(), "Группа не указана.", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AddInfoHourDialogFragment.newInstance(
            groupName = teacherGroupName,
            teacherName = teacherName,
            teacherId = auth.currentUser?.uid ?: ""
        )
        dialog.show(childFragmentManager, "AddInfoHour")
        view?.post {
            (childFragmentManager.findFragmentByTag("AddInfoHour") as? AddInfoHourDialogFragment)?.dialog?.setOnDismissListener {
                loadScheduleAndShowHours()
            }
        }
    }

    private fun openDetailsDialog(schedule: ScheduleItem) {
        val ctx = context ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val students = if (schedule.type == ScheduleType.INFO_HOUR && teacherGroupName.isNotBlank()) {
                userRepository.getStudentsByGroupName(teacherGroupName)
            } else {
                emptyList()
            }
            val assigned = if (schedule.type == ScheduleType.INFO_HOUR && schedule.assignedStudentIds.isNotEmpty()) {
                students.filter { schedule.assignedStudentIds.contains(it.id) }
            } else {
                emptyList()
            }
            val studentsText = if (schedule.type == ScheduleType.INFO_HOUR) {
                if (assigned.isEmpty()) {
                    "Никто не закреплён за подготовкой темы."
                } else {
                    assigned.joinToString(separator = "\n") { user ->
                        val role = if (user.role == "headman") " (староста)" else ""
                        "• ${user.fullName.ifBlank { user.email }}$role"
                    }
                }
            } else {
                "Для кураторского часа закреплённые учащиеся не используются."
            }

            withContext(Dispatchers.Main) {
                val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_info_hour_details, null)
                val typeBadge = dialogView.findViewById<TextView>(R.id.typeBadge)
                val topicText = dialogView.findViewById<TextView>(R.id.topicText)
                val dateTimeText = dialogView.findViewById<TextView>(R.id.dateTimeText)
                val roomText = dialogView.findViewById<TextView>(R.id.roomText)
                val studentsTextView = dialogView.findViewById<TextView>(R.id.studentsText)

                val isCuratorial = schedule.type == ScheduleType.CURATOR_HOUR
                typeBadge.text = if (isCuratorial) "Кураторский час" else "Информационный час"
                topicText.text = schedule.subject.ifBlank { "Без темы" }
                dateTimeText.text = "📅 ${schedule.date} • ${schedule.time}"
                roomText.text = if (schedule.room.isNotBlank()) "🏫 ${schedule.room}" else "🏫 аудитория не указана"
                studentsTextView.text = studentsText

                val editBtn = dialogView.findViewById<MaterialButton>(R.id.actionEdit)
                val deleteBtn = dialogView.findViewById<MaterialButton>(R.id.actionDelete)
                val user = currentUser
                val canModify = user != null && ContentOwnershipRules.canModify(
                    user,
                    schedule.createdBy,
                    schedule.createdByRole
                )
                editBtn.visibility = if (canModify) View.VISIBLE else View.GONE
                deleteBtn.visibility = if (canModify) View.VISIBLE else View.GONE
                val dialog = MaterialAlertDialogBuilder(ctx)
                    .setView(dialogView)
                    .setNegativeButton("Закрыть", null)
                    .create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                deleteBtn.setOnClickListener {
                    dialog.dismiss()
                    if (schedule.id.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val ok = scheduleRepository.deleteSchedule(
                                schedule.id,
                                auth.currentUser?.uid.orEmpty()
                            )
                            withContext(Dispatchers.Main) {
                                if (ok) {
                                    Toast.makeText(ctx, "Час удалён.", Toast.LENGTH_SHORT).show()
                                    loadScheduleAndShowHours()
                                } else {
                                    Toast.makeText(ctx, "Не удалось удалить.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                editBtn.setOnClickListener {
                    dialog.dismiss()
                    if (schedule.id.isNotBlank() && (schedule.type == ScheduleType.INFO_HOUR || schedule.type == ScheduleType.CURATOR_HOUR)) {
                        val editDialog = EditInfoHourDialogFragment.newInstance(
                            scheduleId = schedule.id,
                            groupName = teacherGroupName,
                            teacherName = teacherName,
                            teacherId = auth.currentUser?.uid ?: "",
                            schedule = schedule
                        )
                        editDialog.show(childFragmentManager, "EditInfoHour")
                        view?.post {
                            (childFragmentManager.findFragmentByTag("EditInfoHour") as? EditInfoHourDialogFragment)?.dialog?.setOnDismissListener {
                                loadScheduleAndShowHours()
                            }
                        }
                    } else {
                        Toast.makeText(ctx, "Редактирование недоступно для этого элемента.", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.show()
            }
        }
    }
}
