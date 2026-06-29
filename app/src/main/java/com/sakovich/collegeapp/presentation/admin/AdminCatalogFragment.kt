package com.sakovich.collegeapp.presentation.admin

import android.app.DatePickerDialog
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.CatalogTeacher
import com.sakovich.collegeapp.data.models.GroupLimits
import com.sakovich.collegeapp.data.models.SubjectForGroup
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.AdminRepository
import com.sakovich.collegeapp.data.repositories.GroupRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminCatalogFragment : Fragment() {

    private val adminRepository = AdminRepository()
    private val userRepository = UserRepository()
    private lateinit var auth: FirebaseAuth

    private lateinit var progressBar: ProgressBar
    private lateinit var groupInput: TextInputEditText
    private lateinit var subjectGroupDropdown: AutoCompleteTextView
    private lateinit var subjectInput: TextInputEditText
    private lateinit var subjectAssignDropdown: AutoCompleteTextView
    private lateinit var semesterAssignDropdown: AutoCompleteTextView
    private lateinit var semesterGroupDropdown: AutoCompleteTextView
    private lateinit var semesterNameInput: TextInputEditText
    private lateinit var startDateInput: TextInputEditText
    private lateinit var endDateInput: TextInputEditText
    private lateinit var groupLimitTeacherInput: TextInputEditText
    private lateinit var groupLimitStudentInput: TextInputEditText
    private lateinit var groupLimitHeadmanInput: TextInputEditText
    private lateinit var setGroupLimitsButton: MaterialButton
    private lateinit var subjectsContainer: LinearLayout
    private lateinit var semestersContainer: LinearLayout
    private lateinit var teacherDirectoryNameInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var teacherSelectDropdown: AutoCompleteTextView
    private lateinit var teacherSubjectDropdown: AutoCompleteTextView
    private lateinit var teacherBindingsContainer: LinearLayout
    private lateinit var teachersContainer: LinearLayout
    private lateinit var badgeSubjectsCount: TextView
    private lateinit var badgeSemestersCount: TextView
    private lateinit var badgeTeachersCount: TextView
    private lateinit var badgeBindingsCount: TextView

    private var catalogGroups: List<String> = emptyList()
    private var catalogSubjects: List<SubjectForGroup> = emptyList()
    private var catalogSemesters: List<AdminSemesterTemplate> = emptyList()
    private var catalogTeachers: List<CatalogTeacher> = emptyList()

    private var rawCatalogTeachers: List<CatalogTeacher> = emptyList()
    private var currentAdmin: User? = null
    private var selectedTeacherId: String = ""
    private var curatorGroupName: String = ""
    private var curatorGroupId: String = ""

    private suspend fun withMainUi(block: () -> Unit) {
        withContext(Dispatchers.Main) {
            if (!isAdded || view == null) return@withContext
            block()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_catalog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth

        view.findViewById<ImageButton>(R.id.catalogBackButton).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        progressBar = view.findViewById(R.id.progressBar)
        groupInput = view.findViewById(R.id.groupInput)
        subjectGroupDropdown = view.findViewById(R.id.subjectGroupDropdown)
        subjectInput = view.findViewById(R.id.subjectInput)
        subjectAssignDropdown = view.findViewById(R.id.subjectAssignDropdown)
        semesterAssignDropdown = view.findViewById(R.id.semesterAssignDropdown)
        semesterGroupDropdown = view.findViewById(R.id.semesterGroupDropdown)
        semesterNameInput = view.findViewById(R.id.semesterNameInput)
        startDateInput = view.findViewById(R.id.startDateInput)
        endDateInput = view.findViewById(R.id.endDateInput)
        groupLimitTeacherInput = view.findViewById(R.id.groupLimitTeacherInput)
        groupLimitStudentInput = view.findViewById(R.id.groupLimitStudentInput)
        groupLimitHeadmanInput = view.findViewById(R.id.groupLimitHeadmanInput)
        setGroupLimitsButton = view.findViewById(R.id.setGroupLimitsButton)
        setGroupLimitsButton.setOnClickListener { applyGroupLimitsFromForm() }
        subjectsContainer = view.findViewById(R.id.subjectsContainer)
        semestersContainer = view.findViewById(R.id.semestersContainer)
        teacherDirectoryNameInput = view.findViewById(R.id.teacherDirectoryNameInput)
        teacherSelectDropdown = view.findViewById(R.id.teacherSelectDropdown)
        teacherSubjectDropdown = view.findViewById(R.id.teacherSubjectDropdown)
        teacherBindingsContainer = view.findViewById(R.id.teacherBindingsContainer)
        teachersContainer = view.findViewById(R.id.teachersContainer)
        badgeSubjectsCount = view.findViewById(R.id.badgeSubjectsCount)
        badgeSemestersCount = view.findViewById(R.id.badgeSemestersCount)
        badgeTeachersCount = view.findViewById(R.id.badgeTeachersCount)
        badgeBindingsCount = view.findViewById(R.id.badgeBindingsCount)

        view.findViewById<MaterialButton>(R.id.addGroupButton).setOnClickListener { addGroup() }
        view.findViewById<MaterialButton>(R.id.addSubjectButton).setOnClickListener { addSubject() }
        view.findViewById<MaterialButton>(R.id.assignSubjectSemesterButton).setOnClickListener { assignSubjectSemester() }
        view.findViewById<MaterialButton>(R.id.unassignSubjectSemesterButton).setOnClickListener { unassignSubjectSemester() }
        view.findViewById<MaterialButton>(R.id.addSemesterButton).setOnClickListener { addSemester() }
        view.findViewById<MaterialButton>(R.id.addTeacherBindingButton).setOnClickListener { addTeacherBinding() }
        view.findViewById<MaterialButton>(R.id.addTeacherButton).setOnClickListener { addTeacher() }

        attachDatePicker(startDateInput)
        attachDatePicker(endDateInput)

        verifyAccessAndLoad()
    }

    private fun verifyAccessAndLoad() {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val uid = auth.currentUser?.uid
            val admin = if (uid.isNullOrBlank()) null else userRepository.getUser(uid)
            val hasAccess = admin?.role == "teacher" || admin?.role == "admin"
            if (!hasAccess) {
                withMainUi {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Нет доступа", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
                return@launch
            }
            currentAdmin = admin
            curatorGroupName = admin?.groupName?.ifBlank { admin.group }?.trim().orEmpty()
            curatorGroupId = GroupRepository.effectiveGroupIdForUser(
                admin?.groupName.orEmpty(),
                admin?.groupId.orEmpty(),
                admin?.group.orEmpty()
            )
            if (curatorGroupName.isBlank() || curatorGroupId.isBlank()) {
                withMainUi {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Для куратора не задана группа", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
                return@launch
            }
            withMainUi { refreshCatalog() }
        }
    }

    private fun refreshCatalog() {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val groups = adminRepository.getCatalogGroups()
            val subjects = adminRepository.getSubjects()
            val semesters = adminRepository.getSemesters()
            val teachers = adminRepository.getCatalogTeachers()
            withMainUi {
                progressBar.visibility = View.GONE
                val ownGroupCandidates = groups.filter { g ->
                    GroupRepository.groupNameToDocumentId(g) == curatorGroupId
                }
                catalogGroups = if (ownGroupCandidates.isNotEmpty()) ownGroupCandidates else listOf(curatorGroupName)
                catalogSubjects = subjects.filter { it.groupId == curatorGroupId }
                val ownSemesters = semesters.filter { it.groupId == curatorGroupId }
                catalogSemesters = ownSemesters
                val ownSubjectIds = catalogSubjects
                    .map { adminRepository.getSubjectDocumentId(it.name, it.groupId) }
                    .toSet()
                val ownTeachersRaw = teachers.filter { teacher ->

                    teacher.subjectIds.isEmpty() || teacher.subjectIds.any { it in ownSubjectIds }
                }
                rawCatalogTeachers = ownTeachersRaw
                catalogTeachers = mergeTeachersByName(ownTeachersRaw)
                setupGroupDropdowns()
                setupSubjectSemesterDropdowns()
                setupTeacherDropdowns(catalogTeachers)
                loadGroupLimitsIntoForm()
                renderSubjectItems(catalogSubjects)
                renderSemesterItems(ownSemesters)
                renderTeacherItems(catalogTeachers)

                groupInput.isEnabled = false
                groupInput.setText(curatorGroupName)
                view?.findViewById<MaterialButton>(R.id.addGroupButton)?.isEnabled = false
                view?.findViewById<MaterialButton>(R.id.addGroupButton)?.alpha = 0.5f

                subjectGroupDropdown.setText(curatorGroupName, false)
                subjectGroupDropdown.isEnabled = false
                semesterGroupDropdown.setText(curatorGroupName, false)
                semesterGroupDropdown.isEnabled = false

                updateCatalogSummaryUi(ownSemesters.size)
            }
        }
    }

    private fun updateCatalogSummaryUi(semesterCount: Int) {
        badgeSubjectsCount.text = catalogSubjects.size.toString()
        badgeSemestersCount.text = semesterCount.toString()
        badgeTeachersCount.text = catalogTeachers.size.toString()
        val bindingTotal = catalogTeachers.sumOf { it.subjectIds.size }
        badgeBindingsCount.text = bindingTotal.toString()
    }

    private fun setupSubjectSemesterDropdowns() {
        val subjectNames = catalogSubjects.map { it.name }.distinct().sorted()
        val subjectAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            subjectNames
        )
        subjectAssignDropdown.setAdapter(subjectAdapter)
        subjectAssignDropdown.setOnClickListener { subjectAssignDropdown.showDropDown() }
        subjectAssignDropdown.setOnItemClickListener { _, _, _, _ ->
            refreshSemesterAssignDropdown(forUnassign = false)
        }

        refreshSemesterAssignDropdown(forUnassign = false)
    }

    private fun refreshSemesterAssignDropdown(forUnassign: Boolean) {
        val subjectName = subjectAssignDropdown.text?.toString()?.trim().orEmpty()
        val subject = catalogSubjects.firstOrNull { it.name == subjectName }
        val semesterLabels = if (forUnassign) {
            subject?.semesterNames?.mapNotNull { name ->
                catalogSemesters.firstOrNull { it.name == name }?.let { semesterLabel(it) }
            }.orEmpty().sorted()
        } else {
            catalogSemesters.map { semesterLabel(it) }.sorted()
        }
        val semesterAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            semesterLabels
        )
        semesterAssignDropdown.setAdapter(semesterAdapter)
        semesterAssignDropdown.setOnClickListener {
            refreshSemesterAssignDropdown(forUnassign = false)
            semesterAssignDropdown.showDropDown()
        }
        if (semesterLabels.isNotEmpty()) {
            semesterAssignDropdown.setText(semesterLabels.first(), false)
        } else {
            semesterAssignDropdown.setText("", false)
        }
    }

    private fun semesterLabel(semester: AdminSemesterTemplate): String {
        return if (semester.startDate.isNotBlank() && semester.endDate.isNotBlank()) {
            "${semester.name} (${semester.startDate} — ${semester.endDate})"
        } else {
            semester.name
        }
    }

    private fun semesterFromDropdownLabel(label: String): AdminSemesterTemplate? {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return null
        return catalogSemesters.firstOrNull { semesterLabel(it) == trimmed }
            ?: catalogSemesters.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    }

    private fun unassignSubjectSemester() {
        refreshSemesterAssignDropdown(forUnassign = true)
        val subjectName = subjectAssignDropdown.text?.toString()?.trim().orEmpty()
        val semesterLabelText = semesterAssignDropdown.text?.toString()?.trim().orEmpty()
        if (subjectName.isBlank()) {
            Toast.makeText(requireContext(), "Выберите предмет", Toast.LENGTH_SHORT).show()
            return
        }
        if (semesterLabelText.isBlank()) {
            Toast.makeText(requireContext(), "Выберите семестр для отвязки", Toast.LENGTH_SHORT).show()
            return
        }
        val subject = catalogSubjects.firstOrNull { it.name == subjectName } ?: run {
            Toast.makeText(requireContext(), "Предмет не найден", Toast.LENGTH_SHORT).show()
            return
        }
        val semester = semesterFromDropdownLabel(semesterLabelText) ?: run {
            Toast.makeText(requireContext(), "Семестр не найден", Toast.LENGTH_SHORT).show()
            return
        }
        if (!subject.hasSemester(semester.id)) {
            Toast.makeText(requireContext(), "Этот семестр не привязан к предмету", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = adminRepository.unassignSubjectSemester(subject.name, subject.groupId, semester.id)
            withMainUi {
                progressBar.visibility = View.GONE
                when (result) {
                    AdminRepository.AssignSubjectSemesterResult.SUCCESS -> {
                        Toast.makeText(requireContext(), "Семестр отвязан", Toast.LENGTH_SHORT).show()
                        refreshCatalog()
                    }
                    AdminRepository.AssignSubjectSemesterResult.SUBJECT_NOT_FOUND -> {
                        Toast.makeText(requireContext(), "Предмет не найден", Toast.LENGTH_SHORT).show()
                    }
                    else -> Toast.makeText(requireContext(), "Не удалось отвязать семестр", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun assignSubjectSemester() {
        val subjectName = subjectAssignDropdown.text?.toString()?.trim().orEmpty()
        val semesterLabel = semesterAssignDropdown.text?.toString()?.trim().orEmpty()
        if (subjectName.isBlank()) {
            Toast.makeText(requireContext(), "Выберите предмет", Toast.LENGTH_SHORT).show()
            return
        }
        if (semesterLabel.isBlank()) {
            Toast.makeText(requireContext(), "Выберите семестр", Toast.LENGTH_SHORT).show()
            return
        }
        val subject = catalogSubjects.firstOrNull { it.name == subjectName } ?: run {
            Toast.makeText(requireContext(), "Предмет не найден", Toast.LENGTH_SHORT).show()
            return
        }
        val semester = semesterFromDropdownLabel(semesterLabel) ?: run {
            Toast.makeText(requireContext(), "Семестр не найден", Toast.LENGTH_SHORT).show()
            return
        }
        if (subject.hasSemester(semester.id)) {
            Toast.makeText(requireContext(), "Этот семестр уже назначен предмету", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = adminRepository.assignSubjectSemester(subject.name, subject.groupId, semester)
            withMainUi {
                progressBar.visibility = View.GONE
                when (result) {
                    AdminRepository.AssignSubjectSemesterResult.SUCCESS -> {
                        Toast.makeText(requireContext(), "Семестр назначен", Toast.LENGTH_SHORT).show()
                        refreshCatalog()
                    }
                    AdminRepository.AssignSubjectSemesterResult.DUPLICATE -> {
                        Toast.makeText(
                            requireContext(),
                            "Этот семестр уже назначен предмету",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    AdminRepository.AssignSubjectSemesterResult.SUBJECT_NOT_FOUND -> {
                        Toast.makeText(requireContext(), "Предмет не найден", Toast.LENGTH_SHORT).show()
                    }
                    AdminRepository.AssignSubjectSemesterResult.ERROR -> {
                        Toast.makeText(requireContext(), "Не удалось назначить семестр", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupTeacherDropdowns(allTeachers: List<CatalogTeacher>) {
        val subjectAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            catalogSubjects.map { it.name }.distinct().sorted()
        )
        teacherSubjectDropdown.setAdapter(subjectAdapter)
        teacherSubjectDropdown.setOnClickListener { teacherSubjectDropdown.showDropDown() }

        val teacherNames = allTeachers.map { it.fullName }.distinct().sorted()
        val teacherAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            teacherNames
        )
        teacherSelectDropdown.setAdapter(teacherAdapter)
        teacherSelectDropdown.setOnClickListener { teacherSelectDropdown.showDropDown() }
        val selectedExists = allTeachers.any { it.id == selectedTeacherId }
        if (!selectedExists) {
            selectedTeacherId = allTeachers.firstOrNull()?.id.orEmpty()
        }
        val selectedTeacherName = allTeachers.firstOrNull { it.id == selectedTeacherId }?.fullName.orEmpty()
        teacherSelectDropdown.setText(selectedTeacherName, false)
        teacherSelectDropdown.setOnItemClickListener { _, _, _, _ ->
            val name = teacherSelectDropdown.text?.toString()?.trim().orEmpty()
            val teacher = allTeachers.firstOrNull { it.fullName == name }
            selectedTeacherId = teacher?.id.orEmpty()
            renderTeacherBindingsForSelected(allTeachers)
        }
    }

    private fun mergeTeachersByName(source: List<CatalogTeacher>): List<CatalogTeacher> {
        if (source.isEmpty()) return emptyList()
        return source
            .groupBy { it.fullName.trim().lowercase(Locale.getDefault()) }
            .values
            .map { group ->
                val sorted = group.sortedBy { it.id }
                val base = sorted.first()
                val mergedSubjectIds = sorted.flatMap { it.subjectIds }.distinct()
                base.copy(subjectIds = mergedSubjectIds)
            }
            .sortedBy { it.fullName.lowercase(Locale.getDefault()) }
    }

    private fun setupGroupDropdowns() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            catalogGroups
        )
        subjectGroupDropdown.setAdapter(adapter)
        semesterGroupDropdown.setAdapter(adapter)
    }

    private fun addGroup() {
        Toast.makeText(requireContext(), "Куратор может администрировать только свою группу", Toast.LENGTH_SHORT).show()
    }

    private fun addSubject() {
        val name = subjectInput.text?.toString()?.trim().orEmpty()
        val groupName = curatorGroupName
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "Введите название предмета", Toast.LENGTH_SHORT).show()
            return
        }
        if (groupName.isBlank()) {
            Toast.makeText(requireContext(), "Выберите группу", Toast.LENGTH_SHORT).show()
            return
        }
        val groupId = GroupRepository.groupNameToDocumentId(groupName)
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.addSubject(name, groupId, groupName)
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    subjectInput.setText("")
                    refreshCatalog()
                } else {
                    Toast.makeText(requireContext(), "Не удалось добавить предмет", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addSemester() {
        val semesterNumberRaw = semesterNameInput.text?.toString()?.trim().orEmpty()
        val start = startDateInput.text?.toString()?.trim().orEmpty()
        val end = endDateInput.text?.toString()?.trim().orEmpty()
        val groupName = curatorGroupName
        if (semesterNumberRaw.isBlank() || start.isBlank() || end.isBlank()) {
            Toast.makeText(requireContext(), "Заполните все поля семестра", Toast.LENGTH_SHORT).show()
            return
        }
        val semesterNumber = semesterNumberRaw.toIntOrNull()
        if (semesterNumber == null || semesterNumber <= 0) {
            Toast.makeText(requireContext(), "Введите корректный номер семестра", Toast.LENGTH_SHORT).show()
            return
        }
        if (groupName.isBlank()) {
            Toast.makeText(requireContext(), "Выберите группу", Toast.LENGTH_SHORT).show()
            return
        }
        val groupId = GroupRepository.groupNameToDocumentId(groupName)
        val semesterName = "$semesterNumber семестр"
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.addSemester(
                AdminSemesterTemplate(name = semesterName, startDate = start, endDate = end),
                groupId,
                groupName
            )
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    semesterNameInput.setText("")
                    startDateInput.setText("")
                    endDateInput.setText("")
                    refreshCatalog()
                } else {
                    Toast.makeText(requireContext(), "Не удалось добавить семестр", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadGroupLimitsIntoForm() {
        val groupId = curatorGroupId
        if (groupId.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val limits = adminRepository.getGroupLimits(groupId)
            withMainUi {
                groupLimitTeacherInput.setText(limits?.teacherLimit?.toString() ?: "")
                groupLimitStudentInput.setText(limits?.studentLimit?.toString() ?: "")
                groupLimitHeadmanInput.setText(limits?.headmanLimit?.toString() ?: "")
            }
        }
    }

    private fun applyGroupLimitsFromForm() {
        val groupId = curatorGroupId
        if (groupId.isBlank()) {
            Toast.makeText(requireContext(), "Группа не задана", Toast.LENGTH_SHORT).show()
            return
        }
        val teacherLimit = groupLimitTeacherInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val studentLimit = groupLimitStudentInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val headmanLimit = groupLimitHeadmanInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull()

        if (listOf(teacherLimit, studentLimit, headmanLimit).any { it != null && it < 0 }) {
            Toast.makeText(requireContext(), "Лимиты не могут быть отрицательными", Toast.LENGTH_SHORT).show()
            return
        }

        val limitsToSave = if (teacherLimit == null && studentLimit == null && headmanLimit == null) {
            null
        } else {
            GroupLimits(
                teacherLimit = teacherLimit,
                studentLimit = studentLimit,
                headmanLimit = headmanLimit
            )
        }

        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.setGroupLimits(groupId, limitsToSave)
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    Toast.makeText(requireContext(), "Лимит установлен", Toast.LENGTH_SHORT).show()
                    loadGroupLimitsIntoForm()
                } else {
                    Toast.makeText(requireContext(), "Не удалось сохранить лимиты", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderSubjectItems(subjects: List<SubjectForGroup>) {
        subjectsContainer.removeAllViews()
        if (subjects.isEmpty()) {
            subjectsContainer.addView(buildRowText("Пока нет предметов — добавьте предмет выше", R.color.text_muted))
            return
        }
        subjects.forEach { s ->
            val semesterInfo = s.semestersDisplayText()
            subjectsContainer.addView(buildSubjectRow(
                title = s.name,
                subtitle = semesterInfo,
                onEdit = { editSubject(s) },
                onDelete = { confirmRemoveSubject(s.name, s.groupId, s.groupName) }
            ))
        }
    }

    private fun editSubject(subject: SubjectForGroup) {
        val input = TextInputEditText(requireContext()).apply {
            setText(subject.name)
            setSelection(subject.name.length)
            hint = "Название предмета"
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать предмет")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "Введите название", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == subject.name) return@setPositiveButton
                progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ok = adminRepository.updateSubject(subject.name, subject.groupId, newName)
                    withMainUi {
                        progressBar.visibility = View.GONE
                        if (ok) refreshCatalog()
                        else Toast.makeText(requireContext(), "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renderSemesterItems(semesters: List<AdminSemesterTemplate>) {
        semestersContainer.removeAllViews()
        if (semesters.isEmpty()) {
            semestersContainer.addView(buildRowText("Семестры не заведены — заполните форму выше", R.color.text_muted))
            return
        }
        semesters.forEach { semester ->
            semestersContainer.addView(
                buildSemesterRow(
                    semester = semester,
                    onEdit = { editSemester(semester) },
                    onDelete = { confirmRemoveSemester(semester) }
                )
            )
        }
    }

    private fun semesterNumberFromName(name: String): Int? =
        Regex("(\\d+)").find(name.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun editSemester(semester: AdminSemesterTemplate) {
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        val numberInput = TextInputEditText(requireContext()).apply {
            setText(semesterNumberFromName(semester.name)?.toString().orEmpty())
            hint = "Номер семестра"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        val startInput = TextInputEditText(requireContext()).apply {
            setText(semester.startDate)
            hint = "Дата начала"
            isFocusable = false
            setPadding(48, 32, 48, 32)
        }
        val endInput = TextInputEditText(requireContext()).apply {
            setText(semester.endDate)
            hint = "Дата конца"
            isFocusable = false
            setPadding(48, 32, 48, 32)
        }
        attachDatePicker(startInput)
        attachDatePicker(endInput)

        dialogView.addView(numberInput)
        dialogView.addView(startInput)
        dialogView.addView(endInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать семестр")
            .setMessage("Группа: ${semester.groupName.ifBlank { curatorGroupName }}")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val numberRaw = numberInput.text?.toString()?.trim().orEmpty()
                val start = startInput.text?.toString()?.trim().orEmpty()
                val end = endInput.text?.toString()?.trim().orEmpty()
                if (numberRaw.isBlank() || start.isBlank() || end.isBlank()) {
                    Toast.makeText(requireContext(), "Заполните все поля", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val semesterNumber = numberRaw.toIntOrNull()
                if (semesterNumber == null || semesterNumber <= 0) {
                    Toast.makeText(requireContext(), "Введите корректный номер семестра", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val startDate = try {
                    dateFormat.parse(start)
                } catch (_: Exception) {
                    null
                }
                val endDate = try {
                    dateFormat.parse(end)
                } catch (_: Exception) {
                    null
                }
                if (startDate == null || endDate == null) {
                    Toast.makeText(requireContext(), "Некорректный формат даты", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (startDate.after(endDate)) {
                    Toast.makeText(requireContext(), "Дата начала не может быть позже даты конца", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newName = "$semesterNumber семестр"
                if (newName == semester.name && start == semester.startDate && end == semester.endDate) {
                    return@setPositiveButton
                }
                progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ok = adminRepository.updateSemester(semester, semesterNumber, start, end)
                    withMainUi {
                        progressBar.visibility = View.GONE
                        when {
                            ok -> {
                                Toast.makeText(requireContext(), "Семестр обновлён", Toast.LENGTH_SHORT).show()
                                refreshCatalog()
                            }
                            else -> Toast.makeText(
                                requireContext(),
                                "Не удалось сохранить. Возможно, такой семестр уже есть для группы.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun removeGroup(groupName: String) {
        Toast.makeText(requireContext(), "Удаление групп недоступно для куратора", Toast.LENGTH_SHORT).show()
    }

    private fun confirmRemoveGroup(groupName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить группу?")
            .setMessage("Группа: $groupName")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> removeGroup(groupName) }
            .show()
    }

    private fun removeSubject(subjectName: String, groupId: String) {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.removeSubject(subjectName, groupId)
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) refreshCatalog() else Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRemoveSubject(subjectName: String, groupId: String, groupName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить предмет?")
            .setMessage("Предмет: $subjectName\nГруппа: $groupName")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> removeSubject(subjectName, groupId) }
            .show()
    }

    private fun removeSemester(semesterId: String) {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.removeSemester(semesterId)
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) refreshCatalog() else Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRemoveSemester(semester: AdminSemesterTemplate) {
        val groupPart = semester.groupName.takeIf { it.isNotBlank() } ?: "—"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить семестр?")
            .setMessage("Семестр: ${semester.name}\nГруппа: $groupPart\nПериод: ${semester.startDate} - ${semester.endDate}")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> removeSemester(semester.id) }
            .show()
    }

    private fun addTeacherBinding() {
        val teacherName = teacherSelectDropdown.text?.toString()?.trim().orEmpty()
        val subjectName = teacherSubjectDropdown.text?.toString()?.trim().orEmpty()
        if (teacherName.isBlank()) {
            Toast.makeText(requireContext(), "Выберите преподавателя", Toast.LENGTH_SHORT).show()
            return
        }
        if (subjectName.isBlank()) {
            Toast.makeText(requireContext(), "Выберите предмет", Toast.LENGTH_SHORT).show()
            return
        }
        val teacher = catalogTeachers.firstOrNull { it.fullName == teacherName } ?: run {
            Toast.makeText(requireContext(), "Преподаватель не найден", Toast.LENGTH_SHORT).show()
            return
        }
        val subject = catalogSubjects.firstOrNull { it.name == subjectName } ?: run {
            Toast.makeText(requireContext(), "Предмет не найден", Toast.LENGTH_SHORT).show()
            return
        }
        val subjectId = adminRepository.getSubjectDocumentId(subject.name, subject.groupId)
        if (subjectId in teacher.subjectIds) {
            Toast.makeText(requireContext(), "Эта привязка уже добавлена", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.updateCatalogTeacher(
                id = teacher.id,
                fullName = teacher.fullName,
                subjectIds = (teacher.subjectIds + subjectId).distinct()
            )
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    teacherSubjectDropdown.setText("", false)
                    refreshCatalog()
                    Toast.makeText(requireContext(), "Привязка добавлена", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось добавить привязку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderTeacherBindingsForSelected(teachers: List<CatalogTeacher> = catalogTeachers) {
        teacherBindingsContainer.removeAllViews()
        val subjectIdToLabel = catalogSubjects.associate { adminRepository.getSubjectDocumentId(it.name, it.groupId) to it.name }
        val selected = teachers.firstOrNull { it.id == selectedTeacherId } ?: run {
            teacherBindingsContainer.addView(buildRowText("Выберите преподавателя в списке выше", R.color.text_muted))
            return
        }
        if (selected.subjectIds.isEmpty()) {
            teacherBindingsContainer.addView(buildRowText("У этого преподавателя пока нет назначенных предметов", R.color.text_muted))
            return
        }
        selected.subjectIds.forEach { id ->
            val label = subjectIdToLabel[id] ?: id
            teacherBindingsContainer.addView(buildTeacherBindingRow(label) {
                confirmRemoveTeacherBinding(selected, id, label)
            })
        }
    }

    private fun confirmRemoveTeacherBinding(teacher: CatalogTeacher, subjectId: String, subjectLabel: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Снять назначение предмета?")
            .setMessage("У преподавателя ${teacher.fullName} будет убран предмет «$subjectLabel».")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> removeTeacherBinding(teacher, subjectId) }
            .show()
    }

    private fun removeTeacherBinding(teacher: CatalogTeacher, subjectId: String) {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val updated = teacher.subjectIds.filter { it != subjectId }
            val ok = adminRepository.updateCatalogTeacher(teacher.id, teacher.fullName, updated)
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    refreshCatalog()
                    Toast.makeText(requireContext(), "Привязка удалена", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось удалить привязку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildTeacherBindingRow(label: String, onRemove: () -> Unit): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }
        val textView = TextView(requireContext()).apply {
            this.text = label
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val removeView = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Удалить"
            setTextColor(android.graphics.Color.parseColor("#F87171"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F87171"))
            strokeWidth = dp(1)
            cornerRadius = dp(10)
            minimumHeight = dp(34)
            minHeight = dp(34)
            minimumWidth = dp(84)
            minWidth = dp(84)
            textSize = 12f
            setOnClickListener { onRemove() }
        }
        row.addView(textView)
        row.addView(removeView)
        return row
    }

    private fun addTeacher() {
        val fullName = teacherDirectoryNameInput.text?.toString()?.trim().orEmpty()
        if (fullName.isBlank()) {
            Toast.makeText(requireContext(), "Введите ФИО преподавателя", Toast.LENGTH_SHORT).show()
            return
        }
        if (catalogTeachers.any { it.fullName.equals(fullName, ignoreCase = true) }) {
            Toast.makeText(requireContext(), "Такой преподаватель уже есть", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.addCatalogTeacher(fullName, emptyList())
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    teacherDirectoryNameInput.setText("")
                    refreshCatalog()
                    Toast.makeText(requireContext(), "Преподаватель добавлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось добавить преподавателя", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderTeacherItems(teachers: List<CatalogTeacher>) {
        teachersContainer.removeAllViews()
        if (teachers.isEmpty()) {
            teachersContainer.addView(buildRowText("Справочник пуст — добавьте ФИО преподавателя", R.color.text_muted))
            return
        }
        teachers.forEach { teacher ->
            val label = teacher.fullName
            teachersContainer.addView(buildSubjectRow(
                title = label,
                subtitle = "",
                onEdit = { editTeacherName(teacher) },
                onDelete = { confirmRemoveTeacher(teacher) }
            ))
        }
        renderTeacherBindingsForSelected(teachers)
    }

    private fun editTeacherName(teacher: CatalogTeacher) {
        val oldName = teacher.fullName.trim()
        val input = TextInputEditText(requireContext()).apply {
            setText(teacher.fullName)
            setSelection(teacher.fullName.length)
            hint = "ФИО преподавателя"
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать преподавателя")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "Введите ФИО", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName.equals(oldName, ignoreCase = true)) return@setPositiveButton
                progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val dupes = rawCatalogTeachers.filter { it.fullName.trim().equals(oldName, ignoreCase = true) }
                    val dupeIds = dupes.map { it.id }.toSet()
                    val mergedIds = dupes.flatMap { it.subjectIds }.distinct()
                    val primaryId = dupes.minByOrNull { it.id }?.id ?: teacher.id

                    val conflicting = rawCatalogTeachers.any {
                        it.id !in dupeIds && it.fullName.trim().equals(newName, ignoreCase = true)
                    }
                    if (conflicting) {
                        withMainUi {
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                requireContext(),
                                "Уже есть преподаватель с таким ФИО",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    val ok = adminRepository.updateCatalogTeacher(primaryId, newName, mergedIds)
                    if (!ok) {
                        withMainUi {
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    var deleteFailed = false
                    for (d in dupes) {
                        if (d.id != primaryId && !adminRepository.removeCatalogTeacher(d.id)) {
                            deleteFailed = true
                        }
                    }
                    withMainUi {
                        progressBar.visibility = View.GONE
                        if (deleteFailed) {
                            Toast.makeText(
                                requireContext(),
                                "Имя обновлено; при необходимости удалите оставшиеся дубликаты вручную",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        refreshCatalog()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun removeTeachersMerged(teacher: CatalogTeacher) {
        val dupes = rawCatalogTeachers.filter {
            it.fullName.trim().equals(teacher.fullName.trim(), ignoreCase = true)
        }
        if (dupes.isEmpty()) {
            removeSingleTeacherDocument(teacher.id)
            return
        }
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var allOk = true
            for (d in dupes) {
                if (!adminRepository.removeCatalogTeacher(d.id)) allOk = false
            }
            withMainUi {
                progressBar.visibility = View.GONE
                if (allOk) {
                    selectedTeacherId = ""
                    refreshCatalog()
                } else {
                    Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeSingleTeacherDocument(teacherId: String) {
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = adminRepository.removeCatalogTeacher(teacherId)
            withMainUi {
                progressBar.visibility = View.GONE
                if (ok) {
                    selectedTeacherId = ""
                    refreshCatalog()
                } else Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRemoveTeacher(teacher: CatalogTeacher) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить преподавателя?")
            .setMessage("Преподаватель: ${teacher.fullName}")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> removeTeachersMerged(teacher) }
            .show()
    }

    private fun buildRowText(text: String, colorRes: Int): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            textSize = 13.5f
            letterSpacing = 0.01f
            setPadding(dp(4), dp(14), dp(4), dp(14))
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun buildSemesterRow(
        semester: AdminSemesterTemplate,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_catalog_list_row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        val title = TextView(requireContext()).apply {
            text = semester.name
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(android.graphics.Color.parseColor("#F1F5F9"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val dates = TextView(requireContext()).apply {
            text = "${semester.startDate} — ${semester.endDate}"
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val actionsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val editBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Редактировать"
            setTextColor(android.graphics.Color.parseColor("#C4B5FD"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#8B5CF6"))
            strokeWidth = dp(1)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(10)
            textSize = 12f
            minimumHeight = dp(40)
            minHeight = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onEdit() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val deleteBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Удалить"
            setTextColor(android.graphics.Color.parseColor("#F87171"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F87171"))
            strokeWidth = dp(1)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(10)
            textSize = 12f
            minimumHeight = dp(40)
            minHeight = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onDelete() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        row.addView(title)
        row.addView(dates)
        actionsRow.addView(editBtn)
        actionsRow.addView(deleteBtn)
        row.addView(actionsRow)
        return row
    }

    private fun buildSubjectRow(
        title: String,
        subtitle: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_catalog_list_row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        val titleView = TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_dark))
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val subtitleView = TextView(requireContext()).apply {
            text = subtitle
            visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            textSize = 12.5f
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        val actionsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val editBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = "Редактировать"
            setTextColor(android.graphics.Color.parseColor("#C4B5FD"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#8B5CF6"))
            strokeWidth = dp(1)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(10)
            textSize = 12f
            minimumHeight = dp(40)
            minHeight = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onEdit() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val deleteBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = "Удалить"
            setTextColor(android.graphics.Color.parseColor("#F87171"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F87171"))
            strokeWidth = dp(1)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(10)
            textSize = 12f
            minimumHeight = dp(40)
            minHeight = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onDelete() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        row.addView(titleView)
        row.addView(subtitleView)
        actionsRow.addView(editBtn)
        actionsRow.addView(deleteBtn)
        row.addView(actionsRow)
        return row
    }

    private fun attachDatePicker(target: TextInputEditText) {
        target.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selected = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    target.setText(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selected.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
