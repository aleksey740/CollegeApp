package com.sakovich.collegeapp.presentation.teacher
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Group
import com.sakovich.collegeapp.presentation.teacher.adapters.GroupsAdapter

class TeacherGroupsFragment : Fragment() {

    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private val groupsList = mutableListOf<Group>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_groups, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupsRecyclerView = view.findViewById(R.id.groupsRecyclerView)
        emptyText = view.findViewById(R.id.emptyText)

        setupRecyclerView()
        loadTestGroupsData()
    }

    private fun setupRecyclerView() {
        groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        groupsRecyclerView.adapter = GroupsAdapter(groupsList) { group ->
            // Переход к списку студентов группы - ИСПРАВЛЕННАЯ СТРОКА
            val studentsFragment = TeacherStudentsFragment.newInstance(group.id, group.name)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, studentsFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadTestGroupsData() {
        groupsList.clear()
        groupsList.addAll(listOf(
            Group(id = "group1", name = "ИТ-21", studentCount = 25),
            Group(id = "group2", name = "ИТ-22", studentCount = 28),
            Group(id = "group3", name = "ФИЗ-21", studentCount = 20),
            Group(id = "group4", name = "ФИЗ-22", studentCount = 22)
        ))

        if (groupsList.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            groupsRecyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            groupsRecyclerView.visibility = View.VISIBLE
            groupsRecyclerView.adapter?.notifyDataSetChanged()
        }
    }

    companion object {
        fun newInstance() = TeacherGroupsFragment()
    }
}