package com.sakovich.collegeapp.presentation.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Group
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.presentation.teacher.adapters.GroupsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TeacherGroupsFragment : Fragment() {

    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var modeText: TextView

    private lateinit var groupRepository: GroupRepository
    private lateinit var auth: FirebaseAuth

    private val groupsList = mutableListOf<Group>()
    private var mode: String = TeacherStudentsFragment.MODE_INFO

    companion object {
        fun newInstance(mode: String = TeacherStudentsFragment.MODE_INFO): TeacherGroupsFragment {
            val fragment = TeacherGroupsFragment()
            val args = Bundle()
            args.putString("mode", mode)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_groups, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        groupRepository = GroupRepository()

        initViews(view)

        mode = arguments?.getString("mode") ?: TeacherStudentsFragment.MODE_INFO
        updateUI()

        setupRecyclerView()
        loadTeacherGroups()
    }

    private fun initViews(view: View) {
        groupsRecyclerView = view.findViewById(R.id.groupsRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        progressBar = view.findViewById(R.id.progressBar)
        titleText = view.findViewById(R.id.titleText)
        modeText = view.findViewById(R.id.modeText)
    }

    private fun updateUI() {
        when (mode) {
            TeacherStudentsFragment.MODE_INFO -> {
                titleText.text = "👥 Мои группы"
                modeText.text = "👁️ Режим: Просмотр информации об учащихся"
            }
            TeacherStudentsFragment.MODE_GRADES -> {
                titleText.text = "📝 Выставить отметки"
                modeText.text = "📊 Режим: Выставление отметок учащимся"
            }
        }
    }

    private fun setupRecyclerView() {
        groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        groupsRecyclerView.adapter = GroupsAdapter(groupsList) { group ->
            if (mode == TeacherStudentsFragment.MODE_GRADES) {
                val journalFragment = TeacherGradeJournalFragment.newInstance(group.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, journalFragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                val studentsFragment = TeacherStudentsFragment.newInstance(
                    groupName = group.name,
                    groupDisplayName = group.name,
                    mode = mode
                )
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, studentsFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun loadTeacherGroups() {
        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState("Пользователь не авторизован")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groups = groupRepository.getTeacherGroups(currentUser.uid)

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressBar.visibility = View.GONE
                    groupsList.clear()
                    groupsList.addAll(groups)
                    if (groups.isEmpty()) {
                        showEmptyState("У вас пока нет групп")
                    } else {
                        hideEmptyState()
                        groupsRecyclerView.adapter?.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressBar.visibility = View.GONE
                    showEmptyState("Ошибка загрузки")
                }
            }
        }
    }

    private fun showEmptyState(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        emptyText.text = message
        groupsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
        groupsRecyclerView.visibility = View.VISIBLE
    }
}
