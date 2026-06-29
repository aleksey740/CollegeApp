package com.sakovich.collegeapp.presentation.teacher

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.DrawableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudentInfoFragment : Fragment() {

    private lateinit var userRepository: UserRepository
    private lateinit var progressBar: ProgressBar

    private lateinit var avatarText: TextView
    private lateinit var fullNameText: TextView
    private lateinit var groupBadge: TextView
    private lateinit var roleBadge: TextView

    private lateinit var emailText: TextView
    private lateinit var phoneLayout: LinearLayout
    private lateinit var phoneText: TextView

    private lateinit var personalInfoCard: MaterialCardView
    private lateinit var birthDateLayout: LinearLayout
    private lateinit var birthDateText: TextView
    private lateinit var addressLayout: LinearLayout
    private lateinit var addressText: TextView
    private lateinit var socialCategoriesContainer: LinearLayout

    private lateinit var parentsCard: MaterialCardView
    private lateinit var parentNameLayout: LinearLayout
    private lateinit var parentNameText: TextView
    private lateinit var parentPhoneLayout: LinearLayout
    private lateinit var parentPhoneText: TextView

    private lateinit var noInfoCard: MaterialCardView

    private var studentId: String = ""
    private var studentName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_student_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userRepository = UserRepository()

        studentId = arguments?.getString("studentId") ?: ""
        studentName = arguments?.getString("studentName") ?: ""

        initViews(view)
        loadStudentInfo()
    }

    private fun initViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)

        avatarText = view.findViewById(R.id.avatarText)
        fullNameText = view.findViewById(R.id.fullNameText)
        groupBadge = view.findViewById(R.id.groupBadge)
        roleBadge = view.findViewById(R.id.roleBadge)

        emailText = view.findViewById(R.id.emailText)
        phoneLayout = view.findViewById(R.id.phoneLayout)
        phoneText = view.findViewById(R.id.phoneText)

        personalInfoCard = view.findViewById(R.id.personalInfoCard)
        birthDateLayout = view.findViewById(R.id.birthDateLayout)
        birthDateText = view.findViewById(R.id.birthDateText)
        addressLayout = view.findViewById(R.id.addressLayout)
        addressText = view.findViewById(R.id.addressText)
        socialCategoriesContainer = view.findViewById(R.id.socialCategoriesContainer)

        parentsCard = view.findViewById(R.id.parentsCard)
        parentNameLayout = view.findViewById(R.id.parentNameLayout)
        parentNameText = view.findViewById(R.id.parentNameText)
        parentPhoneLayout = view.findViewById(R.id.parentPhoneLayout)
        parentPhoneText = view.findViewById(R.id.parentPhoneText)

        noInfoCard = view.findViewById(R.id.noInfoCard)

        view.findViewById<ImageButton>(R.id.studentInfoBackButton).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadStudentInfo() {
        if (studentId.isEmpty()) {
            showError("ID учащегося не указан")
            return
        }

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = userRepository.getUser(studentId)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (user != null) {
                        displayStudentInfo(user)
                    } else {
                        showError("Информация об учащемся не найдена")
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    showError("Ошибка загрузки: ${e.message}")
                }
            }
        }
    }

    private fun displayStudentInfo(user: User) {

        fullNameText.text = user.fullName.ifEmpty { "Имя не указано" }

        val initials = getInitials(user.fullName)
        avatarText.text = initials
        DrawableUtils.setViewBackgroundColorHex(
            avatarText,
            DrawableUtils.colorForName(user.fullName)
        )

        val groupName = user.groupName.ifEmpty { user.group.ifEmpty { "Не указана" } }
        groupBadge.text = "🎓 $groupName"

        if (user.isHeadman()) {
            roleBadge.visibility = View.VISIBLE
        } else {
            roleBadge.visibility = View.GONE
        }

        emailText.text = user.email.ifEmpty { "Не указан" }

        if (user.phone.isNotEmpty()) {
            phoneLayout.visibility = View.VISIBLE
            phoneText.text = user.phone
        } else {
            phoneLayout.visibility = View.GONE
        }

        val hasSocialCategories = user.livesInDormitory || user.isDisabled || user.isLargeFamily ||
                user.fundingType.isNotEmpty() || user.isLowIncome || user.isOrphan || user.isNonResident
        val hasPersonalInfo = user.birthDate.isNotEmpty() || user.address.isNotEmpty() || hasSocialCategories
        val hasParentInfo = user.parentName.isNotEmpty() || user.parentPhone.isNotEmpty()

        if (hasPersonalInfo) {
            personalInfoCard.visibility = View.VISIBLE

            if (user.birthDate.isNotEmpty()) {
                birthDateLayout.visibility = View.VISIBLE
                birthDateText.text = user.birthDate
            } else {
                birthDateLayout.visibility = View.GONE
            }

            if (user.address.isNotEmpty()) {
                addressLayout.visibility = View.VISIBLE
                addressText.text = user.address
            } else {
                addressLayout.visibility = View.GONE
            }

            socialCategoriesContainer.removeAllViews()
            if (hasSocialCategories) {
                socialCategoriesContainer.visibility = View.VISIBLE
                val header = TextView(requireContext()).apply {
                    text = "Социальные категории"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                    setPadding(0, 8, 0, 4)
                }
                socialCategoriesContainer.addView(header)
                val labels = mutableListOf<String>()
                if (user.fundingType.isNotEmpty()) {
                    val fundingLabel = when (user.fundingType) {
                        "budget" -> "Бюджет"
                        "paid" -> "Платник"
                        else -> user.fundingType
                    }
                    labels.add("💰 Форма обучения: $fundingLabel")
                }
                if (user.livesInDormitory) labels.add("🏠 Общежитие")
                if (user.isDisabled) labels.add("♿ Инвалид")
                if (user.isLargeFamily) labels.add("👨‍👩‍👧‍👦 Многодетный")
                if (user.isLowIncome) labels.add("📉 Малообеспеченная семья")
                if (user.isOrphan) labels.add("🕊️ Сирота")
                if (user.isNonResident) labels.add("🚌 Иногородний")
                labels.forEach { label ->
                    val row = TextView(requireContext()).apply {
                        text = label
                        setTextColor(Color.parseColor("#E2E8F0"))
                        textSize = 14f
                        setPadding(0, 6, 0, 6)
                    }
                    socialCategoriesContainer.addView(row)
                }
            } else {
                socialCategoriesContainer.visibility = View.GONE
            }
        } else {
            personalInfoCard.visibility = View.GONE
        }

        if (hasParentInfo) {
            parentsCard.visibility = View.VISIBLE

            if (user.parentName.isNotEmpty()) {
                parentNameLayout.visibility = View.VISIBLE
                parentNameText.text = user.parentName
            } else {
                parentNameLayout.visibility = View.GONE
            }

            if (user.parentPhone.isNotEmpty()) {
                parentPhoneLayout.visibility = View.VISIBLE
                parentPhoneText.text = user.parentPhone
            } else {
                parentPhoneLayout.visibility = View.GONE
            }
        } else {
            parentsCard.visibility = View.GONE
        }

        if (!hasPersonalInfo && !hasParentInfo && user.phone.isEmpty()) {
            noInfoCard.visibility = View.VISIBLE
        } else {
            noInfoCard.visibility = View.GONE
        }
    }

    private fun getInitials(fullName: String): String {
        val parts = fullName.trim().split(" ")
        return when {
            parts.size >= 2 -> "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "??"
        }
    }

    private fun showError(message: String) {
        fullNameText.text = message
        groupBadge.visibility = View.GONE
        roleBadge.visibility = View.GONE
        personalInfoCard.visibility = View.GONE
        parentsCard.visibility = View.GONE
        noInfoCard.visibility = View.GONE
    }

    companion object {
        fun newInstance(studentId: String, studentName: String): StudentInfoFragment {
            val fragment = StudentInfoFragment()
            val args = Bundle()
            args.putString("studentId", studentId)
            args.putString("studentName", studentName)
            fragment.arguments = args
            return fragment
        }
    }
}
