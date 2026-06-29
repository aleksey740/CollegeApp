package com.sakovich.collegeapp.presentation.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.presentation.absences.ManageAbsencesFragment
import com.sakovich.collegeapp.presentation.clubs.ClubsFragment
import com.sakovich.collegeapp.presentation.curatorial.CuratorialInfoHoursFragment
import com.sakovich.collegeapp.presentation.events.GroupEventsFragment
import com.sakovich.collegeapp.presentation.nutrition.NutritionFragment
import com.sakovich.collegeapp.presentation.schedule.ScheduleFragment
import com.sakovich.collegeapp.presentation.statistics.TeacherStatisticsFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TeacherProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()

        val currentUser = auth.currentUser

        val teacherBackButton = view.findViewById<ImageButton>(R.id.teacherBackButton)
        val welcomeText = view.findViewById<TextView>(R.id.welcomeText)
        val userEmailText = view.findViewById<TextView>(R.id.userEmailText)
        val userRoleText = view.findViewById<TextView>(R.id.userRoleText)

        teacherBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val addGradesBtn = view.findViewById<MaterialCardView>(R.id.addGradesBtn)
        val viewGroupsBtn = view.findViewById<MaterialCardView>(R.id.viewGroupsBtn)
        val attendanceBtn = view.findViewById<MaterialCardView>(R.id.attendanceBtn)
        val statisticsBtn = view.findViewById<MaterialCardView>(R.id.statisticsBtn)
        val curatorialInfoHoursBtn = view.findViewById<MaterialCardView>(R.id.curatorialInfoHoursBtn)
        val clubsManageBtn = view.findViewById<MaterialCardView>(R.id.clubsManageBtn)
        val nutritionBtn = view.findViewById<MaterialCardView>(R.id.nutritionBtn)
        val scheduleMainBtn = view.findViewById<MaterialCardView>(R.id.scheduleMainBtn)
        val eventsBtn = view.findViewById<MaterialCardView>(R.id.eventsBtn)

        userEmailText.text = currentUser?.email ?: "Неизвестно"

        currentUser?.uid?.let { userId ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val user = userRepository.getUser(userId)
                    withContext(Dispatchers.Main) {
                        if (user != null && user.fullName.isNotEmpty()) {
                            welcomeText.text = user.fullName
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }

        addGradesBtn.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                val user = userRepository.getUser(uid)
                val groupName = user?.groupName?.takeIf { it.isNotBlank() } ?: user?.group?.takeIf { it.isNotBlank() }
                withContext(Dispatchers.Main) {
                    if (!groupName.isNullOrBlank()) {
                        val journalFragment = TeacherGradeJournalFragment.newInstance(groupName)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, journalFragment)
                            .addToBackStack(null)
                            .commit()
                    } else {
                        Toast.makeText(requireContext(), "У вас не указана группа. Обратитесь к администратору.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewGroupsBtn.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                val user = userRepository.getUser(uid)
                val groupName = user?.groupName?.takeIf { it.isNotBlank() } ?: user?.group?.takeIf { it.isNotBlank() }
                withContext(Dispatchers.Main) {
                    if (!groupName.isNullOrBlank()) {
                        val studentsFragment = TeacherStudentsFragment.newInstance(
                            groupName = groupName,
                            groupDisplayName = groupName,
                            mode = TeacherStudentsFragment.MODE_INFO
                        )
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, studentsFragment)
                            .addToBackStack(null)
                            .commit()
                    } else {
                        Toast.makeText(requireContext(), "У вас не указана группа. Обратитесь к администратору.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        attendanceBtn.setOnClickListener {
            val manageAbsencesFragment = ManageAbsencesFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, manageAbsencesFragment)
                .addToBackStack(null)
                .commit()
        }

        statisticsBtn.setOnClickListener {
            val statisticsFragment = TeacherStatisticsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, statisticsFragment)
                .addToBackStack(null)
                .commit()
        }

        curatorialInfoHoursBtn.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                val user = userRepository.getUser(uid)
                withContext(Dispatchers.Main) {
                    if (user?.groupName?.isNotBlank() == true || user?.group?.isNotBlank() == true) {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, CuratorialInfoHoursFragment())
                            .addToBackStack(null)
                            .commit()
                    } else {
                        Toast.makeText(requireContext(), "У вас не указана группа. Обратитесь к администратору.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        clubsManageBtn.setOnClickListener {
            val clubsFragment = ClubsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, clubsFragment)
                .addToBackStack(null)
                .commit()
        }

        nutritionBtn.setOnClickListener {
            val nutritionFragment = NutritionFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, nutritionFragment)
                .addToBackStack(null)
                .commit()
        }

        scheduleMainBtn.setOnClickListener {
            val scheduleFragment = ScheduleFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, scheduleFragment)
                .addToBackStack(null)
                .commit()
        }

        eventsBtn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GroupEventsFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
