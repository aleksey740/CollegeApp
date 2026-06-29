package com.sakovich.collegeapp.presentation.profile

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EditPersonalInfoFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository

    private lateinit var addressEditText: TextInputEditText
    private lateinit var birthDateEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var parentNameEditText: TextInputEditText
    private lateinit var parentPhoneEditText: TextInputEditText
    private lateinit var parentName2EditText: TextInputEditText
    private lateinit var parentPhone2EditText: TextInputEditText
    private lateinit var fundingTypeDropdown: AutoCompleteTextView
    private lateinit var switchDormitory: SwitchMaterial
    private lateinit var switchDisabled: SwitchMaterial
    private lateinit var switchLargeFamily: SwitchMaterial
    private lateinit var switchLowIncome: SwitchMaterial
    private lateinit var switchOrphan: SwitchMaterial
    private lateinit var switchNonResident: SwitchMaterial
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var progressBar: ProgressBar

    private val fundingOptions = listOf("Не выбрано", "Бюджет", "Платник")

    private var currentUser: User? = null
    private val calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_edit_personal_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()

        initViews(view)
        setupClickListeners()
        loadCurrentUserInfo()
    }

    private fun initViews(view: View) {
        addressEditText = view.findViewById(R.id.addressEditText)
        birthDateEditText = view.findViewById(R.id.birthDateEditText)
        phoneEditText = view.findViewById(R.id.phoneEditText)
        parentNameEditText = view.findViewById(R.id.parentNameEditText)
        parentPhoneEditText = view.findViewById(R.id.parentPhoneEditText)
        parentName2EditText = view.findViewById(R.id.parentName2EditText)
        parentPhone2EditText = view.findViewById(R.id.parentPhone2EditText)
        fundingTypeDropdown = view.findViewById(R.id.fundingTypeDropdown)
        switchDormitory = view.findViewById(R.id.switchDormitory)
        switchDisabled = view.findViewById(R.id.switchDisabled)
        switchLargeFamily = view.findViewById(R.id.switchLargeFamily)
        switchLowIncome = view.findViewById(R.id.switchLowIncome)
        switchOrphan = view.findViewById(R.id.switchOrphan)
        switchNonResident = view.findViewById(R.id.switchNonResident)
        saveButton = view.findViewById(R.id.saveButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        progressBar = view.findViewById(R.id.progressBar)

        val fundingAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf("Не выбрано", "Бюджет", "Платник"))
        fundingTypeDropdown.setAdapter(fundingAdapter)
        fundingTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            fundingTypeDropdown.setText(fundingOptions.getOrNull(position) ?: "", false)
        }
    }

    private fun setupClickListeners() {

        birthDateEditText.setOnClickListener {
            showDatePicker()
        }

        saveButton.setOnClickListener {
            savePersonalInfo()
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                birthDateEditText.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun loadCurrentUserInfo() {
        val firebaseUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = userRepository.getUser(firebaseUser.uid)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (user != null) {
                        currentUser = user
                        fillFields(user)
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fillFields(user: User) {
        addressEditText.setText(user.address)
        birthDateEditText.setText(user.birthDate)
        phoneEditText.setText(user.phone)
        parentNameEditText.setText(user.parentName)
        parentPhoneEditText.setText(user.parentPhone)
        parentName2EditText.setText(user.parentName2)
        parentPhone2EditText.setText(user.parentPhone2)
        val fundingLabel = when (user.fundingType) {
            "budget" -> "Бюджет"
            "paid" -> "Платник"
            else -> "Не выбрано"
        }
        fundingTypeDropdown.setText(fundingLabel, false)
        switchDormitory.isChecked = user.livesInDormitory
        switchDisabled.isChecked = user.isDisabled
        switchLargeFamily.isChecked = user.isLargeFamily
        switchLowIncome.isChecked = user.isLowIncome
        switchOrphan.isChecked = user.isOrphan
        switchNonResident.isChecked = user.isNonResident
    }

    private fun savePersonalInfo() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        val address = addressEditText.text.toString().trim()
        val birthDate = birthDateEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val parentName = parentNameEditText.text.toString().trim()
        val parentPhone = parentPhoneEditText.text.toString().trim()
        val parentName2 = parentName2EditText.text.toString().trim()
        val parentPhone2 = parentPhone2EditText.text.toString().trim()
        val fundingLabel = fundingTypeDropdown.text.toString().trim()
        val fundingType = when {
            fundingLabel == "Бюджет" -> "budget"
            fundingLabel == "Платник" -> "paid"
            else -> ""
        }
        val livesInDormitory = switchDormitory.isChecked
        val isDisabled = switchDisabled.isChecked
        val isLargeFamily = switchLargeFamily.isChecked
        val isLowIncome = switchLowIncome.isChecked
        val isOrphan = switchOrphan.isChecked
        val isNonResident = switchNonResident.isChecked

        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = userRepository.updatePersonalInfo(
                    userId = firebaseUser.uid,
                    address = address,
                    birthDate = birthDate,
                    phone = phone,
                    parentName = parentName,
                    parentPhone = parentPhone,
                    parentName2 = parentName2,
                    parentPhone2 = parentPhone2,
                    livesInDormitory = livesInDormitory,
                    isDisabled = isDisabled,
                    isLargeFamily = isLargeFamily,
                    fundingType = fundingType,
                    isLowIncome = isLowIncome,
                    isOrphan = isOrphan,
                    isNonResident = isNonResident
                )

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true

                    if (success) {
                        Toast.makeText(requireContext(), "✅ Информация сохранена!", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "❌ Ошибка сохранения", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true
                    Toast.makeText(requireContext(), "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление информации")
            .setMessage("Вы уверены, что хотите удалить всю личную информацию?")
            .setPositiveButton("Удалить") { _, _ ->
                deletePersonalInfo()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deletePersonalInfo() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = userRepository.deletePersonalInfo(firebaseUser.uid)

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (success) {
                        Toast.makeText(requireContext(), "✅ Информация удалена!", Toast.LENGTH_SHORT).show()
                        addressEditText.setText("")
                        birthDateEditText.setText("")
                        phoneEditText.setText("")
                        parentNameEditText.setText("")
                        parentPhoneEditText.setText("")
                        parentName2EditText.setText("")
                        parentPhone2EditText.setText("")
                        fundingTypeDropdown.setText("", false)
                        switchDormitory.isChecked = false
                        switchDisabled.isChecked = false
                        switchLargeFamily.isChecked = false
                        switchLowIncome.isChecked = false
                        switchOrphan.isChecked = false
                        switchNonResident.isChecked = false
                    } else {
                        Toast.makeText(requireContext(), "❌ Ошибка удаления", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        fun newInstance() = EditPersonalInfoFragment()
    }
}
