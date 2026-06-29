package com.sakovich.collegeapp.presentation.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Notification
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.utils.DrawableUtils
import java.text.SimpleDateFormat
import java.util.*

class NotificationsAdapter(
    private var notifications: List<Notification>,
    private val onNotificationClick: (Notification) -> Unit,
    private val onNotificationLongClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.titleText)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val typeIcon: TextView = itemView.findViewById(R.id.typeIcon)
        val unreadIndicator: View = itemView.findViewById(R.id.unreadIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        holder.titleText.text = notification.title
        holder.messageText.text = notification.message

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        holder.timeText.text = dateFormat.format(notification.createdAt)

        val (icon, color) = getTypeIconAndColor(notification.type)
        holder.typeIcon.text = icon
        DrawableUtils.setViewBackgroundColor(holder.typeIcon, color)

        holder.unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

        if (!notification.isRead) {
            holder.itemView.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.notification_unread_bg)
            )
        } else {
            holder.itemView.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.transparent)
            )
        }

        holder.itemView.setOnClickListener { onNotificationLongClick(notification) }
    }

    override fun getItemCount() = notifications.size

    fun updateNotifications(newNotifications: List<Notification>) {
        this.notifications = newNotifications
        notifyDataSetChanged()
    }

    private fun getTypeIconAndColor(type: NotificationType): Pair<String, Int> {
        return when (type) {
            NotificationType.GRADE -> Pair("📊", android.graphics.Color.parseColor("#8B5CF6"))
            NotificationType.ABSENCE -> Pair("📋", android.graphics.Color.parseColor("#F59E0B"))
            NotificationType.EVENT -> Pair("📅", android.graphics.Color.parseColor("#10B981"))
            NotificationType.SCHEDULE -> Pair("📚", android.graphics.Color.parseColor("#8B5CF6"))
            NotificationType.CHAT -> Pair("💬", android.graphics.Color.parseColor("#3B82F6"))
            NotificationType.SYSTEM -> Pair("🔔", android.graphics.Color.parseColor("#64748B"))
        }
    }
}
