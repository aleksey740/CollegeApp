package com.sakovich.collegeapp.presentation.chat

import android.content.Context
import android.text.util.Linkify
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.util.LinkifyCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.ImageViewCompat
import coil.load
import coil.size.Size
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatMessagesAdapter(
    context: Context,
    private val currentUserId: String,
    private val onMessageLongClick: (ChatMessage) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val appContext = context.applicationContext
    private val stickerPathById: Map<String, String> by lazy {
        runCatching { LocalStickerCatalog.load(appContext).stickers.associate { it.id to it.assetPath } }
            .getOrDefault(emptyMap())
    }

    private val items = mutableListOf<ListItem>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateLocale = Locale("ru", "RU")

    private sealed class ListItem {
        data class DateHeader(val label: String) : ListItem()
        data class MessageItem(val message: ChatMessage) : ListItem()
    }

    fun submitList(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(buildListWithDateHeaders(messages))
        notifyDataSetChanged()
    }

    private fun buildListWithDateHeaders(messages: List<ChatMessage>): List<ListItem> {
        if (messages.isEmpty()) return emptyList()
        val result = mutableListOf<ListItem>()
        var lastDayKey: Int? = null
        val cal = Calendar.getInstance()
        val nowCal = Calendar.getInstance()
        for (message in messages) {
            cal.time = message.createdAt
            val dayKey = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR)
            if (dayKey != lastDayKey) {
                lastDayKey = dayKey
                result.add(ListItem.DateHeader(formatDayLabel(message.createdAt, cal, nowCal)))
            }
            result.add(ListItem.MessageItem(message))
        }
        return result
    }

    private fun formatDayLabel(date: Date, msgCal: Calendar, nowCal: Calendar): String {
        val pattern = if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
            "d MMMM"
        } else {
            "d MMMM yyyy"
        }
        return SimpleDateFormat(pattern, dateLocale).format(date)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.DateHeader -> VIEW_TYPE_DATE
            is ListItem.MessageItem -> {
                val msg = (items[position] as ListItem.MessageItem).message
                if (msg.senderId == currentUserId) VIEW_TYPE_MINE else VIEW_TYPE_OTHER
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> {
                val view = inflater.inflate(R.layout.item_chat_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            VIEW_TYPE_MINE -> {
                val view = inflater.inflate(R.layout.item_chat_message_mine, parent, false)
                MineMessageViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_chat_message_other, parent, false)
                OtherMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.label)
            }
            is ListItem.MessageItem -> {
                val message = item.message
                val time = buildMeta(message)
                when (holder) {
                    is MineMessageViewHolder -> holder.bind(message, time, stickerPathById, onMessageLongClick)
                    is OtherMessageViewHolder -> holder.bind(message, time, stickerPathById, onMessageLongClick)
                }
            }
        }
    }

    private fun buildMeta(message: ChatMessage): String {
        val base = timeFormat.format(message.createdAt)
        return if (message.editedAt != null) "$base  •  изм." else base
    }

    private class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateHeaderText: TextView = itemView.findViewById(R.id.dateHeaderText)
        fun bind(label: String) {
            dateHeaderText.text = label
        }
    }

    private class MineMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageBubble: View = itemView.findViewById(R.id.messageBubble)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val stickerImage: ImageView = itemView.findViewById(R.id.stickerImage)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)
        private val bubblePadH = (12 * itemView.resources.displayMetrics.density).toInt()
        private val bubblePadTop = (10 * itemView.resources.displayMetrics.density).toInt()
        private val bubblePadBottom = (8 * itemView.resources.displayMetrics.density).toInt()

        fun bind(
            item: ChatMessage,
            time: String,
            stickerPathById: Map<String, String>,
            onLong: (ChatMessage) -> Unit
        ) {
            metaText.text = time
            val sid = item.stickerId?.trim().orEmpty()
            if (sid.isNotEmpty()) {
                messageBubble.background = null
                messageBubble.setPadding(0, 0, 0, 0)
                messageText.visibility = View.GONE
                stickerImage.visibility = View.VISIBLE
                stickerImage.clearColorFilter()
                ImageViewCompat.setImageTintList(stickerImage, null)
                val path = stickerPathById[sid]
                if (path != null) {
                    stickerImage.load("file:///android_asset/$path") {
                        crossfade(280)
                        size(Size.ORIGINAL)
                    }
                } else {
                    stickerImage.setImageDrawable(null)
                }
            } else {
                messageBubble.setBackgroundResource(R.drawable.bg_chat_message_mine)
                messageBubble.setPadding(bubblePadH, bubblePadTop, bubblePadH, bubblePadBottom)
                stickerImage.visibility = View.GONE
                stickerImage.setImageDrawable(null)
                messageText.visibility = View.VISIBLE
                messageText.text = item.text
                LinkifyCompat.addLinks(messageText, Linkify.WEB_URLS)
                messageText.linksClickable = true
                messageText.movementMethod = LinkMovementMethod.getInstance()
            }
            itemView.setOnClickListener { onLong(item) }
            messageText.setOnClickListener { onLong(item) }
            stickerImage.setOnClickListener { onLong(item) }
        }
    }

    private class OtherMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageBubble: View = itemView.findViewById(R.id.messageBubble)
        private val senderText: TextView = itemView.findViewById(R.id.senderText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val stickerImage: ImageView = itemView.findViewById(R.id.stickerImage)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)
        private val bubblePadH = (12 * itemView.resources.displayMetrics.density).toInt()
        private val bubblePadTop = (10 * itemView.resources.displayMetrics.density).toInt()
        private val bubblePadBottom = (8 * itemView.resources.displayMetrics.density).toInt()

        fun bind(
            item: ChatMessage,
            time: String,
            stickerPathById: Map<String, String>,
            onLong: (ChatMessage) -> Unit
        ) {
            senderText.text = item.senderName
            metaText.text = time
            val sid = item.stickerId?.trim().orEmpty()
            if (sid.isNotEmpty()) {
                messageBubble.background = null
                messageBubble.setPadding(0, 0, 0, 0)
                messageText.visibility = View.GONE
                stickerImage.visibility = View.VISIBLE
                stickerImage.clearColorFilter()
                ImageViewCompat.setImageTintList(stickerImage, null)
                val path = stickerPathById[sid]
                if (path != null) {
                    stickerImage.load("file:///android_asset/$path") {
                        crossfade(280)
                        size(Size.ORIGINAL)
                    }
                } else {
                    stickerImage.setImageDrawable(null)
                }
            } else {
                messageBubble.setBackgroundResource(R.drawable.bg_chat_message_other)
                messageBubble.setPadding(bubblePadH, bubblePadTop, bubblePadH, bubblePadBottom)
                stickerImage.visibility = View.GONE
                stickerImage.setImageDrawable(null)
                messageText.visibility = View.VISIBLE
                messageText.text = item.text
                LinkifyCompat.addLinks(messageText, Linkify.WEB_URLS)
                messageText.linksClickable = true
                messageText.movementMethod = LinkMovementMethod.getInstance()
            }
            itemView.setOnClickListener { onLong(item) }
            messageText.setOnClickListener { onLong(item) }
            stickerImage.setOnClickListener { onLong(item) }
        }
    }

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_MINE = 1
        private const val VIEW_TYPE_OTHER = 2
    }
}
