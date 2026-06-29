package com.sakovich.collegeapp.presentation.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.presentation.teacher.adapters.StudentsAdapter
import com.sakovich.collegeapp.utils.DrawableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class TeacherStudentsFragment : Fragment() {

    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var groupNameText: TextView
    private lateinit var modeText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var studentsSectionTitle: TextView
    private lateinit var studentsCountSubtitle: TextView
    private lateinit var studentsCountBadge: TextView
    private lateinit var studentsSummaryCard: View

    private lateinit var teacherStudentsBackButton: ImageButton

    private lateinit var groupRepository: GroupRepository

    private val studentsList = mutableListOf<Student>()
    private var groupName: String = ""
    private var mode: String = MODE_INFO
    private var headmanName: String? = null

    companion object {
        const val MODE_INFO = "info"
        const val MODE_GRADES = "grades"

        fun newInstance(groupName: String, groupDisplayName: String, mode: String = MODE_INFO): TeacherStudentsFragment {
            val fragment = TeacherStudentsFragment()
            val args = Bundle()
            args.putString("groupName", groupName)
            args.putString("groupDisplayName", groupDisplayName)
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
        return inflater.inflate(R.layout.fragment_teacher_students, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupRepository = GroupRepository()

        initViews(view)

        groupName = arguments?.getString("groupName") ?: ""
        val groupDisplayName = arguments?.getString("groupDisplayName") ?: groupName
        mode = arguments?.getString("mode") ?: MODE_INFO

        groupNameText.text = "Моя группа: $groupDisplayName"
        updateModeUI()

        setupRecyclerView()
        loadStudentsByGroup()
    }

    private fun initViews(view: View) {
        studentsRecyclerView = view.findViewById(R.id.studentsRecyclerView)
        groupNameText = view.findViewById(R.id.groupNameText)
        modeText = view.findViewById(R.id.modeText)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        studentsSectionTitle = view.findViewById(R.id.studentsSectionTitle)
        studentsCountSubtitle = view.findViewById(R.id.studentsCountSubtitle)
        studentsCountBadge = view.findViewById(R.id.studentsCountBadge)
        studentsSummaryCard = view.findViewById(R.id.studentsSummaryCard)

        teacherStudentsBackButton = view.findViewById(R.id.teacherStudentsBackButton)
        teacherStudentsBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        DrawableUtils.setViewBackgroundColorHex(studentsCountBadge, "#8B5CF6")
    }

    private fun updateModeUI() {
        when (mode) {
            MODE_INFO -> {
                modeText.text = "Нажмите на карточку для просмотра информации"
            }
            MODE_GRADES -> {
                modeText.text = "Нажмите на карточку для выставления отметок"
            }
        }
    }

    private fun setupRecyclerView() {
        studentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapterMode = when (mode) {
            MODE_GRADES -> StudentsAdapter.MODE_GRADES
            else -> StudentsAdapter.MODE_INFO
        }

        studentsRecyclerView.adapter = StudentsAdapter(
            students = studentsList,
            onStudentClick = { student ->
                when (mode) {
                    MODE_INFO -> openStudentInfo(student)
                    MODE_GRADES -> openStudentGrades(student)
                }
            },
            onGradesClick = if (mode == MODE_GRADES) { student -> openStudentGrades(student) } else null,
            mode = adapterMode
        )
    }

    private fun openStudentInfo(student: Student) {
        val infoFragment = StudentInfoFragment.newInstance(
            studentId = student.id,
            studentName = student.fullName
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, infoFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openStudentGrades(student: Student) {
        val gradesFragment = StudentGradesFragment.newInstance(
            studentId = student.id,
            studentName = student.fullName,
            groupName = groupName
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, gradesFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun updateStudentsCount(count: Int) {
        studentsCountBadge.text = count.toString()
        studentsCountSubtitle.text = when (count) {
            0 -> "В группе пока никого нет"
            1 -> "Всего 1 учащийся"
            else -> "Всего $count учащихся"
        }
    }

    private fun loadStudentsByGroup() {
        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        studentsSummaryCard.visibility = View.VISIBLE
        updateStudentsCount(0)
        studentsCountSubtitle.text = "Загрузка…"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val students = groupRepository.getStudentsByGroup(groupName)
                val sortedStudents = students.sortedBy { it.fullName.trim().lowercase(Locale.getDefault()) }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressBar.visibility = View.GONE
                    studentsList.clear()
                    studentsList.addAll(sortedStudents)
                    val headman = sortedStudents.find { it.isHeadman }
                    headmanName = if (headman != null) getShortName(headman.fullName) else null

                    if (sortedStudents.isEmpty()) {
                        showEmptyState("В группе пока нет учащихся")
                        studentsSummaryCard.visibility = View.GONE
                    } else {
                        hideEmptyState()
                        studentsSummaryCard.visibility = View.VISIBLE
                        updateStudentsCount(sortedStudents.size)
                        studentsRecyclerView.adapter?.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressBar.visibility = View.GONE
                    showEmptyState("Ошибка загрузки")
                    studentsSummaryCard.visibility = View.GONE
                }
            }
        }
    }

    private fun getShortName(fullName: String): String {
        val parts = fullName.split(" ")
        return when {
            parts.size >= 2 -> "${parts[0]} ${parts[1].firstOrNull() ?: ""}."
            parts.isNotEmpty() -> parts[0]
            else -> fullName
        }
    }

    private fun showEmptyState(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        emptyText.text = message
        studentsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
        studentsRecyclerView.visibility = View.VISIBLE
    }

}
