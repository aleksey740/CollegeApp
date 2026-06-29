package com.sakovich.collegeapp.presentation.events

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.GroupEvent
import java.util.Calendar
import java.util.Locale

class AddGroupEventDialogFragment : DialogFragment() {
    private var onSave: ((GroupEvent) -> Unit)? = null

    fun setOnSaveListener(listener: (GroupEvent) -> Unit) {
        onSave = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val editMode = args.getBoolean(ARG_EDIT_MODE, false)
        val groupName = args.getString(ARG_GROUP_NAME).orEmpty()
        val curatorId = args.getString(ARG_CURATOR_ID).orEmpty()
        val curatorName = args.getString(ARG_CURATOR_NAME).orEmpty()
        val creatorRole = args.getString(ARG_CREATOR_ROLE).orEmpty()

        val view = layoutInflater.inflate(R.layout.dialog_add_group_event, null)
        val titleInput = view.findViewById<TextInputEditText>(R.id.eventTitleInput)
        val titleLayout = view.findViewById<TextInputLayout>(R.id.eventTitleLayout)
        val dateInput = view.findViewById<TextInputEditText>(R.id.eventDateInput)
        val dateLayout = view.findViewById<TextInputLayout>(R.id.eventDateLayout)
        val timeInput = view.findViewById<TextInputEditText>(R.id.eventTimeInput)
        val timeLayout = view.findViewById<TextInputLayout>(R.id.eventTimeLayout)
        val placeInput = view.findViewById<TextInputEditText>(R.id.eventPlaceInput)
        val placeLayout = view.findViewById<TextInputLayout>(R.id.eventPlaceLayout)
        val descInput = view.findViewById<TextInputEditText>(R.id.eventDescriptionInput)

        val cal = Calendar.getInstance()
        if (editMode) {
            titleInput.setText(args.getString(ARG_TITLE).orEmpty())
            dateInput.setText(args.getString(ARG_DATE).orEmpty())
            timeInput.setText(args.getString(ARG_TIME).orEmpty())
            placeInput.setText(args.getString(ARG_PLACE).orEmpty())
            descInput.setText(args.getString(ARG_DESCRIPTION).orEmpty())
        } else {
            val prefillDate = args.getString(ARG_PREFILL_DATE).orEmpty()
            dateInput.setText(
                prefillDate.ifBlank {
                    String.format(
                        Locale.getDefault(),
                        "%02d.%02d.%04d",
                        cal.get(Calendar.DAY_OF_MONTH),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.YEAR)
                    )
                }
            )
            timeInput.setText(
                String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            )
        }

        dateInput.setOnClickListener {
            val parts = dateInput.text?.toString()?.trim().orEmpty().split(".")
            val day = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
            val month = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
            val year = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
            DatePickerDialog(requireContext(), { _, y, m, d ->
                dateInput.setText(String.format(Locale.getDefault(), "%02d.%02d.%04d", d, m + 1, y))
            }, year, month, day).show()
        }
        timeInput.setOnClickListener {
            val parts = timeInput.text?.toString()?.trim().orEmpty().split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: cal.get(Calendar.MINUTE)
            TimePickerDialog(requireContext(), { _, h, min ->
                timeInput.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min))
            }, hour, minute, true).show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editMode) "Редактировать мероприятие" else "Добавить мероприятие")
            .setView(view)
            .setNegativeButton("Отмена", null)
            .setPositiveButton(if (editMode) "Сохранить" else "Добавить", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                titleLayout.error = null
                dateLayout.error = null
                timeLayout.error = null
                placeLayout.error = null
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val date = dateInput.text?.toString()?.trim().orEmpty()
                val time = timeInput.text?.toString()?.trim().orEmpty()
                val place = placeInput.text?.toString()?.trim().orEmpty()
                val description = descInput.text?.toString()?.trim().orEmpty()
                var hasError = false
                if (title.isBlank()) {
                    titleLayout.error = "Обязательное поле"
                    hasError = true
                }
                if (date.isBlank()) {
                    dateLayout.error = "Обязательное поле"
                    hasError = true
                }
                if (time.isBlank()) {
                    timeLayout.error = "Обязательное поле"
                    hasError = true
                }
                if (place.isBlank()) {
                    placeLayout.error = "Обязательное поле"
                    hasError = true
                }
                if (hasError) {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val event = if (editMode) {
                    GroupEvent(
                        id = args.getString(ARG_EVENT_ID).orEmpty(),
                        title = title,
                        date = date,
                        time = time,
                        place = place,
                        description = description,
                        groupName = args.getString(ARG_GROUP_NAME).orEmpty(),
                        createdBy = args.getString(ARG_CREATED_BY).orEmpty(),
                        createdByName = args.getString(ARG_CREATED_BY_NAME).orEmpty(),
                        createdByRole = args.getString(ARG_CREATED_BY_ROLE).orEmpty(),
                        createdAt = java.util.Date(args.getLong(ARG_CREATED_AT, System.currentTimeMillis()))
                    )
                } else {
                    GroupEvent(
                        title = title,
                        date = date,
                        time = time,
                        place = place,
                        description = description,
                        groupName = groupName,
                        createdBy = curatorId,
                        createdByName = curatorName,
                        createdByRole = creatorRole
                    )
                }
                onSave?.invoke(event)
                dialog.dismiss()
            }
        }
        return dialog
    }

    companion object {
        private const val ARG_EDIT_MODE = "edit_mode"
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_GROUP_NAME = "group_name"
        private const val ARG_CURATOR_ID = "curator_id"
        private const val ARG_CURATOR_NAME = "curator_name"
        private const val ARG_CREATOR_ROLE = "creator_role"
        private const val ARG_TITLE = "title"
        private const val ARG_DATE = "date"
        private const val ARG_TIME = "time"
        private const val ARG_PLACE = "place"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_CREATED_BY = "created_by"
        private const val ARG_CREATED_BY_NAME = "created_by_name"
        private const val ARG_CREATED_BY_ROLE = "created_by_role"
        private const val ARG_CREATED_AT = "created_at"
        private const val ARG_PREFILL_DATE = "prefill_date"

        fun newInstance(
            groupName: String,
            curatorId: String,
            curatorName: String,
            creatorRole: String,
            prefillDate: String = ""
        ): AddGroupEventDialogFragment {
            return AddGroupEventDialogFragment().apply {
                arguments = bundleOf(
                    ARG_EDIT_MODE to false,
                    ARG_GROUP_NAME to groupName,
                    ARG_CURATOR_ID to curatorId,
                    ARG_CURATOR_NAME to curatorName,
                    ARG_CREATOR_ROLE to creatorRole,
                    ARG_PREFILL_DATE to prefillDate
                )
            }
        }

        fun newInstanceForEdit(event: GroupEvent): AddGroupEventDialogFragment {
            return AddGroupEventDialogFragment().apply {
                arguments = bundleOf(
                    ARG_EDIT_MODE to true,
                    ARG_EVENT_ID to event.id,
                    ARG_GROUP_NAME to event.groupName,
                    ARG_TITLE to event.title,
                    ARG_DATE to event.date,
                    ARG_TIME to event.time,
                    ARG_PLACE to event.place,
                    ARG_DESCRIPTION to event.description,
                    ARG_CREATED_BY to event.createdBy,
                    ARG_CREATED_BY_NAME to event.createdByName,
                    ARG_CREATED_BY_ROLE to event.createdByRole,
                    ARG_CREATED_AT to event.createdAt.time
                )
            }
        }
    }
}
