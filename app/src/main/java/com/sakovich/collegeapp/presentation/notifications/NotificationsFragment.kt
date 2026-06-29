package com.sakovich.collegeapp.presentation.notifications

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Notification
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationsFragment : Fragment() {

    companion object {
        private const val TAG = "NotificationsFragment"
    }

    private lateinit var notificationsBackButton: ImageButton
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var notificationsMoreButton: ImageButton
    private lateinit var subtitleText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var notificationsAdapter: NotificationsAdapter

    private val notificationsList = mutableListOf<Notification>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        notificationRepository = NotificationRepository()

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadNotifications()
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }

    private fun initViews(view: View) {
        notificationsRecyclerView = view.findViewById(R.id.notificationsRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyText = view.findViewById(R.id.emptyText)
        notificationsMoreButton = view.findViewById(R.id.notificationsMoreButton)
        subtitleText = view.findViewById(R.id.subtitleText)
        notificationsBackButton = view.findViewById(R.id.notificationsBackButton)
    }

    private fun setupRecyclerView() {
        notificationsAdapter = NotificationsAdapter(
            notifications = notificationsList,
            onNotificationClick = { notification -> handleNotificationClick(notification) },
            onNotificationLongClick = { notification -> showNotificationMenu(notification) }
        )
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationsRecyclerView.adapter = notificationsAdapter
    }

    private fun setupClickListeners() {
        notificationsMoreButton.setOnClickListener { showHeaderMenu() }

        notificationsBackButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadNotifications() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState("Войдите в аккаунт")
            return
        }

        progressBar.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppLog.d(TAG, "🔍 Загрузка уведомлений для userId: ${currentUser.uid}")
                val notifications = notificationRepository.getUserNotifications(currentUser.uid)
                val unreadCount = notifications.count { !it.isRead }

                AppLog.d(TAG, "📬 Загружено уведомлений: ${notifications.size}, непрочитанных: $unreadCount")
                notifications.forEachIndexed { index, notification ->
                    AppLog.d(TAG, "  [$index] ${notification.title}: ${notification.message} (прочитано: ${notification.isRead}, userId: ${notification.userId})")
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    notificationsList.clear()
                    notificationsList.addAll(notifications)

                    AppLog.d(TAG, "📱 Обновление адаптера с ${notificationsList.size} уведомлениями")
                    notificationsAdapter.updateNotifications(notificationsList.toList())

                    notificationsRecyclerView.post {
                        notificationsAdapter.notifyDataSetChanged()
                    }

                    subtitleText.text = if (unreadCount > 0) {
                        "Непрочитанных: $unreadCount"
                    } else {
                        "Все ваши уведомления"
                    }

                    if (notifications.isEmpty()) {
                        showEmptyState("У вас нет уведомлений")
                    } else {
                        emptyStateLayout.visibility = View.GONE
                        notificationsRecyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showEmptyState("Ошибка загрузки уведомлений")
                }
            }
        }
    }

    private fun handleNotificationClick(notification: Notification) {

        if (!notification.isRead) {
            CoroutineScope(Dispatchers.IO).launch {
                notificationRepository.markAsRead(notification.id)
                withContext(Dispatchers.Main) {

                    val index = notificationsList.indexOfFirst { it.id == notification.id }
                    if (index != -1) {
                        notificationsList[index] = notification.copy(isRead = true)
                        notificationsAdapter.notifyItemChanged(index)
                        loadNotifications()
                    }
                }
            }
        }

        showNotificationDetailsDialog(notification)
    }

    private fun showNotificationDetailsDialog(notification: Notification) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_notification_details, null)

        val iconText = view.findViewById<TextView>(R.id.notificationIcon)
        val titleText = view.findViewById<TextView>(R.id.notificationTitle)
        val messageText = view.findViewById<TextView>(R.id.notificationMessage)
        val typeText = view.findViewById<TextView>(R.id.notificationType)
        val dateText = view.findViewById<TextView>(R.id.notificationDate)
        val statusText = view.findViewById<TextView>(R.id.notificationStatus)

        val (icon, color) = getTypeIconAndColor(notification.type)
        iconText.text = icon
        iconText.setTextColor(color)

        titleText.text = notification.title
        messageText.text = notification.message
        typeText.text = getTypeDisplayName(notification.type)

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        dateText.text = dateFormat.format(notification.createdAt)

        if (notification.isRead) {
            statusText.text = "Прочитано"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.notification_read_text))
            statusText.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_notification_status)
        } else {
            statusText.text = "Новое"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.notification_unread_text))
            statusText.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_notification_status)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton("Закрыть", null)
            .create()
            .show()
    }

    private fun getTypeIconAndColor(type: NotificationType): Pair<String, Int> {
        return when (type) {
            NotificationType.GRADE -> Pair("📊", ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
            NotificationType.ABSENCE -> Pair("📋", ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
            NotificationType.EVENT -> Pair("📅", ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            NotificationType.SCHEDULE -> Pair("📚", ContextCompat.getColor(requireContext(), android.R.color.holo_purple))
            NotificationType.CHAT -> Pair("💬", ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light))
            NotificationType.SYSTEM -> Pair("ℹ️", ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        }
    }

    private fun getTypeDisplayName(type: NotificationType): String {
        return when (type) {
            NotificationType.GRADE -> "Отметка"
            NotificationType.ABSENCE -> "Пропуск"
            NotificationType.EVENT -> "Мероприятие"
            NotificationType.SCHEDULE -> "Расписание"
            NotificationType.CHAT -> "Чат"
            NotificationType.SYSTEM -> "Системное"
        }
    }

    private fun showNotificationMenu(notification: Notification) {
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_menu, null)
        val titleText = menuView.findViewById<TextView>(R.id.menuTitleText)
        val detailsBtn = menuView.findViewById<MaterialButton>(R.id.actionButton1)
        val deleteBtn = menuView.findViewById<MaterialButton>(R.id.actionButton2)
        menuView.findViewById<View>(R.id.actionButton3).visibility = View.GONE
        menuView.findViewById<View>(R.id.actionButton4).visibility = View.GONE

        titleText.text = notification.title
        detailsBtn.text = "Подробнее"
        deleteBtn.text = "Удалить"
        detailsBtn.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_info)
        detailsBtn.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.text_secondary_dark)

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(menuView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        detailsBtn.setOnClickListener { dialog.dismiss(); handleNotificationClick(notification) }
        deleteBtn.setOnClickListener { dialog.dismiss(); deleteNotification(notification) }
        dialog.show()
    }

    private fun showDeleteDialog(notification: Notification) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить уведомление?")
            .setMessage(notification.message)
            .setPositiveButton("Удалить") { _, _ ->
                deleteNotification(notification)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteNotification(notification: Notification) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationRepository.deleteNotification(notification.id)
                withContext(Dispatchers.Main) {
                    val index = notificationsList.indexOfFirst { it.id == notification.id }
                    if (index != -1) {
                        notificationsList.removeAt(index)
                        notificationsAdapter.updateNotifications(notificationsList.toList())
                        subtitleText.text = if (notificationsList.any { !it.isRead }) {
                            "Непрочитанных: ${notificationsList.count { !it.isRead }}"
                        } else {
                            "Все ваши уведомления"
                        }
                        if (notificationsList.isEmpty()) {
                            showEmptyState("У вас нет уведомлений")
                        } else {
                            emptyStateLayout.visibility = View.GONE
                            notificationsRecyclerView.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun markAllAsRead() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        notificationsMoreButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationRepository.markAllAsRead(currentUser.uid)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    notificationsMoreButton.isEnabled = true
                    loadNotifications()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    notificationsMoreButton.isEnabled = true
                }
            }
        }
    }

    private fun deleteAllNotifications() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        notificationsMoreButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationRepository.deleteAllByUser(currentUser.uid)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    notificationsMoreButton.isEnabled = true
                    loadNotifications()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    notificationsMoreButton.isEnabled = true
                }
            }
        }
    }

    private fun showHeaderMenu() {
        val unreadCount = notificationsList.count { !it.isRead }
        val popup = PopupMenu(requireContext(), notificationsMoreButton, Gravity.END).apply {
            menu.add(0, 1, 0, "Все прочитано")
            menu.add(0, 2, 1, "Удалить все уведомления")
            menu.findItem(1)?.isEnabled = unreadCount > 0
            menu.findItem(2)?.isEnabled = notificationsList.isNotEmpty()
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        markAllAsRead()
                        true
                    }
                    2 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Удалить все уведомления?")
                            .setMessage("Это действие нельзя отменить.")
                            .setPositiveButton("Удалить") { _, _ -> deleteAllNotifications() }
                            .setNegativeButton("Отмена", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
        }
        popup.show()
    }

    private fun showEmptyState(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        notificationsRecyclerView.visibility = View.GONE
        emptyText.text = message
    }
}
