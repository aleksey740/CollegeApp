package com.sakovich.collegeapp.presentation.clubs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Club
import com.sakovich.collegeapp.data.models.ClubType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.ClubRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClubsFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var clubRepository: ClubRepository
    private lateinit var userRepository: UserRepository

    private lateinit var tabClubsButton: Chip
    private lateinit var tabSectionsButton: Chip
    private lateinit var tabElectivesButton: Chip
    private lateinit var countText: TextView
    private lateinit var clubsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var addClubFab: FloatingActionButton
    private lateinit var clubsBackButton: ImageButton
    private lateinit var clubsMoreButton: ImageButton
    private lateinit var adapter: ClubsAdapter

    private var currentUser: User? = null
    private val allItems = mutableListOf<Club>()
    private var currentType = ClubType.CLUB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_clubs, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth
        clubRepository = ClubRepository()
        userRepository = UserRepository()

        tabClubsButton = view.findViewById(R.id.tabClubsButton)
        tabSectionsButton = view.findViewById(R.id.tabSectionsButton)
        tabElectivesButton = view.findViewById(R.id.tabElectivesButton)
        countText = view.findViewById(R.id.countText)
        clubsRecyclerView = view.findViewById(R.id.clubsRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        progressBar = view.findViewById(R.id.progressBar)
        addClubFab = view.findViewById(R.id.addClubFab)
        clubsBackButton = view.findViewById(R.id.clubsBackButton)
        clubsMoreButton = view.findViewById(R.id.clubsMoreButton)

        adapter = ClubsAdapter(emptyList()) { club ->
            openClubDetails(club.id)
        }
        clubsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        clubsRecyclerView.adapter = adapter

        tabClubsButton.setOnClickListener {
            currentType = ClubType.CLUB
            render()
        }
        tabSectionsButton.setOnClickListener {
            currentType = ClubType.SECTION
            render()
        }
        tabElectivesButton.setOnClickListener {
            currentType = ClubType.ELECTIVE
            render()
        }

        addClubFab.setOnClickListener { showAddDialog() }

        clubsBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        clubsMoreButton.setOnClickListener { showClubReminderBottomSheet() }

        loadCurrentUserAndData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadCurrentUserAndData() {
        val uid = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            currentUser = userRepository.getUser(uid)
            withContext(Dispatchers.Main) {
                addClubFab.visibility = if (currentUser?.isTeacher() == true) View.VISIBLE else View.GONE
                val show = currentUser?.isStudent() == true || currentUser?.isHeadman() == true
                clubsMoreButton.visibility = if (show) View.VISIBLE else View.GONE
                if (show) {
                    ClubReminderScheduler.scheduleForUser(requireContext(), uid)
                }
                loadData()
            }
        }
    }

    private fun showClubReminderBottomSheet() {
        val user = currentUser ?: return
        if (!(user.isStudent() || user.isHeadman())) return

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_club_reminder_options, null)

        val cb24 = view.findViewById<MaterialCheckBox>(R.id.club24hCheckBox)
        val cb2h = view.findViewById<MaterialCheckBox>(R.id.club2hCheckBox)
        val cb30m = view.findViewById<MaterialCheckBox>(R.id.club30mCheckBox)

        val opts = ClubReminderScheduler.getUserOptionsForUi(requireContext(), user.id)
        cb24.isChecked = opts.interval24hEnabled
        cb2h.isChecked = opts.interval2hEnabled
        cb30m.isChecked = opts.interval30mEnabled

        val applyBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.applyClubReminderButton)
        applyBtn.setOnClickListener {
            ClubReminderScheduler.setUserOptions(
                requireContext(),
                user.id,
                interval24hEnabled = cb24.isChecked,
                interval2hEnabled = cb2h.isChecked,
                interval30mEnabled = cb30m.isChecked
            )
            ClubReminderScheduler.scheduleForUser(requireContext(), user.id)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val user = currentUser
            val groupId = user?.let {
                GroupRepository.effectiveGroupIdForUser(it.groupName, it.groupId, it.group)
            }.orEmpty()
            val groupName = user?.groupName?.ifBlank { user.group }.orEmpty()
            val data = if (user?.isTeacher() == true) {
                clubRepository.getByTeacherId(uid)
                    .filter { ClubRepository.belongsToGroup(it, groupId, groupName) }
            } else {
                clubRepository.getActiveForGroup(groupId, groupName)
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                allItems.clear()
                allItems.addAll(data)
                render()
            }
        }
    }

    private fun render() {
        tabClubsButton.isChecked = currentType == ClubType.CLUB
        tabSectionsButton.isChecked = currentType == ClubType.SECTION
        tabElectivesButton.isChecked = currentType == ClubType.ELECTIVE

        val filtered = allItems.filter { it.type == currentType }
        adapter.update(filtered)
        countText.text = "Найдено: ${filtered.size}"
        if (filtered.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            emptyText.text = when (currentType) {
                ClubType.CLUB -> "Кружки не найдены"
                ClubType.SECTION -> "Секции не найдены"
                ClubType.ELECTIVE -> "Факультативы не найдены"
            }
        } else {
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun showAddDialog(editing: Club? = null) {
        val user = currentUser ?: return
        if (!user.isTeacher()) {
            Snackbar.make(requireView(), "Только преподаватель может управлять", Snackbar.LENGTH_SHORT).show()
            return
        }
        val groupId = GroupRepository.effectiveGroupIdForUser(user.groupName, user.groupId, user.group)
        val groupName = user.groupName.ifBlank { user.group }
        val dialog = AddClubDialogFragment.newInstance(
            teacherId = user.id,
            teacherName = user.fullName.ifBlank { user.email },
            editingClub = editing,
            groupId = groupId,
            groupName = groupName,
            initialType = if (editing == null) currentType else null
        )
        dialog.setOnSavedListener { club ->
            CoroutineScope(Dispatchers.IO).launch {
                val clubGroupId = ClubRepository.clubGroupId(club).ifBlank { groupId }
                val exists = if (editing == null) {
                    clubRepository.existsByName(club.name, clubGroupId, club.type)
                } else {
                    clubRepository.existsByName(club.name, clubGroupId, club.type, excludeId = club.id)
                }
                if (exists) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(requireView(), "Данный кружок/секция/факультатив уже создан", Snackbar.LENGTH_LONG).show()
                    }
                    return@launch
                }
                if (editing == null) {
                    clubRepository.create(club)
                } else {
                    clubRepository.update(club)
                }
                withContext(Dispatchers.Main) { loadData() }
            }
        }
        dialog.show(parentFragmentManager, "AddClubDialog")
    }

    private fun openClubDetails(clubId: String) {
        val fragment = ClubDetailsFragment.newInstance(clubId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
