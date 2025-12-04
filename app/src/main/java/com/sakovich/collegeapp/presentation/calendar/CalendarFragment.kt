package com.sakovich.collegeapp.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Event
import com.sakovich.collegeapp.data.models.EventType
import com.sakovich.collegeapp.data.repositories.EventRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class CalendarFragment : Fragment() {

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAddEvent: FloatingActionButton
    private lateinit var btnUpcoming: MaterialButton
    private lateinit var btnPast: MaterialButton

    private lateinit var eventRepository: EventRepository
    private lateinit var userRepository: UserRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var eventsAdapter: EventsAdapter

    private var eventsList = mutableListOf<Event>()
    private var currentFilter = EventFilter.UPCOMING
    private var currentUser: com.sakovich.collegeapp.data.models.User? = null
    private var canEditEvents = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        eventRepository = EventRepository()
        userRepository = UserRepository()

        initViews(view)
        setupRecyclerView()
        loadCurrentUser()
    }

    private fun initViews(view: View) {
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        fabAddEvent = view.findViewById(R.id.fabAddEvent)
        btnUpcoming = view.findViewById(R.id.btnUpcoming)
        btnPast = view.findViewById(R.id.btnPast)
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
                        loadEvents()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        setupPermissions()
                        setupClickListeners()
                        loadEvents()
                    }
                }
            }
        } else {
            setupPermissions()
            setupClickListeners()
            loadEvents()
        }
    }

    private fun setupPermissions() {
        // 👇 ПРОВЕРЯЕМ ПРАВА ПОЛЬЗОВАТЕЛЯ
        canEditEvents = currentUser?.canEditEvents() == true

        // 👇 СКРЫВАЕМ FAB ЕСЛИ НЕТ ПРАВ НА ДОБАВЛЕНИЕ
        if (canEditEvents) {
            fabAddEvent.visibility = View.VISIBLE
        } else {
            fabAddEvent.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        eventsAdapter = EventsAdapter(eventsList, canEditEvents) { event ->
            // Обработка клика на событие - временно показываем Snackbar
            showEventSnackbar(event)
        }
        eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        eventsRecyclerView.adapter = eventsAdapter
    }

    private fun setupClickListeners() {
        fabAddEvent.setOnClickListener {
            if (canEditEvents) {
                // 👇 ВЫЗЫВАЕМ ДИАЛОГ ДОБАВЛЕНИЯ СОБЫТИЯ
                showAddEventDialog()
            } else {
                Snackbar.make(requireView(), "У вас нет прав для добавления событий", Snackbar.LENGTH_SHORT).show()
            }
        }

        btnUpcoming.setOnClickListener {
            currentFilter = EventFilter.UPCOMING
            updateFilterButtons()
            filterEvents()
        }

        btnPast.setOnClickListener {
            currentFilter = EventFilter.PAST
            updateFilterButtons()
            filterEvents()
        }
    }

    private fun updateFilterButtons() {
        btnUpcoming.isSelected = currentFilter == EventFilter.UPCOMING
        btnPast.isSelected = currentFilter == EventFilter.PAST
    }

    private fun loadEvents() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Загружаем реальные события из Firestore
                val events = eventRepository.getAllEvents()

                requireActivity().runOnUiThread {
                    eventsList.clear()
                    eventsList.addAll(events)
                    filterEvents()
                    progressBar.visibility = View.GONE

                    if (events.isEmpty()) {
                        // Если событий нет, показываем тестовые данные
                        showTestEvents()
                        Snackbar.make(requireView(), "Используются тестовые данные", Snackbar.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    // Если ошибка, показываем тестовые данные
                    showTestEvents()
                    Snackbar.make(requireView(), "Ошибка загрузки. Используются тестовые данные", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showTestEvents() {
        val testEvents = createTestEvents()
        eventsList.clear()
        eventsList.addAll(testEvents)
        filterEvents()
    }

    private fun createTestEvents(): List<Event> {
        val calendar = Calendar.getInstance()

        // Прошедшее событие
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val pastEvent = Event(
            id = "1",
            title = "Лекция по программированию",
            description = "Основы Kotlin и Android разработки",
            date = calendar.time,
            startTime = "10:00",
            endTime = "11:30",
            type = EventType.LECTURE,
            subject = "Программирование",
            location = "Аудитория 301",
            groupName = "ПО-31",
            teacherName = "Павловский П.А."
        )

        // Текущее событие
        calendar.add(Calendar.DAY_OF_MONTH, 2)
        val currentEvent = Event(
            id = "2",
            title = "Практика по базам данных",
            description = "Работа с SQL и Room",
            date = calendar.time,
            startTime = "14:00",
            endTime = "15:30",
            type = EventType.PRACTICE,
            subject = "Базы данных",
            location = "Аудитория 205",
            groupName = "ПО-31",
            teacherName = "Иванова М.С."
        )

        // Будущее событие
        calendar.add(Calendar.DAY_OF_MONTH, 3)
        val futureEvent = Event(
            id = "3",
            title = "Собрание группы",
            description = "Обсуждение учебных вопросов",
            date = calendar.time,
            startTime = "16:00",
            endTime = "17:00",
            type = EventType.MEETING,
            subject = "Организационные вопросы",
            location = "Аудитория 101",
            groupName = "ПО-31",
            teacherName = "Староста группы"
        )

        return listOf(pastEvent, currentEvent, futureEvent)
    }

    private fun filterEvents() {
        val filteredList = when (currentFilter) {
            EventFilter.UPCOMING -> eventsList.filter { !it.isPastEvent() }
            EventFilter.PAST -> eventsList.filter { it.isPastEvent() }
        }.sortedBy { it.date }

        eventsAdapter.updateEvents(filteredList)

        if (filteredList.isEmpty()) {
            val message = when (currentFilter) {
                EventFilter.UPCOMING -> "Нет предстоящих событий"
                EventFilter.PAST -> "Нет прошедших событий"
            }
            Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showEventSnackbar(event: Event) {
        val editHint = if (canEditEvents) "\n\nℹ️ Нажмите и удерживайте для редактирования" else ""
        Snackbar.make(requireView(),
            "Событие: ${event.title}\n📅 ${event.getFormattedDate()} 🕒 ${event.startTime}-${event.endTime}\n📍 ${event.location}$editHint",
            Snackbar.LENGTH_LONG
        ).show()
    }

    // 👇 НОВЫЙ МЕТОД ДЛЯ ПОКАЗА ДИАЛОГА ДОБАВЛЕНИЯ
    private fun showAddEventDialog() {
        val addEventDialog = AddEventDialog.newInstance(currentUser)
        addEventDialog.setOnEventAddedListener { newEvent ->
            // Сохраняем событие в Firestore
            saveEventToFirestore(newEvent)
        }
        addEventDialog.show(parentFragmentManager, "AddEventDialog")
    }

    // 👇 НОВЫЙ МЕТОД ДЛЯ СОХРАНЕНИЯ СОБЫТИЯ В FIRESTORE
    private fun saveEventToFirestore(event: Event) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eventId = eventRepository.addEvent(event)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    // Добавляем событие в список с правильным ID
                    val eventWithId = event.copy(id = eventId)
                    eventsList.add(eventWithId)
                    filterEvents()

                    Snackbar.make(requireView(), "Событие успешно добавлено", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Snackbar.make(requireView(), "Ошибка при добавлении события: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        enum class EventFilter {
            UPCOMING, PAST
        }
    }
}