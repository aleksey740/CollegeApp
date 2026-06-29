package com.sakovich.collegeapp.presentation.nutrition

import android.app.DatePickerDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.MealSubscription
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.MealRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.StatisticsExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NutritionFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var mealRepository: MealRepository

    private lateinit var dateText: TextView
    private lateinit var prevDateBtn: MaterialButton
    private lateinit var nextDateBtn: MaterialButton
    private lateinit var studentCard: View
    private lateinit var myStatusText: TextView
    private lateinit var toggleMealBtn: MaterialButton
    private lateinit var weeklyPlanBtn: MaterialButton
    private lateinit var weeklyAutoSwitch: MaterialSwitch
    private lateinit var nutritionMoreButton: ImageButton
    private lateinit var exportButton: MaterialButton
    private lateinit var nutritionBackButton: ImageButton
    private lateinit var listTitleText: TextView
    private lateinit var participantsRecyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: NutritionParticipantsAdapter

    private var currentUser: User? = null
    private val selectedDateCalendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale("ru"))
    private var currentIsSubscribed = false
    private var loadedSubscriptions = emptyList<MealSubscription>()
    private var loadedEligibleTotal = 0
    private var updatingAutoSwitch = false

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                savePdfToUri(uri)
            } else {
                exportButton.isEnabled = true
            }
        } else {
            exportButton.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_nutrition, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth
        userRepository = UserRepository()
        mealRepository = MealRepository()

        dateText = view.findViewById(R.id.dateText)
        nutritionBackButton = view.findViewById(R.id.nutritionBackButton)
        prevDateBtn = view.findViewById(R.id.prevDateBtn)
        nextDateBtn = view.findViewById(R.id.nextDateBtn)
        studentCard = view.findViewById(R.id.studentCard)
        myStatusText = view.findViewById(R.id.myStatusText)
        toggleMealBtn = view.findViewById(R.id.toggleMealBtn)
        weeklyPlanBtn = view.findViewById(R.id.weeklyPlanBtn)
        weeklyAutoSwitch = view.findViewById(R.id.weeklyAutoSwitch)
        nutritionMoreButton = view.findViewById(R.id.nutritionMoreButton)
        exportButton = view.findViewById(R.id.exportButton)
        listTitleText = view.findViewById(R.id.listTitleText)
        participantsRecyclerView = view.findViewById(R.id.participantsRecyclerView)
        emptyText = view.findViewById(R.id.emptyText)
        progressBar = view.findViewById(R.id.progressBar)

        adapter = NutritionParticipantsAdapter(emptyList())
        participantsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        participantsRecyclerView.adapter = adapter

        prevDateBtn.setOnClickListener {
            selectedDateCalendar.add(Calendar.DAY_OF_MONTH, -1)
            skipToPreviousWeekday()
            refresh()
        }
        nextDateBtn.setOnClickListener {
            selectedDateCalendar.add(Calendar.DAY_OF_MONTH, 1)
            skipToNextWeekday()
            refresh()
        }
        dateText.setOnClickListener { showDatePicker() }
        toggleMealBtn.setOnClickListener { toggleSubscription() }
        weeklyPlanBtn.setOnClickListener { showWeeklyPlanDialog() }
        weeklyAutoSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingAutoSwitch) onWeeklyAutoSwitchChanged(checked)
        }
        exportButton.setOnClickListener { showExportOptions() }

        nutritionBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        nutritionMoreButton.setOnClickListener { showMealReminderBottomSheet() }

        loadCurrentUser()
    }

    override fun onResume() {
        super.onResume()
        if (currentUser != null) refresh()
    }

    private fun loadCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val user = userRepository.getUser(uid)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                currentUser = user
                if (user != null) {
                    ensureWeeklyAutoPlanIfNeeded(user)
                }
                refresh()
                val show = user != null && (user.isStudent() || user.isHeadman())
                nutritionMoreButton.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showMealReminderBottomSheet() {
        val user = currentUser ?: return
        if (!(user.isStudent() || user.isHeadman())) return

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_meal_reminder_options, null)

        val cbMorning = view.findViewById<MaterialCheckBox>(R.id.mealMorningCheckBox)
        val cbEve = view.findViewById<MaterialCheckBox>(R.id.mealEveCheckBox)

        val opts = MealReminderScheduler.getUserOptionsForUi(requireContext(), user.id)
        cbMorning.isChecked = opts.morningEnabled
        cbEve.isChecked = opts.eveEnabled

        val applyBtn = view.findViewById<MaterialButton>(R.id.applyMealReminderButton)
        applyBtn.setOnClickListener {
            MealReminderScheduler.setUserOptions(
                requireContext(),
                user.id,
                morningEnabled = cbMorning.isChecked,
                eveEnabled = cbEve.isChecked
            )
            MealReminderScheduler.scheduleForUser(requireContext(), user.id)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun refresh() {
        val user = currentUser ?: return
        val selectedDate = dateFormat.format(selectedDateCalendar.time)
        val dayOfWeek = dayOfWeekFormat.format(selectedDateCalendar.time).replaceFirstChar { it.uppercase() }
        dateText.text = "$selectedDate\n$dayOfWeek"

        val isTeacher = user.isTeacher()
        val isHeadman = user.isHeadman()
        val isStudent = user.isStudent()

        studentCard.visibility = if (isStudent || isHeadman) View.VISIBLE else View.GONE

        listTitleText.visibility = if (isTeacher || isHeadman) View.VISIBLE else View.GONE
        participantsRecyclerView.visibility = if (isTeacher || isHeadman) View.VISIBLE else View.GONE
        exportButton.visibility = if (isTeacher || isHeadman) View.VISIBLE else View.GONE
        emptyText.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val mySubscription = mealRepository.getUserSubscription(selectedDate, user.id)

            val groupFilter = user.groupName.takeIf { it.isNotBlank() }
            val subscribed = if (isTeacher || isHeadman) {
                mealRepository.getSubscribedByDate(selectedDate, groupFilter)
            } else {
                emptyList()
            }
            val eligibleUsers = if (isTeacher || isHeadman) {
                userRepository.getAllUsers().filter {
                    (it.role == "student" || it.role == "headman") &&
                        (groupFilter == null || it.groupName == groupFilter)
                }
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                if (isStudent || isHeadman) {
                    val weekend = isSelectedDateWeekend()
                    currentIsSubscribed = mySubscription?.isSubscribed == true
                    myStatusText.text = if (weekend) {
                        "В выходные (СБ/ВС) запись на питание недоступна"
                    } else if (currentIsSubscribed) {
                        "Вы записаны на питание на $selectedDate"
                    } else {
                        "Вы не записаны на питание на $selectedDate"
                    }
                    toggleMealBtn.text = if (currentIsSubscribed) "Отписаться от питания" else "Записаться на питание"
                    toggleMealBtn.isEnabled = !weekend || currentIsSubscribed
                    weeklyPlanBtn.isEnabled = !weekend
                    updatingAutoSwitch = true
                    weeklyAutoSwitch.isChecked = user.mealAutoPlanEnabled
                    updatingAutoSwitch = false
                    weeklyAutoSwitch.visibility = View.VISIBLE
                } else {
                    weeklyAutoSwitch.visibility = View.GONE
                }

                if (isTeacher || isHeadman) {
                    adapter.update(subscribed)
                    loadedSubscriptions = subscribed
                    loadedEligibleTotal = eligibleUsers.size
                    emptyText.visibility = if (subscribed.isEmpty()) View.VISIBLE else View.GONE
                    emptyText.text = "На выбранную дату никто не записан"
                }
            }
        }
    }

    private fun toggleSubscription() {
        val user = currentUser ?: return
        if (!(user.isStudent() || user.isHeadman())) return
        if (isSelectedDateWeekend() && !currentIsSubscribed) {
            Snackbar.make(requireView(), "В СБ и ВС записаться на питание нельзя", Snackbar.LENGTH_SHORT).show()
            return
        }

        val selectedDate = dateFormat.format(selectedDateCalendar.time)
        val newStatus = !currentIsSubscribed
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val ok = mealRepository.setSubscription(
                date = selectedDate,
                user = user,
                isSubscribed = newStatus,
                updatedById = user.id
            )
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (ok) {
                    currentIsSubscribed = newStatus
                    MealReminderScheduler.scheduleForUser(requireContext(), user.id)
                    refresh()
                } else {
                    Snackbar.make(requireView(), "Не удалось изменить статус", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showWeeklyPlanDialog() {
        val user = currentUser ?: return
        if (!(user.isStudent() || user.isHeadman())) return

        val options = arrayOf("✅ Записать на Пн–Пт", "❌ Отписать на Пн–Пт")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Недельный автоплан")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyWeeklyPlan(user, true)
                    1 -> applyWeeklyPlan(user, false)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun applyWeeklyPlan(user: User, subscribe: Boolean) {
        progressBar.visibility = View.VISIBLE
        val monday = getMondayOfSelectedWeek()

        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            for (i in 0..4) {
                val day = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                val date = dateFormat.format(day.time)
                val ok = mealRepository.setSubscription(
                    date = date,
                    user = user,
                    isSubscribed = subscribe,
                    updatedById = user.id
                )
                if (ok) successCount++
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                MealReminderScheduler.scheduleForUser(requireContext(), user.id)
                refresh()
                val action = if (subscribe) "Записано" else "Отписано"
                Snackbar.make(requireView(), "$action на $successCount из 5 дней (Пн–Пт)", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun onWeeklyAutoSwitchChanged(enabled: Boolean) {
        val user = currentUser ?: return
        if (!(user.isStudent() || user.isHeadman())) return

        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val weekKey = currentWeekKey()
            var applied = false
            if (enabled) {
                applyWeekPlanInternal(user, subscribe = true)
                applied = true
            }
            val saved = userRepository.updateMealAutoPlan(
                userId = user.id,
                enabled = enabled,
                lastAppliedWeek = if (enabled && applied) weekKey else (currentUser?.mealAutoPlanLastAppliedWeek ?: "")
            )
            val refreshedUser = userRepository.getUser(user.id)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (!saved) {
                    updatingAutoSwitch = true
                    weeklyAutoSwitch.isChecked = !enabled
                    updatingAutoSwitch = false
                    Snackbar.make(requireView(), "Не удалось сохранить автоплан", Snackbar.LENGTH_SHORT).show()
                } else {
                    currentUser = refreshedUser ?: currentUser
                    MealReminderScheduler.scheduleForUser(requireContext(), user.id)
                    refresh()
                    val text = if (enabled) "Автоплан включен" else "Автоплан выключен"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun ensureWeeklyAutoPlanIfNeeded(user: User?) {
        val u = user ?: return
        if (!(u.isStudent() || u.isHeadman())) return
        if (!u.mealAutoPlanEnabled) return

        CoroutineScope(Dispatchers.IO).launch {
            val weekKey = currentWeekKey()
            if (u.mealAutoPlanLastAppliedWeek == weekKey) return@launch

            applyWeekPlanInternal(u, subscribe = true)
            userRepository.updateMealAutoPlan(u.id, enabled = true, lastAppliedWeek = weekKey)
            val refreshed = userRepository.getUser(u.id)
            withContext(Dispatchers.Main) {
                currentUser = refreshed ?: currentUser
                MealReminderScheduler.scheduleForUser(requireContext(), u.id)
                refresh()
            }
        }
    }

    private suspend fun applyWeekPlanInternal(user: User, subscribe: Boolean) {
        val monday = getMondayOfCurrentWeek()
        for (i in 0..4) {
            val day = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val date = dateFormat.format(day.time)
            mealRepository.setSubscription(
                date = date,
                user = user,
                isSubscribed = subscribe,
                updatedById = user.id
            )
        }
    }

    private fun getMondayOfSelectedWeek(): Calendar {
        val calendar = selectedDateCalendar.clone() as Calendar
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val shift = when (dayOfWeek) {
            Calendar.SUNDAY -> -6
            else -> Calendar.MONDAY - dayOfWeek
        }
        calendar.add(Calendar.DAY_OF_MONTH, shift)
        return calendar
    }

    private fun getMondayOfCurrentWeek(): Calendar {
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val shift = when (dayOfWeek) {
            Calendar.SUNDAY -> -6
            else -> Calendar.MONDAY - dayOfWeek
        }
        now.add(Calendar.DAY_OF_MONTH, shift)
        return now
    }

    private fun currentWeekKey(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        return "$year-$week"
    }

    private fun isSelectedDateWeekend(): Boolean {
        val day = selectedDateCalendar.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    private fun skipToNextWeekday() {
        if (selectedDateCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) selectedDateCalendar.add(Calendar.DAY_OF_MONTH, 2)
        if (selectedDateCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) selectedDateCalendar.add(Calendar.DAY_OF_MONTH, 1)
    }

    private fun skipToPreviousWeekday() {
        if (selectedDateCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) selectedDateCalendar.add(Calendar.DAY_OF_MONTH, -2)
        if (selectedDateCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) selectedDateCalendar.add(Calendar.DAY_OF_MONTH, -1)
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDateCalendar.set(Calendar.YEAR, year)
                selectedDateCalendar.set(Calendar.MONTH, month)
                selectedDateCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                refresh()
            },
            selectedDateCalendar.get(Calendar.YEAR),
            selectedDateCalendar.get(Calendar.MONTH),
            selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showExportOptions() {
        if (loadedSubscriptions.isEmpty()) {
            Snackbar.make(requireView(), "Нет данных для экспорта", Snackbar.LENGTH_SHORT).show()
            return
        }
        exportToPdf()
    }

    private fun exportToPdf() {
        val date = dateFormat.format(selectedDateCalendar.time)
        val fileName = "nutrition_$date.pdf".replace(".", "_")

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        exportButton.isEnabled = false
        savePdfLauncher.launch(intent)
    }

    private fun savePdfToUri(uri: Uri) {
        try {
            progressBar.visibility = View.VISIBLE
            val user = currentUser
            val responsible = user?.fullName
                ?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName
                ?: auth.currentUser?.email
                ?: "Ответственный"

            val date = dateFormat.format(selectedDateCalendar.time)
            val groupLabel = currentUser?.groupName?.takeIf { it.isNotBlank() } ?: "Группа"

            val pdf = StatisticsExporter.createNutritionPdfDocument(
                groupNameOrFilter = groupLabel,
                selectedDate = date,
                subscribed = loadedSubscriptions,
                eligibleTotal = loadedEligibleTotal,
                responsibleName = responsible
            )

            CoroutineScope(Dispatchers.IO).launch {
                val success = StatisticsExporter.savePdfToUri(requireContext(), pdf, uri)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportButton.isEnabled = true
                    if (success) {
                        Snackbar.make(requireView(), "PDF сохранен", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(requireView(), "Ошибка сохранения PDF", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            exportButton.isEnabled = true
            Snackbar.make(requireView(), "Ошибка экспорта PDF: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
