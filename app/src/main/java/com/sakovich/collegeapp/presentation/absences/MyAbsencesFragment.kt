package com.sakovich.collegeapp.presentation.absences

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.getAbsenceReasonDisplayName
import com.sakovich.collegeapp.data.repositories.AbsenceRepository
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.utils.AbsenceFormat
import com.sakovich.collegeapp.utils.SemesterFilterHelper
import com.sakovich.collegeapp.utils.DrawableUtils
import com.sakovich.collegeapp.utils.StatisticsExporter
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.button.MaterialButton

class MyAbsencesFragment : Fragment() {

    private lateinit var absencesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var exportButton: Button
    private lateinit var myAbsencesBackButton: ImageButton

    private lateinit var totalHoursValue: TextView
    private lateinit var unexcusedHoursValue: TextView
    private lateinit var excusedHoursValue: TextView

    private lateinit var semesterFilterLayout: TextInputLayout
    private lateinit var semesterFilterDropdown: AutoCompleteTextView
    private lateinit var typeFilterLayout: TextInputLayout
    private lateinit var typeFilterDropdown: AutoCompleteTextView
    private lateinit var subjectFilterLayout: TextInputLayout
    private lateinit var subjectFilterDropdown: AutoCompleteTextView
    private lateinit var dateFromButton: MaterialButton
    private lateinit var dateToButton: MaterialButton
    private lateinit var clearDatesButton: MaterialButton

    private lateinit var auth: FirebaseAuth
    private lateinit var absenceRepository: AbsenceRepository
    private lateinit var userRepository: UserRepository
    private lateinit var adminRepository: AdminRepository
    private lateinit var absencesAdapter: AbsencesAdapter

