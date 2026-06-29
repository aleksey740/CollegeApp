package com.sakovich.collegeapp.presentation.admin

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ClubLeaderEntry
import com.sakovich.collegeapp.data.models.ClubType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.ClubLeaderRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminClubLeadersFragment : Fragment() {

    private val clubLeaderRepository = ClubLeaderRepository()
    private val userRepository = UserRepository()
    private lateinit var auth: FirebaseAuth

    private lateinit var leadersListClubs: LinearLayout
    private lateinit var leadersListSections: LinearLayout
    private lateinit var leadersListElectives: LinearLayout
    private lateinit var badgeClubsCount: TextView
    private lateinit var badgeSectionsCount: TextView
    private lateinit var badgeElectivesCount: TextView
    private lateinit var progressBar: ProgressBar

    private var allLeaders: List<ClubLeaderEntry> = emptyList()
    private var curatorGroupId: String = ""
    private var curatorGroupName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_club_leaders, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth
        view.findViewById<ImageButton>(R.id.clubLeadersBackButton).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        progressBar = view.findViewById(R.id.clubLeadersProgressBar)
        leadersListClubs = view.findViewById(R.id.leadersListClubs)
        leadersListSections = view.findViewById(R.id.leadersListSections)
        leadersListElectives = view.findViewById(R.id.leadersListElectives)
        badgeClubsCount = view.findViewById(R.id.badgeClubsCount)
        badgeSectionsCount = view.findViewById(R.id.badgeSectionsCount)
        badgeElectivesCount = view.findViewById(R.id.badgeElectivesCount)

        view.findViewById<MaterialButton>(R.id.addLeaderClubsButton)
            .setOnClickListener { showAddLeaderDialog(ClubType.CLUB) }
        view.findViewById<MaterialButton>(R.id.addLeaderSectionsButton)
            .setOnClickListener { showAddLeaderDialog(ClubType.SECTION) }
        view.findViewById<MaterialButton>(R.id.addLeaderElectivesButton)
            .setOnClickListener { showAddLeaderDialog(ClubType.ELECTIVE) }

        verifyAccessAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (curatorGroupId.isNotBlank()) loadData()
    }

    private fun verifyAccessAndLoad() {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val uid = auth.currentUser?.uid
            val user: User? = if (uid.isNullOrBlank()) null else userRepository.getUser(uid)
            val hasAccess = user?.role == "teacher" || user?.role == "admin"
            if (!hasAccess) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressBar.visibility = View.GONE
                    android.widget.Toast.makeText(requireContext(), "Нет доступа", android.widget.Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
                return@launch
            }
            curatorGroupName = user?.groupName?.ifBlank { user.group }?.trim().orEmpty()
            curatorGroupId = GroupRepository.effectiveGroupIdForUser(
                user?.groupName.orEmpty(),
                user?.groupId.orEmpty(),
                user?.group.orEmpty()
            )
            if (curatorGroupId.isBlank() || curatorGroupName.isBlank()) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressBar.visibility = View.GONE
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Для справочника укажите группу куратора в профиле",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                loadData()
            }
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val leaders = clubLeaderRepository.getAllForGroup(curatorGroupId, curatorGroupName)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                progressBar.visibility = View.GONE
                allLeaders = leaders
                renderLists()
                updateBadges()
            }
        }
    }

    private fun updateBadges() {
        val clubs = allLeaders.count { it.type == ClubType.CLUB }
        val sections = allLeaders.count { it.type == ClubType.SECTION }
        val electives = allLeaders.count { it.type == ClubType.ELECTIVE }
        badgeClubsCount.text = clubs.toString()
        badgeSectionsCount.text = sections.toString()
        badgeElectivesCount.text = electives.toString()
    }

    private fun renderLists() {
        renderList(leadersListClubs, allLeaders.filter { it.type == ClubType.CLUB }, "Пока нет руководителей кружков")
        renderList(leadersListSections, allLeaders.filter { it.type == ClubType.SECTION }, "Пока нет руководителей секций")
        renderList(leadersListElectives, allLeaders.filter { it.type == ClubType.ELECTIVE }, "Пока нет руководителей факультативов")
    }

    private fun renderList(container: LinearLayout, entries: List<ClubLeaderEntry>, emptyHint: String) {
        container.removeAllViews()
        if (entries.isEmpty()) {
            container.addView(buildEmptyHint(emptyHint))
            return
        }
        entries.forEach { entry ->
            val item = layoutInflater.inflate(R.layout.item_admin_club_leader, container, false)
            item.findViewById<TextView>(R.id.leaderNameText).text =
                entry.teacherName.ifBlank { entry.teacherId }
            item.findViewById<MaterialButton>(R.id.editLeaderButton).setOnClickListener {
                editLeader(entry)
            }
            item.findViewById<MaterialButton>(R.id.removeLeaderButton).setOnClickListener {
                removeLeader(entry)
            }
            container.addView(item)
        }
    }

    private fun buildEmptyHint(message: String): TextView {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            textSize = 13.5f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(12), dp(8), dp(4))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showAddLeaderDialog(type: ClubType) {
        val inputLayout = createNameInputLayout()
        val input = createNameEditText()
        inputLayout.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить руководителя")
            .setView(inputLayout)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Snackbar.make(requireView(), "Введите ФИО", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                addLeader(
                    ClubLeaderEntry(
                        type = type,
                        teacherId = "",
                        teacherName = name,
                        groupId = curatorGroupId,
                        groupName = curatorGroupName
                    )
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addLeader(entry: ClubLeaderEntry) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = clubLeaderRepository.add(entry)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (ok) {
                    Snackbar.make(requireView(), "Руководитель добавлен", Snackbar.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Snackbar.make(requireView(), "Ошибка добавления", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeLeader(entry: ClubLeaderEntry) {
        if (entry.id.isBlank()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить из справочника?")
            .setMessage(entry.teacherName.ifBlank { entry.teacherId })
            .setPositiveButton("Удалить") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ok = clubLeaderRepository.remove(entry.id)
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        if (ok) {
                            Snackbar.make(requireView(), "Удалено", Snackbar.LENGTH_SHORT).show()
                            loadData()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun editLeader(entry: ClubLeaderEntry) {
        if (entry.id.isBlank()) return

        val inputLayout = createNameInputLayout()
        val input = createNameEditText().apply {
            setText(entry.teacherName.ifBlank { entry.teacherId })
            setSelection(text?.length ?: 0)
        }
        inputLayout.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать руководителя")
            .setView(inputLayout)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Snackbar.make(requireView(), "Введите ФИО", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ok = clubLeaderRepository.updateName(entry.id, name)
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        if (ok) {
                            Snackbar.make(requireView(), "Изменения сохранены", Snackbar.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Snackbar.make(requireView(), "Не удалось сохранить", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createNameInputLayout(): TextInputLayout {
        return TextInputLayout(requireContext()).apply {
            hint = "ФИО руководителя"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            boxBackgroundColor = android.graphics.Color.parseColor("#0F172A")
            setBoxStrokeColorStateList(
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.border_subtle)
                )
            )
        }
    }

    private fun createNameEditText(): TextInputEditText {
        return TextInputEditText(requireContext()).apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_dark))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
        }
    }
}
