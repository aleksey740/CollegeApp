package com.sakovich.collegeapp.presentation.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminPanelFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val userRepository = UserRepository()
    private var menuActionsReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_panel, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth

        val subtitleText = view.findViewById<TextView>(R.id.subtitleText)
        view.findViewById<ImageButton>(R.id.adminPanelBackButton).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val catalogCard = view.findViewById<MaterialCardView>(R.id.catalogCard)
        val clubLeadersCard = view.findViewById<MaterialCardView>(R.id.clubLeadersCard)

        menuActionsReady = false
        catalogCard.isClickable = false
        clubLeadersCard.isClickable = false

        catalogCard.setOnClickListener {
            if (!menuActionsReady) return@setOnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminCatalogFragment())
                .addToBackStack(null)
                .commit()
        }

        clubLeadersCard.setOnClickListener {
            if (!menuActionsReady) return@setOnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminClubLeadersFragment())
                .addToBackStack(null)
                .commit()
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val currentUid = auth.currentUser?.uid
            val user = if (currentUid.isNullOrBlank()) null else userRepository.getUser(currentUid)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val hasAccess = user?.role == "teacher" || user?.role == "admin"
                if (!hasAccess) {
                    subtitleText.text = "Доступ только для роли Куратор"
                    subtitleText.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Нет доступа", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                subtitleText.visibility = View.GONE
                menuActionsReady = true
                catalogCard.isClickable = true
                clubLeadersCard.isClickable = true
                catalogCard.isFocusable = true
                clubLeadersCard.isFocusable = true
                clubLeadersCard.visibility = View.VISIBLE
            }
        }
    }
}
