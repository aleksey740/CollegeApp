package com.sakovich.collegeapp.presentation.clubs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Club
import com.sakovich.collegeapp.data.models.User
import java.util.Locale
import com.sakovich.collegeapp.data.repositories.ClubRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClubDetailsFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var clubRepository: ClubRepository
    private lateinit var userRepository: UserRepository

    private lateinit var titleText: TextView
    private lateinit var metaText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var participantsCard: MaterialCardView
    private lateinit var participantsText: TextView
    private lateinit var joinStatusCard: MaterialCardView
    private lateinit var joinStatusIcon: TextView
    private lateinit var joinStatusTitle: TextView
    private lateinit var joinStatusSubtitle: TextView
    private lateinit var joinStatusDivider: View
    private lateinit var joinStatusReminderBlock: View
    private lateinit var joinStatusReminderText: TextView
    private lateinit var joinStatusScheduleBlock: View
    private lateinit var joinStatusScheduleText: TextView
    private lateinit var joinStatusNextSessionBlock: View
    private lateinit var joinStatusNextSessionText: TextView
    private lateinit var joinButton: MaterialButton
    private lateinit var manageParticipantsButton: MaterialButton
    private lateinit var editButton: MaterialButton
    private lateinit var deleteButton: MaterialButton

    private var clubId: String = ""
    private var currentUser: User? = null
    private var currentClub: Club? = null

    companion object {
        fun newInstance(clubId: String): ClubDetailsFragment {
            return ClubDetailsFragment().apply {
                arguments = Bundle().apply { putString("clubId", clubId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clubId = arguments?.getString("clubId").orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_club_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth
        clubRepository = ClubRepository()
        userRepository = UserRepository()

        view.findViewById<ImageButton>(R.id.clubDetailsBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        titleText = view.findViewById(R.id.titleText)
        metaText = view.findViewById(R.id.metaText)
        descriptionText = view.findViewById(R.id.descriptionText)
        participantsCard = view.findViewById(R.id.participantsCard)
        participantsText = view.findViewById(R.id.participantsText)
        joinStatusCard = view.findViewById(R.id.joinStatusCard)
        joinStatusIcon = view.findViewById(R.id.joinStatusIcon)
        joinStatusTitle = view.findViewById(R.id.joinStatusTitle)
        joinStatusSubtitle = view.findViewById(R.id.joinStatusSubtitle)
        joinStatusDivider = view.findViewById(R.id.joinStatusDivider)
        joinStatusReminderBlock = view.findViewById(R.id.joinStatusReminderBlock)
        joinStatusReminderText = view.findViewById(R.id.joinStatusReminderText)
        joinStatusScheduleBlock = view.findViewById(R.id.joinStatusScheduleBlock)
        joinStatusScheduleText = view.findViewById(R.id.joinStatusScheduleText)
        joinStatusNextSessionBlock = view.findViewById(R.id.joinStatusNextSessionBlock)
        joinStatusNextSessionText = view.findViewById(R.id.joinStatusNextSessionText)
        joinButton = view.findViewById(R.id.joinButton)
        manageParticipantsButton = view.findViewById(R.id.manageParticipantsButton)
        editButton = view.findViewById(R.id.editButton)
        deleteButton = view.findViewById(R.id.deleteButton)

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val user = userRepository.getUser(uid)
            val club = clubRepository.getById(clubId)
            withContext(Dispatchers.Main) {
                currentUser = user
                currentClub = club
                if (club == null) {
                    Snackbar.make(requireView(), "Запись не найдена", Snackbar.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@withContext
                }
                bind(club, user)
            }
        }
    }

    private fun bind(club: Club, user: User?) {
        titleText.text = club.name
        descriptionText.text = club.description.ifBlank { "Описание не заполнено" }
        participantsText.text = "${club.participantIds.size}/${club.maxParticipants}"

        val uid = user?.id.orEmpty()
        val isOwnerTeacher = user?.isTeacher() == true && club.teacherId == user.id
        val enrolledStudent = club.isParticipant(uid) &&
            (user?.isStudent() == true || user?.isHeadman() == true)
        bindHeroMeta(club, compact = enrolledStudent)

        participantsCard.visibility = if (isOwnerTeacher) View.VISIBLE else View.GONE
        manageParticipantsButton.visibility = if (isOwnerTeacher) View.VISIBLE else View.GONE
        editButton.visibility = if (isOwnerTeacher) View.VISIBLE else View.GONE
        deleteButton.visibility = if (isOwnerTeacher) View.VISIBLE else View.GONE

        if (isOwnerTeacher) {
            hideJoinStatusCard()
            joinButton.visibility = View.VISIBLE
            joinButton.text = "Просмотр участников"
            joinButton.isEnabled = true
            joinButton.setOnClickListener { showParticipantsList(club) }
            manageParticipantsButton.setOnClickListener { showManageParticipantsDialog(club) }
            editButton.setOnClickListener { showEditDialog(club) }
            deleteButton.setOnClickListener { deleteClub(club) }
            return
        }

        hideJoinStatusCard()
        val canSelfEnroll = user?.isStudent() == true || user?.isHeadman() == true
        if (!canSelfEnroll) {
            joinButton.visibility = View.GONE
            return
        }

        if (club.isParticipant(uid)) {
            joinButton.visibility = View.GONE
            showEnrolledStatusCard(club, uid)
        } else if (club.canJoin(uid, user?.groupId)) {
            joinButton.visibility = View.VISIBLE
            joinButton.text = "Записаться"
            joinButton.isEnabled = true
            joinButton.setOnClickListener { joinClub(club, user) }
        } else {
            joinButton.visibility = View.GONE
            val notMyGroup = club.groupId.isNotBlank() && user?.groupId != club.groupId
            val message = if (notMyGroup) {
                "Запись доступна только для группы ${club.groupName.ifBlank { "куратора" }}"
            } else {
                "Свободных мест нет — дождитесь, пока кто-то отпишется"
            }
            showInfoStatusCard(
                title = if (notMyGroup) "Недоступно для вашей группы" else "Мест нет",
                subtitle = message,
                accentColor = if (notMyGroup) "#F59E0B" else "#F87171",
                icon = if (notMyGroup) "!" else "×"
            )
        }
    }

    private fun bindHeroMeta(club: Club, compact: Boolean) {
        if (compact) {
            val leader = club.teacherName.ifBlank { "Руководитель не указан" }
            metaText.text = "👨‍🏫 $leader"
            return
        }
        val schedule = club.schedule.ifBlank { "—" }
        val room = club.location.ifBlank { "—" }
        val sessionPart = ClubScheduleHelper.resolveNextSession(club)
            ?.let { (date, time) -> " • ближ. $date $time" }
            .orEmpty()
        metaText.text = "$schedule • $room • ${club.teacherName.ifBlank { "—" }}$sessionPart"
    }

    private fun hideJoinStatusCard() {
        joinStatusCard.visibility = View.GONE
    }

    private fun showEnrolledStatusCard(club: Club, userId: String) {
        val opts = ClubReminderScheduler.getUserOptionsForUi(requireContext(), userId)
        val remindersOn = opts.interval24hEnabled || opts.interval2hEnabled || opts.interval30mEnabled
        val scheduleText = club.schedule.trim()
        val nextSession = ClubScheduleHelper.resolveNextSession(club)

        joinStatusCard.visibility = View.VISIBLE
        joinStatusCard.strokeColor = Color.parseColor(if (remindersOn) "#22C55E" else "#F59E0B")
        joinStatusIcon.setBackgroundResource(R.drawable.bg_icon_green)
        joinStatusIcon.text = "✓"
        joinStatusTitle.text = "Вы записаны"
        joinStatusSubtitle.text = "Отписать может только куратор"

        var hasDetails = false

        joinStatusReminderBlock.visibility = View.VISIBLE
        hasDetails = true
        if (remindersOn) {
            val intervals = buildList {
                if (opts.interval24hEnabled) add("за 24 часа")
                if (opts.interval2hEnabled) add("за 2 часа")
                if (opts.interval30mEnabled) add("за 30 минут")
            }
            joinStatusReminderText.text = "Уведомления включены: ${intervals.joinToString(", ")}."
            joinStatusReminderText.setTextColor(Color.parseColor("#86EFAC"))
        } else {
            joinStatusReminderText.text =
                "Не забудьте включить напоминания — на экране «Кружки и секции» нажмите ⋯ в правом верхнем углу."
            joinStatusReminderText.setTextColor(Color.parseColor("#FCD34D"))
        }

        if (scheduleText.isNotEmpty()) {
            joinStatusScheduleBlock.visibility = View.VISIBLE
            joinStatusScheduleText.text = scheduleText
            hasDetails = true
        } else {
            joinStatusScheduleBlock.visibility = View.GONE
        }

        if (nextSession != null) {
            val (date, time) = nextSession
            joinStatusNextSessionBlock.visibility = View.VISIBLE
            joinStatusNextSessionText.text = "$date  •  $time"
            hasDetails = true
        } else {
            joinStatusNextSessionBlock.visibility = View.GONE
        }

        joinStatusDivider.visibility = if (hasDetails) View.VISIBLE else View.GONE
    }

    private fun showInfoStatusCard(title: String, subtitle: String, accentColor: String, icon: String) {
        joinStatusCard.visibility = View.VISIBLE
        joinStatusCard.strokeColor = Color.parseColor(accentColor)
        joinStatusIcon.text = icon
        joinStatusIcon.setBackgroundResource(
            when (icon) {
                "✓" -> R.drawable.bg_icon_green
                "×" -> R.drawable.bg_icon_red
                else -> R.drawable.bg_icon_orange
            }
        )
        joinStatusTitle.text = title
        joinStatusSubtitle.text = subtitle
        joinStatusDivider.visibility = View.GONE
        joinStatusReminderBlock.visibility = View.GONE
        joinStatusScheduleBlock.visibility = View.GONE
        joinStatusNextSessionBlock.visibility = View.GONE
    }

    private fun joinClub(club: Club, user: User?) {
        val uid = user?.id.orEmpty()
        val name = user?.fullName?.ifBlank { user.email }.orEmpty()
        if (uid.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            val success = clubRepository.join(club.id, uid, name, user?.groupId)
            withContext(Dispatchers.Main) {
                if (success) {
                    ClubReminderScheduler.scheduleForUser(requireContext().applicationContext, uid)
                    loadData()
                    Snackbar.make(
                        requireView(),
                        "Вы записаны. Настройте напоминания в меню ⋯ на экране списка.",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    Snackbar.make(requireView(), "Не удалось записаться", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showParticipantsList(club: Club) {
        val groupId = club.groupId.ifBlank { currentUser?.groupId.orEmpty() }
        if (groupId.isBlank()) {
            Snackbar.make(requireView(), "Группа не указана", Snackbar.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val rows = loadEnrolledParticipantRows(club, groupId)
            withContext(Dispatchers.Main) {
                val count = rows.size
                val subtitle = when (count) {
                    0 -> "Пока никто не записан"
                    1 -> "1 учащийся"
                    in 2..4 -> "$count учащихся"
                    else -> "$count учащихся"
                }
                showStudentsListDialog(
                    title = "Список записанных",
                    subtitle = subtitle,
                    rows = rows,
                    emptyMessage = "Пока нет записанных учащихся",
                    onPick = null
                )
            }
        }
    }

    private fun showManageParticipantsDialog(club: Club) {
        val view = layoutInflater.inflate(R.layout.dialog_club_manage_participants, null)
        val container = view.findViewById<LinearLayout>(R.id.manageActionsContainer)
        val actions = listOf(
            Triple("＋", "Записать учащегося", R.drawable.bg_icon_green) to { showAddParticipantDialog(club) },
            Triple("−", "Отписать учащегося", R.drawable.bg_icon_red) to { showRemoveParticipantDialog(club) },
            Triple("👥", "Список записанных", R.drawable.bg_icon_blue) to { showParticipantsList(club) }
        )
        var dialog: AlertDialog? = null
        actions.forEach { (meta, action) ->
            val (icon, title, bgRes) = meta
            val row = layoutInflater.inflate(R.layout.item_club_manage_action, container, false)
            row.findViewById<TextView>(R.id.actionIcon).apply {
                text = icon
                setBackgroundResource(bgRes)
            }
            row.findViewById<TextView>(R.id.actionTitle).text = title
            row.setOnClickListener {
                dialog?.dismiss()
                action()
            }
            container.addView(row)
        }
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
        dialog = alert
        view.findViewById<MaterialButton>(R.id.manageCancelButton).setOnClickListener { alert.dismiss() }
        alert.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alert.show()
    }

    private fun showAddParticipantDialog(club: Club) {
        val groupId = club.groupId.ifBlank { currentUser?.groupId.orEmpty() }
        if (groupId.isBlank()) {
            Snackbar.make(requireView(), "У кружка не указана группа", Snackbar.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val users = userRepository.getStudentsByGroupId(groupId)
                .filter { !club.participantIds.contains(it.id) }
                .sortedBy { ClubParticipantUi.formatDisplayName(it.fullName).lowercase(Locale.getDefault()) }
            val rows = users.map { user ->
                ClubParticipantRow(
                    id = user.id,
                    displayName = ClubParticipantUi.formatDisplayName(user.fullName),
                    isHeadman = ClubParticipantUi.isHeadman(user.fullName)
                )
            }
            withContext(Dispatchers.Main) {
                if (rows.isEmpty()) {
                    Snackbar.make(requireView(), "Все учащиеся уже записаны", Snackbar.LENGTH_SHORT).show()
                    return@withContext
                }
                showStudentsListDialog(
                    title = "Выберите учащегося",
                    subtitle = "Доступно для записи: ${rows.size}",
                    rows = rows,
                    emptyMessage = "Нет доступных учащихся",
                    onPick = { selected ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val user = users.find { it.id == selected.id }
                            val ok = clubRepository.join(
                                club.id,
                                selected.id,
                                user?.fullName?.ifBlank { user.email }.orEmpty(),
                                user?.groupId
                            )
                            withContext(Dispatchers.Main) {
                                if (ok) {
                                    Snackbar.make(requireView(), "Учащийся записан", Snackbar.LENGTH_SHORT).show()
                                    ClubReminderScheduler.scheduleForUser(
                                        requireContext().applicationContext,
                                        selected.id
                                    )
                                    loadData()
                                } else {
                                    Snackbar.make(requireView(), "Не удалось записать", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun showRemoveParticipantDialog(club: Club) {
        val groupId = club.groupId.ifBlank { currentUser?.groupId.orEmpty() }
        if (groupId.isBlank()) {
            Snackbar.make(requireView(), "Группа не указана", Snackbar.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val rows = loadEnrolledParticipantRows(club, groupId)
            withContext(Dispatchers.Main) {
                if (rows.isEmpty()) {
                    Snackbar.make(requireView(), "Нет записанных учащихся для отписки", Snackbar.LENGTH_SHORT).show()
                    return@withContext
                }
                showStudentsListDialog(
                    title = "Кого отписать?",
                    subtitle = "Записано: ${rows.size}",
                    rows = rows,
                    emptyMessage = "Нет записанных учащихся",
                    onPick = { selected ->
                        val rawName = club.participantNames.getOrNull(
                            club.participantIds.indexOf(selected.id)
                        ).orEmpty()
                        CoroutineScope(Dispatchers.IO).launch {
                            val ok = clubRepository.removeParticipant(club.id, selected.id, rawName)
                            withContext(Dispatchers.Main) {
                                if (ok) {
                                    Snackbar.make(requireView(), "Учащийся отписан", Snackbar.LENGTH_SHORT).show()
                                    ClubReminderScheduler.scheduleForUser(
                                        requireContext().applicationContext,
                                        selected.id
                                    )
                                    loadData()
                                } else {
                                    Snackbar.make(requireView(), "Не удалось отписать", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private suspend fun loadEnrolledParticipantRows(club: Club, groupId: String): List<ClubParticipantRow> {
        val groupStudentIds = userRepository.getStudentsByGroupId(groupId).map { it.id }.toSet()
        val names = club.participantNames + List((club.participantIds.size - club.participantNames.size).coerceAtLeast(0)) { "" }
        val (ids, rawNames) = club.participantIds.zip(names)
            .filter { (id, _) -> id in groupStudentIds }
            .unzip()
        return ClubParticipantUi.rowsFromIdsAndNames(ids, rawNames)
    }

    private fun showStudentsListDialog(
        title: String,
        subtitle: String,
        rows: List<ClubParticipantRow>,
        emptyMessage: String,
        onPick: ((ClubParticipantRow) -> Unit)?
    ) {
        val contentView = layoutInflater.inflate(R.layout.dialog_club_students_list, null)
        contentView.findViewById<TextView>(R.id.dialogTitle).text = title
        contentView.findViewById<TextView>(R.id.dialogSubtitle).text = subtitle

        val recyclerView = contentView.findViewById<RecyclerView>(R.id.studentsRecyclerView)
        val emptyText = contentView.findViewById<TextView>(R.id.emptyText)
        val listContainer = recyclerView.parent as FrameLayout
        val maxListHeight = (360 * resources.displayMetrics.density).toInt()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(contentView)
            .create()

        if (rows.isEmpty()) {
            listContainer.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = emptyMessage
        } else {
            emptyText.visibility = View.GONE
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = ClubParticipantRowAdapter(
                items = rows,
                showActionHint = onPick != null,
                onItemClick = onPick?.let { pick -> { row -> pick(row); dialog.dismiss() } }
            )
            recyclerView.post {
                val measured = recyclerView.measuredHeight
                if (measured > maxListHeight) {
                    listContainer.layoutParams = (listContainer.layoutParams as LinearLayout.LayoutParams).apply {
                        height = maxListHeight
                    }
                    recyclerView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        maxListHeight
                    )
                }
            }
        }

        contentView.findViewById<MaterialButton>(R.id.dialogCloseButton).apply {
            text = if (onPick != null) "Отмена" else "Закрыть"
            setOnClickListener { dialog.dismiss() }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showEditDialog(club: Club) {
        val teacher = currentUser ?: return
        val dialog = AddClubDialogFragment.newInstance(
            teacherId = teacher.id,
            teacherName = teacher.fullName.ifBlank { teacher.email },
            editingClub = club,
            groupId = teacher.groupId.ifBlank { club.groupId },
            groupName = teacher.groupName.ifBlank { club.groupName }
        )
        dialog.setOnSavedListener { updated ->
            CoroutineScope(Dispatchers.IO).launch {
                val gid = ClubRepository.clubGroupId(updated)
                if (clubRepository.existsByName(updated.name, gid, updated.type, excludeId = updated.id)) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(requireView(), "Данный кружок/секция/факультатив уже создан", Snackbar.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val ok = clubRepository.update(updated)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Snackbar.make(requireView(), "Изменения сохранены", Snackbar.LENGTH_SHORT).show()
                        val appContext = requireContext().applicationContext
                        updated.participantIds.forEach { participantId ->
                            ClubReminderScheduler.scheduleForUser(appContext, participantId)
                        }
                        loadData()
                    } else {
                        Snackbar.make(requireView(), "Ошибка сохранения", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show(parentFragmentManager, "EditClubDialog")
    }

    private fun safeSnackbar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        val anchor = (if (isAdded) view else null)
            ?: activity?.findViewById(android.R.id.content)
            ?: return
        Snackbar.make(anchor, message, length).show()
    }

    private fun deleteClub(club: Club) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить запись?")
            .setMessage(club.name)
            .setPositiveButton("Удалить") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { clubRepository.delete(club.id) }
                    if (!isAdded) return@launch
                    if (ok) {
                        parentFragmentManager.popBackStack()
                        activity?.findViewById<View>(android.R.id.content)?.let { anchor ->
                            Snackbar.make(anchor, "Удалено", Snackbar.LENGTH_SHORT).show()
                        }
                    } else {
                        safeSnackbar("Ошибка удаления")
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
