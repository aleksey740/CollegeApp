package com.sakovich.collegeapp.presentation.events

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.GroupEventRepository
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.ContentOwnershipRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GroupEventsFragment : Fragment() {
    private val auth: FirebaseAuth = Firebase.auth
    private val userRepo = UserRepository()
    private val eventRepo = GroupEventRepository()
    private val notificationRepo = NotificationRepository()

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var dateText: TextView
    private lateinit var prevDateBtn: com.google.android.material.button.MaterialButton
    private lateinit var nextDateBtn: com.google.android.material.button.MaterialButton
    private lateinit var backBtn: ImageButton
    private lateinit var addFab: FloatingActionButton
    private lateinit var adapter: GroupEventsAdapter

    private var currentUser: User? = null
    private val selectedDate = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var allEvents = emptyList<com.sakovich.collegeapp.data.models.GroupEvent>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_group_events, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.eventsRecyclerView)
        progress = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyStateText)
        dateText = view.findViewById(R.id.dateText)
        prevDateBtn = view.findViewById(R.id.prevDateBtn)
        nextDateBtn = view.findViewById(R.id.nextDateBtn)
        backBtn = view.findViewById(R.id.eventsBackButton)
        addFab = view.findViewById(R.id.fabAddEvent)
        backBtn.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        prevDateBtn.setOnClickListener { selectedDate.add(Calendar.DAY_OF_MONTH, -1); applyFilter() }
        nextDateBtn.setOnClickListener { selectedDate.add(Calendar.DAY_OF_MONTH, 1); applyFilter() }
        dateText.setOnClickListener { openDatePicker() }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        loadUserAndEvents()
    }

    private fun loadUserAndEvents() {
        val uid = auth.currentUser?.uid ?: return
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val user = userRepo.getUser(uid)
            val group = user?.groupName?.takeIf { it.isNotBlank() } ?: user?.group.orEmpty()
            val items = eventRepo.getEventsForGroup(group)
            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                currentUser = user
                allEvents = items
                val canAdd = user?.role == "teacher" || user?.role == "headman"
                addFab.visibility = if (canAdd) View.VISIBLE else View.GONE
                addFab.setOnClickListener { openAddDialog() }
                adapter = GroupEventsAdapter(items, canAdd) { onEventClick(it) }
                recycler.adapter = adapter
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val d = dateFormat.format(selectedDate.time)
        dateText.text = "$d\n${dayName(selectedDate)}"
        val filtered = allEvents.filter { it.date == d }
        adapter.submit(filtered)
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (filtered.isEmpty()) "Нет мероприятий на $d" else ""
    }

    private fun onEventClick(item: com.sakovich.collegeapp.data.models.GroupEvent) {
        val user = currentUser ?: return
        val canModify = user.canEditEvents() &&
            ContentOwnershipRules.canModify(user, item.createdBy, item.createdByRole)
        if (canModify) {
            showActionDialog(item)
        } else {
            showEventDetails(item)
        }
    }

    private fun showEventDetails(item: com.sakovich.collegeapp.data.models.GroupEvent) {
        val details = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_group_event_details, null)
        details.findViewById<TextView>(R.id.detailTitle).text = item.title
        details.findViewById<TextView>(R.id.detailBadge).text = "📌 Мероприятие группы"
        details.findViewById<TextView>(R.id.detailDateTime).text = "📅 ${item.date} • ${item.time}"
        details.findViewById<TextView>(R.id.detailPlace).text = "📍 ${item.place}"
        details.findViewById<TextView>(R.id.detailDescription).text = item.description.ifBlank { "Без описания" }
        MaterialAlertDialogBuilder(requireContext())
            .setView(details)
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showActionDialog(item: com.sakovich.collegeapp.data.models.GroupEvent) {
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_menu, null)
        val titleText = menuView.findViewById<TextView>(R.id.menuTitleText)
        val detailsBtn = menuView.findViewById<MaterialButton>(R.id.actionButton1)
        val editBtn = menuView.findViewById<MaterialButton>(R.id.actionButton2)
        val deleteBtn = menuView.findViewById<MaterialButton>(R.id.actionButton3)
        menuView.findViewById<View>(R.id.actionButton4).visibility = View.GONE

        titleText.text = "${item.title}\n${item.date} ${item.time} • ${item.place}"
        detailsBtn.text = "Подробнее"
        detailsBtn.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_info)
        detailsBtn.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.text_secondary_dark)
        editBtn.text = "Редактировать"
        editBtn.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_edit)
        editBtn.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.text_secondary_dark)
        deleteBtn.text = "Удалить"
        deleteBtn.visibility = View.VISIBLE
        deleteBtn.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)
        deleteBtn.setTextColor(0xFFFCA5A5.toInt())
        deleteBtn.iconTint = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light)

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(menuView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        detailsBtn.setOnClickListener { dialog.dismiss(); showEventDetails(item) }
        editBtn.setOnClickListener { dialog.dismiss(); openEditDialog(item) }
        deleteBtn.setOnClickListener { dialog.dismiss(); showDeleteConfirmation(item) }
        dialog.show()
    }

    private fun showDeleteConfirmation(item: com.sakovich.collegeapp.data.models.GroupEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Подтверждение удаления")
            .setMessage(
                "Удалить мероприятие?\n\n" +
                    "📌 ${item.title}\n" +
                    "📅 ${item.date} ${item.time}\n" +
                    "📍 ${item.place}"
            )
            .setPositiveButton("Удалить") { _, _ ->
                progress.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.IO).launch {
                    val ok = eventRepo.deleteEvent(item.id)
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        if (ok) {
                            loadUserAndEvents()
                            Toast.makeText(requireContext(), "Мероприятие удалено", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Не удалось удалить", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openEditDialog(item: com.sakovich.collegeapp.data.models.GroupEvent) {
        val dialog = AddGroupEventDialogFragment.newInstanceForEdit(item)
        dialog.setOnSaveListener { updated ->
            progress.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val ok = eventRepo.updateEvent(updated)
                if (ok) {
                    GroupEventReminderScheduler.scheduleForEvent(requireContext(), updated)
                }
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (ok) {
                        loadUserAndEvents()
                        Toast.makeText(requireContext(), "Мероприятие обновлено", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show(childFragmentManager, "EditGroupEventDialog")
    }

    private fun openAddDialog() {
        val user = currentUser ?: return
        val group = user.groupName.takeIf { it.isNotBlank() } ?: user.group
        val dialog = AddGroupEventDialogFragment.newInstance(
            group,
            user.id,
            user.fullName.ifBlank { "Организатор" },
            user.role,
            prefillDate = dateFormat.format(selectedDate.time)
        )
        dialog.setOnSaveListener { event ->
            progress.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val eventId = eventRepo.addEvent(event)
                GroupEventReminderScheduler.scheduleForEvent(requireContext(), event.copy(id = eventId))
                val students = userRepo.getStudentsByGroupName(group)
                val ids = (students.map { it.id } + user.id).distinct()
                notificationRepo.createGroupNotification(
                    studentIds = ids,
                    title = "📅 Новое мероприятие",
                    message = "«${event.title}»\n${event.date} ${event.time}\n${event.place}",
                    type = NotificationType.EVENT,
                    relatedId = eventId,
                    relatedType = "group_event",
                    excludeUserId = user.id
                )
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    loadUserAndEvents()
                    Toast.makeText(requireContext(), "Мероприятие добавлено", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show(childFragmentManager, "AddGroupEventDialog")
    }

    private fun openDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                applyFilter()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun dayName(c: Calendar): String = when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Понедельник"
        Calendar.TUESDAY -> "Вторник"
        Calendar.WEDNESDAY -> "Среда"
        Calendar.THURSDAY -> "Четверг"
        Calendar.FRIDAY -> "Пятница"
        Calendar.SATURDAY -> "Суббота"
        else -> "Воскресенье"
    }
}
