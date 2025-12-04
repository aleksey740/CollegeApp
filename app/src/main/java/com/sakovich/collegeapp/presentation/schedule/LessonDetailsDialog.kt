package com.sakovich.collegeapp.presentation.schedule

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.sakovich.collegeapp.data.models.DayOfWeek
import com.sakovich.collegeapp.data.models.Lesson
import com.sakovich.collegeapp.data.models.LessonType
import com.sakovich.collegeapp.data.models.TimeSlot
import com.sakovich.collegeapp.databinding.DialogLessonDetailsBinding

class LessonDetailsDialog : DialogFragment() {

    private var _binding: DialogLessonDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var lesson: Lesson
    private var canEdit: Boolean = false

    private var onLessonUpdatedListener: ((Lesson) -> Unit)? = null
    private var onLessonDeletedListener: ((String) -> Unit)? = null

    companion object {
        private const val ARG_LESSON_ID = "lesson_id"
        private const val ARG_SUBJECT = "subject"
        private const val ARG_TEACHER = "teacher"
        private const val ARG_GROUP = "group"
        private const val ARG_DAY = "day"
        private const val ARG_TIME_SLOT = "time_slot"
        private const val ARG_CLASSROOM = "classroom"
        private const val ARG_TYPE = "type"
        private const val ARG_CAN_EDIT = "can_edit"

        fun newInstance(lesson: Lesson, canEdit: Boolean): LessonDetailsDialog {
            return LessonDetailsDialog().apply {
                arguments = Bundle().apply {
                    // Передаем данные через отдельные поля вместо Parcelable
                    putString(ARG_LESSON_ID, lesson.id)
                    putString(ARG_SUBJECT, lesson.subject)
                    putString(ARG_TEACHER, lesson.teacherName)
                    putString(ARG_GROUP, lesson.groupName)
                    putString(ARG_DAY, lesson.dayOfWeek.name)
                    putString(ARG_TIME_SLOT, lesson.timeSlot.name)
                    putString(ARG_CLASSROOM, lesson.classroom)
                    putString(ARG_TYPE, lesson.type.name)
                    putBoolean(ARG_CAN_EDIT, canEdit)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            lesson = Lesson(
                id = it.getString(ARG_LESSON_ID, ""),
                subject = it.getString(ARG_SUBJECT, ""),
                teacherName = it.getString(ARG_TEACHER, ""),
                groupName = it.getString(ARG_GROUP, ""),
                dayOfWeek = DayOfWeek.valueOf(it.getString(ARG_DAY, "MONDAY")),
                timeSlot = TimeSlot.valueOf(it.getString(ARG_TIME_SLOT, "FIRST")),
                classroom = it.getString(ARG_CLASSROOM, ""),
                type = LessonType.valueOf(it.getString(ARG_TYPE, "LECTURE"))
            )
            canEdit = it.getBoolean(ARG_CAN_EDIT, false)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogLessonDetailsBinding.inflate(layoutInflater)

        setupUI()
        setupClickListeners()

        val builder = AlertDialog.Builder(requireContext())
            .setView(binding.root)

        // Добавляем кнопки в зависимости от прав
        if (canEdit) {
            builder.setPositiveButton("Сохранить") { dialog, which ->
                if (validateForm()) {
                    updateLesson()
                } else {
                    // Показываем ошибку валидации
                    Toast.makeText(requireContext(), "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
                .setNeutralButton("Удалить") { dialog, which ->
                    showDeleteConfirmation()
                }
                .setNegativeButton("Отмена") { dialog, which ->
                    dialog.dismiss()
                }
        } else {
            builder.setPositiveButton("Закрыть") { dialog, which ->
                dialog.dismiss()
            }
        }

        return builder.create()
    }

    private fun setupUI() {
        // Заполняем данные занятия
        binding.lessonSubjectText.text = lesson.subject
        binding.lessonDescriptionText.text = getLessonTypeDescription(lesson.type)
        binding.lessonDayText.text = getDayDisplayName(lesson.dayOfWeek)
        binding.lessonTimeText.text = getTimeRange(lesson.timeSlot) // Исправлено
        binding.lessonTypeText.text = getLessonTypeDisplayName(lesson.type)
        binding.lessonTeacherText.text = lesson.teacherName
        binding.lessonLocationText.text = "Аудитория: ${lesson.classroom}"
        binding.lessonGroupText.text = "Группа: ${lesson.groupName}"
        binding.lessonAuthorText.text = "Добавил: ${lesson.teacherName}"

        // Настраиваем режим редактирования
        if (canEdit) {
            binding.editSubject.setText(lesson.subject)
            binding.editTeacher.setText(lesson.teacherName)
            binding.editClassroom.setText(lesson.classroom)
            binding.editGroup.setText(lesson.groupName)

            // Показываем поля редактирования
            binding.editFields.visibility = android.view.View.VISIBLE
            binding.viewFields.visibility = android.view.View.GONE
            binding.editButton.visibility = android.view.View.GONE
        } else {
            // Показываем только просмотр
            binding.editFields.visibility = android.view.View.GONE
            binding.viewFields.visibility = android.view.View.VISIBLE
            binding.editButton.visibility = android.view.View.GONE
        }
    }

    private fun setupClickListeners() {
        // Переключение между просмотром и редактированием
        binding.editButton.setOnClickListener {
            toggleEditMode(true)
        }

        binding.cancelEditButton.setOnClickListener {
            toggleEditMode(false)
        }
    }

    private fun toggleEditMode(editMode: Boolean) {
        if (editMode) {
            binding.viewFields.visibility = android.view.View.GONE
            binding.editFields.visibility = android.view.View.VISIBLE
            binding.editButton.visibility = android.view.View.GONE
        } else {
            binding.viewFields.visibility = android.view.View.VISIBLE
            binding.editFields.visibility = android.view.View.GONE
            binding.editButton.visibility = android.view.View.VISIBLE
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

    private fun getLessonTypeDisplayName(type: LessonType): String {
        return when (type) {
            LessonType.LECTURE -> "Лекция"
            LessonType.PRACTICE -> "Практика"
            LessonType.LAB -> "Лабораторная"
            LessonType.SEMINAR -> "Семинар"
            LessonType.CONSULTATION -> "Консультация"
        }
    }

    private fun getLessonTypeDescription(type: LessonType): String {
        return when (type) {
            LessonType.LECTURE -> "Лекционное занятие"
            LessonType.PRACTICE -> "Практическое занятие"
            LessonType.LAB -> "Лабораторная работа"
            LessonType.SEMINAR -> "Семинарское занятие"
            LessonType.CONSULTATION -> "Консультация с преподавателем"
        }
    }

    private fun validateForm(): Boolean {
        if (binding.editSubject.text.toString().trim().isEmpty()) {
            binding.editSubject.error = "Введите название предмета"
            return false
        }

        if (binding.editTeacher.text.toString().trim().isEmpty()) {
            binding.editTeacher.error = "Введите ФИО преподавателя"
            return false
        }

        if (binding.editClassroom.text.toString().trim().isEmpty()) {
            binding.editClassroom.error = "Введите аудиторию"
            return false
        }

        return true
    }

    private fun updateLesson() {
        val updatedLesson = Lesson(
            id = lesson.id,
            subject = binding.editSubject.text.toString().trim(),
            teacherName = binding.editTeacher.text.toString().trim(),
            groupName = binding.editGroup.text.toString().trim(),
            dayOfWeek = lesson.dayOfWeek,
            timeSlot = lesson.timeSlot,
            classroom = binding.editClassroom.text.toString().trim(),
            type = lesson.type
        )

        onLessonUpdatedListener?.invoke(updatedLesson)
        dismiss()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление занятия")
            .setMessage("Вы уверены, что хотите удалить занятие \"${lesson.subject}\"?")
            .setPositiveButton("Удалить") { dialog, which ->
                onLessonDeletedListener?.invoke(lesson.id)
                dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun setOnLessonUpdatedListener(listener: (Lesson) -> Unit) {
        this.onLessonUpdatedListener = listener
    }

    fun setOnLessonDeletedListener(listener: (String) -> Unit) {
        this.onLessonDeletedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}