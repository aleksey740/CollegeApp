package com.sakovich.collegeapp.presentation.teacher

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.TeacherHourRecord
import com.sakovich.collegeapp.data.models.TeacherHourType
import com.sakovich.collegeapp.data.repositories.ScheduleRepository
import com.sakovich.collegeapp.data.repositories.TeacherHourRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import com.sakovich.collegeapp.presentation.teacher.adapters.TeacherHoursAdapter
import com.sakovich.collegeapp.utils.StatisticsExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TeacherHoursFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var teacherHourRepository: TeacherHourRepository
    private lateinit var scheduleRepository: ScheduleRepository

    private lateinit var upcomingReminderText: TextView
    private lateinit var addRecordButton: MaterialButton
    private lateinit var exportPdfButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var recordsRecyclerView: RecyclerView

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> savePdfToUri(uri) }
        } else {
            progressBar.visibility = View.GONE
            exportPdfButton.isEnabled = true
        }
    }
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: TeacherHoursAdapter

    private var teacherId: String = ""
    private var teacherName: String = ""
    private val records = mutableListOf<TeacherHourRecord>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_teacher_hours, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()
        teacherHourRepository = TeacherHourRepository()
        scheduleRepository = ScheduleRepository()

        upcomingReminderText = view.findViewById(R.id.upcomingReminderText)
        addRecordButton = view.findViewById(R.id.addRecordButton)
        exportPdfButton = view.findViewById(R.id.exportPdfButton)
        exportButton = view.findViewById(R.id.exportButton)
        recordsRecyclerView = view.findViewById(R.id.recordsRecyclerView)
        emptyText = view.findViewById(R.id.emptyText)
        progressBar = view.findViewById(R.id.progressBar)

        adapter = TeacherHoursAdapter(records) { item ->
            showHourMenu(item)
        }
        recordsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        recordsRecyclerView.adapter = adapter

        addRecordButton.setOnClickListener { showAddDialog() }
        exportPdfButton.setOnClickListener { exportToPdf() }
        exportButton.setOnClickListener { exportToCsv() }

        loadCurrentTeacher()
    }

    override fun onResume() {
        super.onResume()
        if (teacherId.isNotEmpty()) {
            loadRecords()
            loadUpcomingReminder()
        }
    }

    private fun loadCurrentTeacher() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Не выполнен вход", Toast.LENGTH_SHORT).show()
            return
        }
        teacherId = user.uid

        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val teacher = userRepository.getUser(user.uid)
                withContext(Dispatchers.Main) {
                    teacherName = teacher?.fullName ?: (user.email ?: "Преподаватель")
                    loadRecords()
                    loadUpcomingReminder()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    teacherName = user.email ?: "Преподаватель"
                    loadRecords()
                    loadUpcomingReminder()
                }
            }
        }
    }

    private fun loadRecords(showProgress: Boolean = true) {
        if (showProgress) progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val list = teacherHourRepository.getTeacherHours(teacherId)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                records.clear()
                records.addAll(list)
                adapter.update(records.toList())
                recordsRecyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun loadUpcomingReminder() {
        CoroutineScope(Dispatchers.IO).launch {
            val schedule = scheduleRepository.getAllSchedule()
            val nextLesson = findNearestLesson(schedule)
            withContext(Dispatchers.Main) {
                upcomingReminderText.text = if (nextLesson == null) {
                    "Ближайшие пары в расписании не найдены."
                } else {
                    buildReminderText(nextLesson)
                }
            }
        }
    }

    private fun findNearestLesson(items: List<ScheduleItem>): ScheduleItem? {
        val now = Date()
        return items
            .filter { item ->
                (item.teacherId == teacherId || (item.teacherId.isBlank() && item.teacherName == teacherName)) &&
                    parseLessonStart(item)?.after(now) == true
            }
            .minByOrNull { parseLessonStart(it) ?: Date(Long.MAX_VALUE) }
    }

    private fun parseLessonStart(item: ScheduleItem): Date? {
        return try {
            val start = item.time.split("-").firstOrNull()?.trim().orEmpty()
            val value = "${item.date} $start"
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildReminderText(item: ScheduleItem): String {
        val start = parseLessonStart(item) ?: return "Ближайшая пара: ${item.date} ${item.time}"
        val diff = start.time - System.currentTimeMillis()
        val hours = diff / (1000 * 60 * 60)
        val mins = (diff / (1000 * 60)) % 60
        val lead = if (hours < 24) "Напоминание: через ${hours}ч ${mins}м.\n" else ""
        return "${lead}Ближайшая пара: ${item.date} ${item.time}\n${item.subject}, группа ${item.group}"
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_teacher_hour, null)
        val typeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.typeRadioGroup)
        val teachingRadioId = R.id.teachingRadio
        val topicEditText = dialogView.findViewById<EditText>(R.id.topicEditText)
        val groupEditText = dialogView.findViewById<EditText>(R.id.groupEditText)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val hoursCountEditText = dialogView.findViewById<EditText>(R.id.hoursCountEditText)
        val attendanceEditText = dialogView.findViewById<EditText>(R.id.attendanceEditText)
        val notesEditText = dialogView.findViewById<EditText>(R.id.notesEditText)

        val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        dateEditText.setText(currentDate)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Новая запись часов")
            .setView(dialogView)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val type = if (typeRadioGroup.checkedRadioButtonId == teachingRadioId) {
                        TeacherHourType.TEACHING
                    } else {
                        TeacherHourType.CURATOR
                    }
                    val topic = topicEditText.text.toString().trim()
                    val group = groupEditText.text.toString().trim()
                    val date = dateEditText.text.toString().trim()
                    val time = timeEditText.text.toString().trim()
                    val hoursCount = hoursCountEditText.text.toString().trim().toIntOrNull() ?: 0
                    val attendance = attendanceEditText.text.toString().trim().toIntOrNull() ?: 0
                    val notes = notesEditText.text.toString().trim()

                    if (topic.isBlank() || group.isBlank() || date.isBlank() || time.isBlank() || hoursCount <= 0) {
                        Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (type == TeacherHourType.TEACHING) {
                        val weekTotal = getTeachingHoursForWeek(date)
                        if (weekTotal + hoursCount > 36) {
                            Toast.makeText(
                                requireContext(),
                                "Превышение нормы: учебная нагрузка не должна быть больше 36 часов в неделю",
                                Toast.LENGTH_LONG
                            ).show()
                            return@setOnClickListener
                        }
                    }

                    saveRecord(
                        TeacherHourRecord(
                            teacherId = teacherId,
                            teacherName = teacherName,
                            type = type,
                            topic = topic,
                            groupName = group,
                            date = date,
                            time = time,
                            hoursCount = hoursCount,
                            attendanceCount = attendance,
                            notes = notes
                        )
                    )
                    dialog.dismiss()
                }
            }
    }

    private fun showEditDialog(record: TeacherHourRecord) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_teacher_hour, null)
        val typeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.typeRadioGroup)
        val teachingRadioId = R.id.teachingRadio
        val topicEditText = dialogView.findViewById<EditText>(R.id.topicEditText)
        val groupEditText = dialogView.findViewById<EditText>(R.id.groupEditText)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val hoursCountEditText = dialogView.findViewById<EditText>(R.id.hoursCountEditText)
        val attendanceEditText = dialogView.findViewById<EditText>(R.id.attendanceEditText)
        val notesEditText = dialogView.findViewById<EditText>(R.id.notesEditText)

        topicEditText.setText(record.topic)
        groupEditText.setText(record.groupName)
        dateEditText.setText(record.date)
        timeEditText.setText(record.time)
        hoursCountEditText.setText(record.hoursCount.toString())
        attendanceEditText.setText(record.attendanceCount.toString())
        notesEditText.setText(record.notes)
        typeRadioGroup.check(if (record.type == TeacherHourType.TEACHING) teachingRadioId else R.id.curatorRadio)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать запись")
            .setView(dialogView)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val type = if (typeRadioGroup.checkedRadioButtonId == teachingRadioId) {
                        TeacherHourType.TEACHING
                    } else {
                        TeacherHourType.CURATOR
                    }
                    val topic = topicEditText.text.toString().trim()
                    val group = groupEditText.text.toString().trim()
                    val date = dateEditText.text.toString().trim()
                    val time = timeEditText.text.toString().trim()
                    val hoursCount = hoursCountEditText.text.toString().trim().toIntOrNull() ?: 0
                    val attendance = attendanceEditText.text.toString().trim().toIntOrNull() ?: 0
                    val notes = notesEditText.text.toString().trim()

                    if (topic.isBlank() || group.isBlank() || date.isBlank() || time.isBlank() || hoursCount <= 0) {
                        Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (type == TeacherHourType.TEACHING) {
                        val weekTotal = getTeachingHoursForWeek(date, excludeRecordId = record.id)
                        if (weekTotal + hoursCount > 36) {
                            Toast.makeText(
                                requireContext(),
                                "Превышение нормы: учебная нагрузка не должна быть больше 36 часов в неделю",
                                Toast.LENGTH_LONG
                            ).show()
                            return@setOnClickListener
                        }
                    }

                    updateRecord(
                        record.copy(
                            type = type,
                            topic = topic,
                            groupName = group,
                            date = date,
                            time = time,
                            hoursCount = hoursCount,
                            attendanceCount = attendance,
                            notes = notes
                        )
                    )
                    dialog.dismiss()
                }
            }
    }

    private fun getTeachingHoursForWeek(dateValue: String, excludeRecordId: String? = null): Int {
        val targetCalendar = Calendar.getInstance()
        val parser = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val parsedDate = try {
            parser.parse(dateValue)
        } catch (_: Exception) {
            null
        } ?: return 0

        targetCalendar.time = parsedDate
        val targetWeek = targetCalendar.get(Calendar.WEEK_OF_YEAR)
        val targetYear = targetCalendar.get(Calendar.YEAR)

        return records
            .filter { it.type == TeacherHourType.TEACHING && it.id != excludeRecordId }
            .filter {
                val c = Calendar.getInstance()
                val d = try {
                    parser.parse(it.date)
                } catch (_: Exception) {
                    null
                } ?: return@filter false
                c.time = d
                c.get(Calendar.WEEK_OF_YEAR) == targetWeek && c.get(Calendar.YEAR) == targetYear
            }
            .sumOf { it.hoursCount }
    }

    private fun saveRecord(record: TeacherHourRecord) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                teacherHourRepository.addTeacherHour(record)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Запись сохранена", Toast.LENGTH_SHORT).show()
                    loadRecords(showProgress = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateRecord(record: TeacherHourRecord) {
        if (record.id.isBlank()) return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = teacherHourRepository.updateTeacherHour(record)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (success) {
                        Toast.makeText(requireContext(), "Запись обновлена", Toast.LENGTH_SHORT).show()
                        loadRecords(showProgress = false)
                    } else {
                        Toast.makeText(requireContext(), "Ошибка обновления", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showHourMenu(item: TeacherHourRecord) {
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_teacher_hour_menu, null)
        val titleText = menuView.findViewById<TextView>(R.id.menuTitleText)
        val editButton = menuView.findViewById<MaterialButton>(R.id.editButton)
        val deleteButton = menuView.findViewById<MaterialButton>(R.id.deleteButton)

        titleText.text = item.topic.ifBlank { "${item.date} ${item.time}" }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(menuView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        editButton.setOnClickListener {
            dialog.dismiss()
            showEditDialog(item)
        }
        deleteButton.setOnClickListener {
            dialog.dismiss()
            showDeleteDialog(item)
        }

        dialog.show()
    }

    private fun showDeleteDialog(item: TeacherHourRecord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить запись?")
            .setMessage("${item.date} • ${item.topic}")
            .setPositiveButton("Удалить") { _, _ -> deleteRecord(item.id) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteRecord(id: String) {
        if (id.isBlank()) return
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val success = teacherHourRepository.deleteTeacherHour(id)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(requireContext(), "Запись удалена", Toast.LENGTH_SHORT).show()
                    loadRecords()
                } else {
                    Toast.makeText(requireContext(), "Не удалось удалить запись", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportToPdf() {
        if (records.isEmpty()) {
            Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "Часы_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            savePdfLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePdfToUri(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        exportPdfButton.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val document = StatisticsExporter.createTeacherHoursPdfDocument(teacherName, records.toList())
                val success = StatisticsExporter.savePdfToUri(requireContext(), document, uri)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportPdfButton.isEnabled = true
                    if (success) {
                        Toast.makeText(requireContext(), "PDF сохранён в Документы", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ошибка сохранения PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    exportPdfButton.isEnabled = true
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportToCsv() {
        if (records.isEmpty()) {
            Toast.makeText(requireContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "teacher_hours_$now.csv"
            val content = buildString {
                append("Тип,Тема,Группа,Дата,Время,Часы,Присутствовали,Комментарий\n")
                records.forEach { r ->
                    val type = if (r.type == TeacherHourType.TEACHING) "Учебные" else "Кураторские"
                    append("$type,${r.topic},${r.groupName},${r.date},${r.time},${r.hoursCount},${r.attendanceCount},${r.notes}\n")
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }
            val uri = requireContext().contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri == null) {
                Toast.makeText(requireContext(), "Не удалось создать файл", Toast.LENGTH_SHORT).show()
                return
            }
            requireContext().contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            Toast.makeText(requireContext(), "CSV сохранен в Документы", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
