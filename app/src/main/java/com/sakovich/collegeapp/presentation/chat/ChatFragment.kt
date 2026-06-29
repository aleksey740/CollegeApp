package com.sakovich.collegeapp.presentation.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.PopupMenu
import android.widget.Toast
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ChatRoom
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.data.repositories.ChatRepository
import com.sakovich.collegeapp.data.repositories.NotificationRepository
import com.sakovich.collegeapp.data.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ChatFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var chatRepository: ChatRepository
    private val notificationRepository = NotificationRepository()

    private lateinit var pinnedCard: View
    private lateinit var pinnedText: TextView
    private lateinit var pinnedMeta: TextView
    private lateinit var unpinButton: MaterialButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var stickersButton: ImageButton
    private lateinit var chatOverflowButton: MaterialButton
    private lateinit var chatBackButton: ImageButton

    private lateinit var chatHeaderRow: View
    private lateinit var chatSearchContainer: View
    private lateinit var chatSearchInput: TextInputEditText
    private lateinit var chatSearchCloseButton: MaterialButton
    private lateinit var chatSearchCalendarButton: MaterialButton

    private var currentUser: User? = null
    private var currentRoom: ChatRoom? = null
    private var currentMessages: List<com.sakovich.collegeapp.data.models.ChatMessage> = emptyList()
    private var messagesListener: ListenerRegistration? = null
    private var roomListener: ListenerRegistration? = null
    private var editingMessage: com.sakovich.collegeapp.data.models.ChatMessage? = null

    private lateinit var messagesAdapter: ChatMessagesAdapter

    private var isSearchMode: Boolean = false
    private var searchYear: Int? = null
    private var searchMonth: Int? = null
    private var searchDay: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        userRepository = UserRepository()
        chatRepository = ChatRepository()

        pinnedCard = view.findViewById(R.id.pinnedCard)
        pinnedText = view.findViewById(R.id.pinnedText)
        pinnedMeta = view.findViewById(R.id.pinnedMeta)
        unpinButton = view.findViewById(R.id.unpinButton)
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        emptyText = view.findViewById(R.id.emptyText)
        progressBar = view.findViewById(R.id.progressBar)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        stickersButton = view.findViewById(R.id.stickersButton)
        chatOverflowButton = view.findViewById(R.id.chatOverflowButton)
        chatBackButton = view.findViewById(R.id.chatBackButton)

        chatHeaderRow = view.findViewById(R.id.chatHeaderRow)
        chatSearchContainer = view.findViewById(R.id.chatSearchContainer)
        chatSearchInput = view.findViewById(R.id.chatSearchInput)
        chatSearchCloseButton = view.findViewById(R.id.chatSearchCloseButton)
        chatSearchCalendarButton = view.findViewById(R.id.chatSearchCalendarButton)

        messagesAdapter = ChatMessagesAdapter(
            context = requireContext().applicationContext,
            currentUserId = auth.currentUser?.uid ?: "",
            onMessageLongClick = { message -> showMessageActions(message) }
        )
        messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesRecyclerView.adapter = messagesAdapter

        sendButton.setOnClickListener { sendMessage() }
        stickersButton.setOnClickListener { showStickerPickerDialog() }
        unpinButton.setOnClickListener { handleUnpin() }
        chatOverflowButton.setOnClickListener { showChatActionsMenu() }
        chatBackButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        chatSearchCloseButton.setOnClickListener { closeSearchMode() }
        chatSearchCalendarButton.setOnClickListener { showSearchDatePicker() }
        chatSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isSearchMode) applySearchFilter()
            }
        })

        loadUserAndRooms()
    }

    override fun onDestroyView() {
        messagesListener?.remove()
        roomListener?.remove()
        messagesListener = null
        roomListener = null
        super.onDestroyView()
    }

    private fun loadUserAndRooms() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            showEmpty("Войдите в аккаунт для чата")
            sendButton.isEnabled = false
            stickersButton.isEnabled = false
            return
        }

        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val user = userRepository.getUser(firebaseUser.uid)
            if (user == null) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showEmpty("Не удалось загрузить профиль")
                    stickersButton.isEnabled = false
                }
                return@launch
            }
            val rooms = chatRepository.getRoomsForUser(user)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                currentUser = user
                if (rooms.isNotEmpty()) {
                    selectRoom(rooms.first())
                } else {
                    showEmpty("Нет доступа к чату. У вас должна быть привязка к группе.")
                    stickersButton.isEnabled = false
                }
            }
        }
    }

    private fun selectRoom(room: ChatRoom) {
        cancelEditMode()
        stickersButton.isEnabled = true
        currentRoom = room
        observeMessages(room.id)
        observeRoomMetadata(room.id)
    }

    private fun observeMessages(roomId: String) {
        messagesListener?.remove()
        progressBar.visibility = View.VISIBLE

        messagesListener = chatRepository.observeMessages(
            roomId = roomId,
            onChanged = { messages ->
                if (!isAdded) return@observeMessages
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    currentMessages = messages
                    if (isSearchMode) {
                        applySearchFilter()
                    } else {
                        messagesAdapter.submitList(messages)
                        if (messages.isEmpty()) {
                            showEmpty("Пока нет сообщений. Начните диалог.")
                        } else {
                            emptyText.visibility = View.GONE
                            messagesRecyclerView.visibility = View.VISIBLE

                            val lastAdapterIndex = maxOf(0, messagesAdapter.itemCount - 1)
                            messagesRecyclerView.scrollToPosition(lastAdapterIndex)
                        }
                    }
                }
            },
            onError = {
                if (!isAdded) return@observeMessages
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    showEmpty("Ошибка загрузки сообщений")
                }
            }
        )
    }

    private fun observeRoomMetadata(roomId: String) {
        roomListener?.remove()
        roomListener = chatRepository.observeRoom(
            roomId = roomId,
            onChanged = { room ->
                if (!isAdded) return@observeRoom
                requireActivity().runOnUiThread {
                    currentRoom = room
                    updatePinnedUi(room)
                }
            },
            onError = { }
        )
    }

    private fun showStickerPickerDialog() {
        val catalog = try {
            LocalStickerCatalog.load(requireContext())
        } catch (_: Exception) {
            null
        }
        if (catalog == null || catalog.stickers.isEmpty()) {
            Toast.makeText(requireContext(), "Стикеры не найдены", Toast.LENGTH_SHORT).show()
            return
        }

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
        messageInput.clearFocus()

        val dialog = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheet_Stickers)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_sticker_picker, null)
        dialog.setContentView(dialogView)

        val recycler = dialogView.findViewById<RecyclerView>(R.id.stickerPickerRecycler)
        val span = if (resources.configuration.screenWidthDp >= 400) 4 else 3
        recycler.layoutManager = GridLayoutManager(requireContext(), span)
        recycler.adapter = StickerGridAdapter(catalog.stickers) { id ->
            dialog.dismiss()
            sendSticker(id)
        }
        dialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.62f).toInt().coerceAtLeast(420)
        dialog.behavior.isFitToContents = true
        dialog.show()
    }

    private fun sendSticker(stickerId: String) {
        val user = currentUser ?: return
        val room = currentRoom ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val success = chatRepository.sendSticker(room, user, stickerId)
            if (success) notifyGroupAboutNewChatMessage(room, user)
            withContext(Dispatchers.Main) {
                if (!success) {
                    Toast.makeText(requireContext(), "Не удалось отправить стикер", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage() {
        val user = currentUser ?: return
        val room = currentRoom ?: return
        val text = messageInput.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return

        sendButton.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            val success = if (editingMessage != null) {
                chatRepository.updateOwnMessage(editingMessage!!.id, user.id, text)
            } else {
                val sent = chatRepository.sendMessage(room, user, text)
                if (sent) notifyGroupAboutNewChatMessage(room, user)
                sent
            }
            withContext(Dispatchers.Main) {
                sendButton.isEnabled = true
                if (success) {
                    messageInput.setText("")
                    if (editingMessage != null) {
                        Toast.makeText(requireContext(), "Сообщение обновлено", Toast.LENGTH_SHORT).show()
                    }
                    cancelEditMode()
                } else {
                    Toast.makeText(
                        requireContext(),
                        if (editingMessage != null) "Не удалось изменить сообщение" else "Не удалось отправить сообщение",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showMessageActions(message: com.sakovich.collegeapp.data.models.ChatMessage) {
        val user = currentUser ?: return
        val isMine = message.senderId == user.id
        val canModerate = user.isTeacher() || user.isHeadman()

        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_menu, null)
        val titleText = menuView.findViewById<android.widget.TextView>(R.id.menuTitleText)
        val btn1 = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton1)
        val btn2 = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton2)
        val btn3 = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton3)
        val btn4 = menuView.findViewById<com.google.android.material.button.MaterialButton>(R.id.actionButton4)

        titleText.text = when {
            message.isSticker -> "Стикер"
            else -> message.text.take(80).let { if (message.text.length > 80) "$it..." else it }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(menuView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (message.isSticker) {
            btn1.visibility = View.GONE
            btn3.visibility = View.GONE
            btn4.visibility = View.GONE
            val canDeleteSticker = message.senderId == user.id || canModerate
            if (!canDeleteSticker) return
            btn2.visibility = View.VISIBLE
            btn2.text = "Удалить"
            btn2.setOnClickListener { dialog.dismiss(); deleteMessage(message) }
            dialog.show()
            return
        }

        if (isMine) {
            btn1.visibility = View.VISIBLE
            btn1.text = "Редактировать"
            btn1.setOnClickListener { dialog.dismiss(); startEditMode(message) }
            btn2.visibility = View.VISIBLE
            btn2.text = "Удалить"
            btn2.setOnClickListener { dialog.dismiss(); deleteMessage(message) }
        } else {
            btn1.visibility = View.GONE
            btn2.visibility = if (canModerate) View.VISIBLE else View.GONE
            if (canModerate) {
                btn2.text = "Удалить"
                btn2.setOnClickListener { dialog.dismiss(); deleteMessage(message) }
            }
        }

        if (canModerate) {
            btn3.visibility = View.VISIBLE
            btn3.text = "Закрепить сообщение"
            btn3.setOnClickListener { dialog.dismiss(); pinMessage(message) }
        } else {
            btn3.visibility = View.GONE
        }
        btn4.visibility = View.GONE

        if (btn1.visibility != View.VISIBLE && btn2.visibility != View.VISIBLE && btn3.visibility != View.VISIBLE) return
        dialog.show()
    }

    private fun startEditMode(message: com.sakovich.collegeapp.data.models.ChatMessage) {
        editingMessage = message
        messageInput.setText(message.text)
        messageInput.setSelection(message.text.length)
        sendButton.text = "Сохранить"
        Toast.makeText(requireContext(), "Режим редактирования включен", Toast.LENGTH_SHORT).show()
    }

    private fun cancelEditMode() {
        editingMessage = null
        sendButton.text = "Отправить"
    }

    private fun deleteMessage(message: com.sakovich.collegeapp.data.models.ChatMessage) {
        val user = currentUser ?: return
        val canDelete = message.senderId == user.id || user.isTeacher() || user.isHeadman()
        if (!canDelete) return

        CoroutineScope(Dispatchers.IO).launch {
            val success = chatRepository.deleteMessage(message.id)
            withContext(Dispatchers.Main) {
                if (success) {
                    if (editingMessage?.id == message.id) cancelEditMode()
                    Toast.makeText(requireContext(), "Сообщение удалено", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось удалить сообщение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pinMessage(message: com.sakovich.collegeapp.data.models.ChatMessage) {
        val user = currentUser ?: return
        if (!(user.isTeacher() || user.isHeadman())) return
        val room = currentRoom ?: return
        val moderatorName = user.fullName.ifBlank { user.email.substringBefore("@") }

        CoroutineScope(Dispatchers.IO).launch {
            val success = chatRepository.pinMessage(room.id, message, moderatorName)
            withContext(Dispatchers.Main) {
                if (!success) {
                    Toast.makeText(requireContext(), "Не удалось закрепить сообщение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleUnpin() {
        val user = currentUser ?: return
        if (!(user.isTeacher() || user.isHeadman())) return
        val room = currentRoom ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val success = chatRepository.clearPinnedMessage(room.id)
            withContext(Dispatchers.Main) {
                if (!success) {
                    Toast.makeText(requireContext(), "Не удалось снять закреп", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePinnedUi(room: ChatRoom) {
        if (room.pinnedMessageText.isBlank()) {
            pinnedCard.visibility = View.GONE
            return
        }
        pinnedCard.visibility = View.VISIBLE
        pinnedText.text = room.pinnedMessageText
        pinnedMeta.text = if (room.pinnedByName.isNotBlank()) {
            "Закрепил(а): ${room.pinnedByName}"
        } else {
            "Закрепленное сообщение"
        }
        val user = currentUser
        unpinButton.visibility = if (user != null && (user.isTeacher() || user.isHeadman())) View.VISIBLE else View.GONE
    }

    private fun isUsersGroupRoom(user: User, room: ChatRoom): Boolean {
        val userGroupKey = resolveGroupKey(user.groupId, user.groupName, user.group)
        val roomGroupKey = resolveGroupKey(room.groupId, room.groupName, room.groupId)
        return userGroupKey.isNotBlank() && userGroupKey == roomGroupKey
    }

    private fun resolveGroupKey(primary: String, secondary: String, fallback: String): String {
        val raw = primary.ifBlank { secondary.ifBlank { fallback } }.trim()
        return raw.lowercase()
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .trim('_')
    }

    private fun showChatActionsMenu() {
        val user = currentUser ?: return
        val room = currentRoom ?: return

        val canClear = user.isTeacher() && room.isGroupRoom && isUsersGroupRoom(user, room)

        val popup = PopupMenu(requireContext(), chatOverflowButton)

        if (canClear) {
            popup.menu.add(0, MENU_CLEAR, 0, "Очистить чат")
        }
        popup.menu.add(0, MENU_SEARCH, 1, "Поиск")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CLEAR -> {
                    confirmClearGroupChat()
                    true
                }
                MENU_SEARCH -> {
                    openSearchMode()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private companion object {
        private const val MENU_CLEAR = 1
        private const val MENU_SEARCH = 2
    }

    private fun openSearchMode() {
        if (isSearchMode) return
        isSearchMode = true

        chatHeaderRow.visibility = View.GONE
        chatSearchContainer.visibility = View.VISIBLE

        chatSearchInput.setText("")
        searchYear = null
        searchMonth = null
        searchDay = null

        applySearchFilter()
        chatSearchInput.requestFocus()
    }

    private fun closeSearchMode() {
        if (!isSearchMode) return
        isSearchMode = false

        chatSearchContainer.visibility = View.GONE
        chatHeaderRow.visibility = View.VISIBLE

        chatSearchInput.setText("")
        searchYear = null
        searchMonth = null
        searchDay = null

        messagesAdapter.submitList(currentMessages)
        if (currentMessages.isEmpty()) {
            showEmpty("Пока нет сообщений. Начните диалог.")
        } else {
            emptyText.visibility = View.GONE
            messagesRecyclerView.visibility = View.VISIBLE
            scrollChatToEnd()
        }
    }

    private fun scrollChatToEnd() {
        val lastAdapterIndex = maxOf(0, messagesAdapter.itemCount - 1)
        messagesRecyclerView.post {
            messagesRecyclerView.scrollToPosition(lastAdapterIndex)
        }
    }

    private fun messageMatchesQuery(msg: com.sakovich.collegeapp.data.models.ChatMessage, q: String): Boolean {
        if (q.isBlank()) return true
        if (msg.text.contains(q, ignoreCase = true)) return true
        if (msg.senderName.contains(q, ignoreCase = true)) return true
        if (msg.isSticker) {
            if (q.equals("стикер", ignoreCase = true)) return true
            if (msg.stickerId?.contains(q, ignoreCase = true) == true) return true
        }
        return false
    }

    private fun applySearchFilter() {
        if (!isSearchMode) {
            messagesAdapter.submitList(currentMessages)
            return
        }

        val q = chatSearchInput.text?.toString().orEmpty().trim()
        val filtered = currentMessages.filter { msg ->
            val textOk = messageMatchesQuery(msg, q)

            val dateOk = if (searchYear == null || searchMonth == null || searchDay == null) {
                true
            } else {
                val cal = Calendar.getInstance()
                cal.time = msg.createdAt
                cal.get(Calendar.YEAR) == searchYear &&
                    cal.get(Calendar.MONTH) == searchMonth &&
                    cal.get(Calendar.DAY_OF_MONTH) == searchDay
            }

            textOk && dateOk
        }

        messagesAdapter.submitList(filtered)

        if (filtered.isEmpty()) {
            if (currentMessages.isEmpty()) {
                showEmpty("Пока нет сообщений. Начните диалог.")
            } else {
                showEmpty("Нет сообщений по запросу")
            }
        } else {
            emptyText.visibility = View.GONE
            messagesRecyclerView.visibility = View.VISIBLE
            scrollChatToEnd()
        }
    }

    private fun showSearchDatePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                searchYear = year
                searchMonth = month
                searchDay = dayOfMonth
                applySearchFilter()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showChatSearchDialog() {
        val baseMessages = currentMessages

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_chat_search, null)
        val searchInput = dialogView.findViewById<TextInputEditText>(R.id.searchInput)

        searchInput.setText("")
        searchInput.requestFocus()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Поиск")
            .setView(dialogView)
            .setNegativeButton("Закрыть", null)
            .create()

        fun restore() {
            messagesAdapter.submitList(baseMessages)
            val lastAdapterIndex = maxOf(0, messagesAdapter.itemCount - 1)
            messagesRecyclerView.post {
                messagesRecyclerView.scrollToPosition(lastAdapterIndex)
            }
            if (baseMessages.isEmpty()) {
                showEmpty("Пока нет сообщений")
            } else {
                emptyText.visibility = View.GONE
                messagesRecyclerView.visibility = View.VISIBLE
            }
        }

        dialog.setOnDismissListener { restore() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty().trim()
                val filtered = if (q.isBlank()) {
                    baseMessages
                } else {
                    baseMessages.filter { msg -> messageMatchesQuery(msg, q) }
                }
                messagesAdapter.submitList(filtered)
                if (filtered.isEmpty()) {
                    emptyText.text = "Нет сообщений по запросу"
                    emptyText.visibility = View.VISIBLE
                    messagesRecyclerView.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    messagesRecyclerView.visibility = View.VISIBLE
                    val lastAdapterIndex = maxOf(0, messagesAdapter.itemCount - 1)
                    messagesRecyclerView.post {
                        messagesRecyclerView.scrollToPosition(lastAdapterIndex)
                    }
                }
            }
        })

        dialog.show()
    }

    private fun confirmClearGroupChat() {
        val room = currentRoom ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Очистить чат группы?")
            .setMessage("Все сообщения в этом групповом чате будут удалены без возможности восстановления.")
            .setPositiveButton("Очистить") { _, _ ->
                clearGroupChat(room.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearGroupChat(roomId: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val success = chatRepository.clearRoomMessages(roomId)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(requireContext(), "Чат очищен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось очистить чат", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEmpty(text: String) {
        emptyText.text = text
        emptyText.visibility = View.VISIBLE
        messagesRecyclerView.visibility = View.GONE
    }

    private suspend fun notifyGroupAboutNewChatMessage(room: ChatRoom, sender: User) {
        val groupName = room.groupName.ifBlank { sender.groupName.ifBlank { sender.group } }
        if (groupName.isBlank()) return
        val memberIds = userRepository.getGroupMemberUserIds(groupName, sender.id)
        if (memberIds.isEmpty()) return
        val senderLabel = sender.fullName.ifBlank { sender.email.substringBefore("@") }
        notificationRepository.createGroupNotification(
            studentIds = memberIds,
            title = "💬 Новое сообщение в чате",
            message = "$senderLabel отправил(а) сообщение в групповом чате",
            type = NotificationType.CHAT,
            relatedId = room.id,
            relatedType = "chat",
            excludeUserId = sender.id
        )
    }
}