    private val absencesList = mutableListOf<Absence>()
    private var currentTypeFilter: String? = null
    private var currentSubjectFilter: String? = null
    private var dateFrom: Long? = null
    private var dateTo: Long? = null
    private var currentSemesterFilter: Int? = null
    private var semesterDateRange: Pair<Date, Date>? = null
    private val absenceDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var totalHours = 0
    private var excusedHours = 0
    private var unexcusedHours = 0

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                savePdfToUri(uri)
            }
        } else {
            progressBar.visibility = View.GONE
            exportButton.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_my_absences, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        absenceRepository = AbsenceRepository()
        userRepository = UserRepository()
        adminRepository = AdminRepository()

        initViews(view)
        setupRecyclerView()
        setupSemesterDropdown()
        setupFilters()
        loadAbsences()
    }

    override fun onResume() {
        super.onResume()
        loadAbsences()
    }

    private fun initViews(view: View) {
        absencesRecyclerView = view.findViewById(R.id.absencesRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        countText = view.findViewById(R.id.countText)
        exportButton = view.findViewById(R.id.exportButton)
        myAbsencesBackButton = view.findViewById(R.id.myAbsencesBackButton)

        totalHoursValue = view.findViewById(R.id.totalHoursValue)
        unexcusedHoursValue = view.findViewById(R.id.unexcusedHoursValue)
        excusedHoursValue = view.findViewById(R.id.excusedHoursValue)

        semesterFilterLayout = view.findViewById(R.id.semesterFilterLayout)
        semesterFilterDropdown = view.findViewById(R.id.semesterFilterDropdown)
        typeFilterLayout = view.findViewById(R.id.typeFilterLayout)
        typeFilterDropdown = view.findViewById(R.id.typeFilterDropdown)
        subjectFilterLayout = view.findViewById(R.id.subjectFilterLayout)
        subjectFilterDropdown = view.findViewById(R.id.subjectFilterDropdown)
        dateFromButton = view.findViewById(R.id.dateFromButton)
        dateToButton = view.findViewById(R.id.dateToButton)
        clearDatesButton = view.findViewById(R.id.clearDatesButton)

        exportButton.setOnClickListener {
            exportToPdf()
        }

        myAbsencesBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private val typeFilterOptions = listOf(
        "Все",
        "❌ Неуважительные",
        "✅ Уважительные"
    )

    private fun setupSemesterDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            SemesterFilterHelper.dropdownOptions
        )
        semesterFilterDropdown.setAdapter(adapter)
        semesterFilterDropdown.setText(SemesterFilterHelper.dropdownOptions.first(), false)
        semesterFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentSemesterFilter = SemesterFilterHelper.semesterNumberFromPosition(position)
            semesterFilterDropdown.setText(SemesterFilterHelper.dropdownOptions[position], false)
            loadSemesterDateRange()
        }
    }

    private fun loadSemesterDateRange() {
        val semester = currentSemesterFilter
        if (semester == null) {
            semesterDateRange = null
            filterAbsences()
            return
        }
        val userId = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = userRepository.getUser(userId)
                val groupId = user?.groupId?.takeIf { it.isNotBlank() }
                    ?: user?.groupName?.takeIf { it.isNotBlank() }?.let { GroupRepository.groupNameToDocumentId(it) }
                    ?: ""
                val range = if (groupId.isNotBlank()) {
                    SemesterFilterHelper.dateRangeForSemester(adminRepository, groupId, semester)
                } else null
                withContext(Dispatchers.Main) {
                    semesterDateRange = range
                    filterAbsences()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    semesterDateRange = null
                    filterAbsences()
                }
            }
        }
    }

    private fun setupFilters() {

        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            typeFilterOptions
        )
        typeFilterDropdown.setAdapter(typeAdapter)

        typeFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentTypeFilter = when (position) {
                1 -> "unexcused"
                2 -> "excused"
                else -> null
            }
            typeFilterDropdown.setText(typeFilterOptions[position], false)
            filterAbsences()
        }

        subjectFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            val subjects = getUniqueSubjects()
            currentSubjectFilter = if (position == 0) null else subjects[position - 1]
            subjectFilterDropdown.setText(
                if (position == 0) "Все" else subjects[position - 1],
                false
            )
            filterAbsences()
        }

        dateFromButton.setOnClickListener { showDatePicker(true) }
        dateToButton.setOnClickListener { showDatePicker(false) }
        clearDatesButton.setOnClickListener {
            dateFrom = null
            dateTo = null
            dateFromButton.text = "С даты"
            dateToButton.text = "По дату"
            clearDatesButton.visibility = View.GONE
            filterAbsences()
        }
    }

    private fun showDatePicker(isFromDate: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dateStr = String.format(Locale.getDefault(), "%02d.%02d.%04d", dayOfMonth, month + 1, year)
                if (isFromDate) {
                    dateFrom = selectedCal.timeInMillis
                    dateFromButton.text = dateStr
                } else {
                    dateTo = selectedCal.timeInMillis
                    dateToButton.text = dateStr
                }
                clearDatesButton.visibility = View.VISIBLE
                filterAbsences()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateSubjectFilter() {
        val subjects = mutableListOf("Все")
        subjects.addAll(getUniqueSubjects())

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            subjects
        )
        subjectFilterDropdown.setAdapter(adapter)
    }

    private fun getUniqueSubjects(): List<String> {
        return absencesList.map { it.subject }.distinct().sorted()
    }

    private fun setupRecyclerView() {
        absencesAdapter = AbsencesAdapter(
            absences = absencesList,
            showStudentName = false,
            canManage = false,
            onAbsenceClick = { absence -> showAbsenceDetails(absence) },
            onAbsenceLongClick = null
        )
        absencesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        absencesRecyclerView.isNestedScrollingEnabled = false
        absencesRecyclerView.adapter = absencesAdapter
    }

    private fun loadAbsences() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState("Войдите в аккаунт")
            return
        }

        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val absences = AbsenceFormat.enrichAbsences(
                    absenceRepository.getStudentAbsences(currentUser.uid),
                    userRepository
                )
                val stats = absenceRepository.getStudentAbsenceStats(currentUser.uid)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    absencesList.clear()
                    absencesList.addAll(absences)

                    totalHours = stats.totalHours
                    excusedHours = stats.excusedHours
                    unexcusedHours = stats.unexcusedHours

                    updateStatistics(stats.totalHours, stats.unexcusedHours, stats.excusedHours)
                    updateSubjectFilter()
                    filterAbsences()

                    exportButton.visibility = if (absences.isNotEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    showEmptyState("Ошибка загрузки")
                }
            }
        }
    }

    private fun filterAbsences() {
        var filtered = absencesList.toList()

        when (currentTypeFilter) {
            "excused" -> filtered = filtered.filter { it.isExcused }
            "unexcused" -> filtered = filtered.filter { !it.isExcused }
        }

        if (currentSubjectFilter != null) {
            filtered = filtered.filter { it.subject == currentSubjectFilter }
        }

        filtered = filtered.filter { absence ->
            SemesterFilterHelper.matchesSemesterFilter(absence.date, semesterDateRange)
        }

        filtered = filtered.filter { absence ->
            val absenceTime = parseAbsenceDate(absence.date)
            val fromOk = dateFrom?.let { absenceTime >= it } ?: true
            val toOk = dateTo?.let { absenceTime <= it + 86400000 } ?: true
            fromOk && toOk
        }

        absencesAdapter.updateAbsences(filtered)

        val filteredTotal = filtered.sumOf { it.hours }
        val filteredExcused = filtered.filter { it.isExcused }.sumOf { it.hours }
        val filteredUnexcused = filtered.filter { !it.isExcused }.sumOf { it.hours }
        updateStatistics(filteredTotal, filteredUnexcused, filteredExcused)

        val typeText = when (currentTypeFilter) {
            "excused" -> " (уваж.)"
            "unexcused" -> " (н/у)"
            else -> ""
        }
        val subjectText = if (currentSubjectFilter != null) " - $currentSubjectFilter" else ""
        countText.text = "Пропусков$typeText$subjectText: ${filtered.size}"

        if (filtered.isEmpty()) {
            if (absencesList.isEmpty()) {
                showEmptyState("У вас нет пропусков!")
            } else {
                emptyStateLayout.visibility = View.VISIBLE
                emptyText.text = "Нет пропусков по фильтру"
            }
        } else {
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun parseAbsenceDate(dateStr: String): Long {
        return try {
            absenceDateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun updateStatistics(total: Int, unexcused: Int, excused: Int) {
        totalHoursValue.text = total.toString()
        unexcusedHoursValue.text = unexcused.toString()
        excusedHoursValue.text = excused.toString()
    }

    private fun showAbsenceDetails(absence: Absence) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_absence_details, null)

        view.findViewById<TextView>(R.id.detailSubject).text = absence.subject
        val statusBadge = view.findViewById<TextView>(R.id.detailStatusBadge)
        statusBadge.text = if (absence.isExcused) "✅ Уважительная" else "❌ Неуважительная"
        DrawableUtils.setViewBackgroundColor(
            statusBadge,
            ContextCompat.getColor(
                requireContext(),
                if (absence.isExcused) R.color.grade_excellent else R.color.grade_fail
            )
        )

        view.findViewById<TextView>(R.id.detailDate).text = absence.date
        view.findViewById<TextView>(R.id.detailHours).text = "${absence.hours} ч."
        view.findViewById<TextView>(R.id.detailReason).text = getAbsenceReasonDisplayName(absence.reason)
        view.findViewById<TextView>(R.id.detailCreatedBy).text =
            AbsenceFormat.creatorLine(absence.createdByRole, absence.createdByName)

        val commentBlock = view.findViewById<View>(R.id.detailCommentBlock)
        val commentText = view.findViewById<TextView>(R.id.detailComment)
        if (absence.comment.isNotBlank()) {
            commentBlock.visibility = View.VISIBLE
            commentText.text = absence.comment
        } else {
            commentBlock.visibility = View.GONE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showEmptyState(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        emptyText.text = message
    }

    private fun exportToPdf() {
        val filteredAbsences = absencesAdapter.getCurrentAbsences()
        if (filteredAbsences.isEmpty()) {
            Toast.makeText(requireContext(), "Нет пропусков для экспорта", Toast.LENGTH_SHORT).show()
            return
        }

        val title = "Мои пропуски"
        val filteredTotalHours = filteredAbsences.sumOf { it.hours }
        val filteredExcusedHours = filteredAbsences.filter { it.isExcused }.sumOf { it.hours }
        val filteredUnexcusedHours = filteredAbsences.filter { !it.isExcused }.sumOf { it.hours }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Мои_пропуски_${dateFormat.format(Date())}.pdf"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            savePdfLauncher.launch(intent)

            pendingPdfData = AbsencePdfData(title, filteredAbsences, false, filteredTotalHours, filteredExcusedHours, filteredUnexcusedHours)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private data class AbsencePdfData(
        val title: String,
        val absences: List<Absence>,
        val showStudentName: Boolean,
        val totalHours: Int?,
        val excusedHours: Int?,
        val unexcusedHours: Int?
    )

    private var pendingPdfData: AbsencePdfData? = null

    private fun savePdfToUri(uri: Uri) {
        val data = pendingPdfData ?: return
        pendingPdfData = null

        progressBar.visibility = View.VISIBLE
        exportButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfDocument = StatisticsExporter.createAbsencesPdfDocument(
                    title = data.title,
                    absences = data.absences,
                    showStudentName = data.showStudentName,
                    totalHours = data.totalHours,
                    excusedHours = data.excusedHours,
                    unexcusedHours = data.unexcusedHours
                )

                val success = StatisticsExporter.savePdfToUri(requireContext(), pdfDocument, uri)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportButton.isEnabled = true

                    if (success) {
                        Toast.makeText(requireContext(), "✅ PDF отчёт успешно сохранён!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "❌ Ошибка сохранения PDF отчёта", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportButton.isEnabled = true
                    Toast.makeText(requireContext(), "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
