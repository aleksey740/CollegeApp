package com.sakovich.collegeapp.presentation.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.DayOfWeek
import com.sakovich.collegeapp.data.models.Lesson
import com.sakovich.collegeapp.data.models.LessonType
import com.sakovich.collegeapp.data.models.TimeSlot
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class ScheduleFragment : Fragment() {

    private lateinit var scheduleRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAddLesson: FloatingActionButton

    private lateinit var userRepository: UserRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var scheduleAdapter: ScheduleAdapter

    private var lessonsList = mutableListOf<Lesson>()
    private var currentUser: User? = null
    private var canEditSchedule = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()

        initViews(view)
        loadCurrentUser()
    }

    private fun initViews(view: View) {
        scheduleRecyclerView = view.findViewById(R.id.scheduleRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        fabAddLesson = view.findViewById(R.id.fabAddLesson)
    }

    private fun loadCurrentUser() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    currentUser = userRepository.getUser(firebaseUser.uid)
                    requireActivity().runOnUiThread {
                        setupPermissions()
                        setupClickListeners()
                        loadLessons()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        setupPermissions()
                        setupClickListeners()
                        loadLessons()
                    }
                }
            }
        } else {
            setupPermissions()
            setupClickListeners()
            loadLessons()
        }
    }

    private fun setupPermissions() {
        canEditSchedule = currentUser?.canEditEvents() == true

        if (canEditSchedule) {
            fabAddLesson.visibility = View.VISIBLE
        } else {
            fabAddLesson.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        fabAddLesson.setOnClickListener {
            if (canEditSchedule) {
                Snackbar.make(requireView(), "Функция добавления занятия временно недоступна", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(requireView(), "У вас нет прав для редактирования расписания", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        // Создаем таблицу 7x7 (дни недели + заголовки)
        val scheduleGrid = createScheduleGrid()

        scheduleAdapter = ScheduleAdapter(scheduleGrid, canEditSchedule) { lesson, position ->
            if (canEditSchedule && lesson != null) {
                Snackbar.make(requireView(), "Редактирование занятия: ${lesson.subject}", Snackbar.LENGTH_SHORT).show()
            } else if (lesson != null) {
                Snackbar.make(requireView(),
                    "${lesson.subject}\n📅 ${getDayDisplayName(lesson.dayOfWeek)} 🕒 ${getTimeRange(lesson.timeSlot)}\n👨‍🏫 ${lesson.teacherName} 📍 ${lesson.classroom}",
                    Snackbar.LENGTH_LONG
                ).show()
            } else if (canEditSchedule) {
                Snackbar.make(requireView(), "Добавить занятие в ячейку $position", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Используем GridLayoutManager для табличного вида
        val layoutManager = GridLayoutManager(requireContext(), 7)
        scheduleRecyclerView.layoutManager = layoutManager
        scheduleRecyclerView.adapter = scheduleAdapter
    }

    private fun createScheduleGrid(): List<ScheduleCell> {
        val grid = mutableListOf<ScheduleCell>()

        // Заголовок таблицы (левый верхний угол)
        grid.add(ScheduleCell("Время \\ День", true, true))

        // Заголовки дней недели
        DayOfWeek.values().forEach { day ->
            grid.add(ScheduleCell(getShortDayName(day), true, false))
        }

        // Заполняем таблицу
        TimeSlot.values().forEachIndexed { timeIndex, timeSlot ->
            // Ячейка времени
            grid.add(ScheduleCell(getTimeRangeDisplay(timeSlot), true, false))

            // Ячейки для каждого дня
            DayOfWeek.values().forEach { day ->
                val lesson = lessonsList.find { it.dayOfWeek == day && it.timeSlot == timeSlot }
                grid.add(ScheduleCell(null, false, false, lesson))
            }
        }

        return grid
    }

    private fun loadLessons() {
        progressBar.visibility = View.VISIBLE

        // Временно используем тестовые данные
        requireActivity().runOnUiThread {
            lessonsList.clear()
            lessonsList.addAll(createTestLessons())
            setupRecyclerView()
            progressBar.visibility = View.GONE
            Snackbar.make(requireView(), "Используются тестовые данные", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun createTestLessons(): List<Lesson> {
        return listOf(
            Lesson(
                id = "1",
                subject = "Программирование",
                teacherName = "Павловский П.А.",
                groupName = "ПО-31",
                dayOfWeek = DayOfWeek.MONDAY,
                timeSlot = TimeSlot.FIRST,
                classroom = "301",
                type = LessonType.LECTURE
            ),
            Lesson(
                id = "2",
                subject = "Базы данных",
                teacherName = "Иванова М.С.",
                groupName = "ПО-31",
                dayOfWeek = DayOfWeek.MONDAY,
                timeSlot = TimeSlot.SECOND,
                classroom = "205",
                type = LessonType.PRACTICE
            ),
            Lesson(
                id = "3",
                subject = "Математика",
                teacherName = "Сидоров А.В.",
                groupName = "ПО-31",
                dayOfWeek = DayOfWeek.TUESDAY,
                timeSlot = TimeSlot.FIRST,
                classroom = "101",
                type = LessonType.LECTURE
            ),
            Lesson(
                id = "4",
                subject = "Английский язык",
                teacherName = "Петрова Е.Л.",
                groupName = "ПО-31",
                dayOfWeek = DayOfWeek.WEDNESDAY,
                timeSlot = TimeSlot.THIRD,
                classroom = "402",
                type = LessonType.SEMINAR
            )
        )
    }

    // Локальные вспомогательные функции (временно)
    private fun getShortDayName(day: DayOfWeek): String {
        return when (day) {
            DayOfWeek.MONDAY -> "ПН"
            DayOfWeek.TUESDAY -> "ВТ"
            DayOfWeek.WEDNESDAY -> "СР"
            DayOfWeek.THURSDAY -> "ЧТ"
            DayOfWeek.FRIDAY -> "ПТ"
            DayOfWeek.SATURDAY -> "СБ"
        }
    }

    private fun getDayDisplayName(day: DayOfWeek): String {
        return when (day) {
            DayOfWeek.MONDAY -> "Понедельник"
            DayOfWeek.TUESDAY -> "Вторник"
            DayOfWeek.WEDNESDAY -> "Среда"
            DayOfWeek.THURSDAY -> "Четверг"
            DayOfWeek.FRIDAY -> "Пятница"
            DayOfWeek.SATURDAY -> "Суббота"
        }
    }

    private fun getTimeRange(timeSlot: TimeSlot): String {
        return when (timeSlot) {
            TimeSlot.FIRST -> "08:30-10:00"
            TimeSlot.SECOND -> "10:10-11:40"
            TimeSlot.THIRD -> "12:10-13:40"
            TimeSlot.FOURTH -> "14:00-15:30"
            TimeSlot.FIFTH -> "15:40-17:10"
            TimeSlot.SIXTH -> "17:20-18:50"
        }
    }

    private fun getTimeRangeDisplay(timeSlot: TimeSlot): String {
        return when (timeSlot) {
            TimeSlot.FIRST -> "08:30\n10:00"
            TimeSlot.SECOND -> "10:10\n11:40"
            TimeSlot.THIRD -> "12:10\n13:40"
            TimeSlot.FOURTH -> "14:00\n15:30"
            TimeSlot.FIFTH -> "15:40\n17:10"
            TimeSlot.SIXTH -> "17:20\n18:50"
        }
    }

    private fun getLessonTypeDisplayName(type: LessonType): String {
        return when (type) {
            LessonType.LECTURE -> "Лекция"
            LessonType.PRACTICE -> "Практика"
            LessonType.LAB -> "Лабораторная"
            LessonType.SEMINAR -> "Семинар"
            LessonType.CONSULTATION -> "Консультация"
        }
    }

    data class ScheduleCell(
        val title: String?,
        val isHeader: Boolean,
        val isCorner: Boolean = false,
        val lesson: Lesson? = null
    )
}