package com.sakovich.collegeapp.presentation.headman

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.presentation.absences.ManageAbsencesFragment
import com.sakovich.collegeapp.presentation.nutrition.NutritionFragment
import com.sakovich.collegeapp.presentation.events.GroupEventsFragment
import com.sakovich.collegeapp.presentation.schedule.ScheduleFragment
import com.sakovich.collegeapp.presentation.statistics.HeadmanStatisticsFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HeadmanProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_headman_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()

        val currentUser = auth.currentUser

        val headmanBackButton = view.findViewById<ImageButton>(R.id.headmanBackButton)
        val welcomeText = view.findViewById<TextView>(R.id.welcomeText)
        val userEmailText = view.findViewById<TextView>(R.id.userEmailText)
        val userRoleText = view.findViewById<TextView>(R.id.userRoleText)
        val groupBadge = view.findViewById<TextView>(R.id.groupBadge)

        headmanBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val attendanceBtn = view.findViewById<MaterialCardView>(R.id.attendanceBtn)
        val scheduleBtn = view.findViewById<MaterialCardView>(R.id.scheduleBtn)
        val eventsBtn = view.findViewById<MaterialCardView>(R.id.eventsBtn)
        val statisticsBtn = view.findViewById<MaterialCardView>(R.id.statisticsBtn)
        val nutritionBtn = view.findViewById<MaterialCardView>(R.id.nutritionBtn)

        userEmailText.text = currentUser?.email ?: "Неизвестно"

        currentUser?.uid?.let { userId ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val user = userRepository.getUser(userId)
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            if (user.fullName.isNotEmpty()) {
                                welcomeText.text = user.fullName
                            }
                            if (user.groupName.isNotEmpty()) {
                                groupBadge.text = user.groupName
                                groupBadge.visibility = View.VISIBLE
                            }
                        }
                    }
                } catch (e: Exception) {

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

        scheduleBtn.setOnClickListener {
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

        statisticsBtn.setOnClickListener {
            val statisticsFragment = HeadmanStatisticsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, statisticsFragment)
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

    }
}
