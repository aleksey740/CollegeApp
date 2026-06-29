package com.sakovich.collegeapp.presentation.clubs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.utils.DrawableUtils

data class ClubParticipantRow(
    val id: String,
    val displayName: String,
    val isHeadman: Boolean = false
)

class ClubParticipantRowAdapter(
    private val items: List<ClubParticipantRow>,
    private val showActionHint: Boolean,
    private val onItemClick: ((ClubParticipantRow) -> Unit)? = null
) : RecyclerView.Adapter<ClubParticipantRowAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarText: TextView = itemView.findViewById(R.id.avatarText)
        val nameText: TextView = itemView.findViewById(R.id.nameText)
        val headmanBadge: TextView = itemView.findViewById(R.id.headmanBadge)
        val actionHint: TextView = itemView.findViewById(R.id.actionHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_club_participant_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.displayName
        holder.avatarText.text = ClubParticipantUi.initials(item.displayName)
        DrawableUtils.setViewBackgroundColorHex(
            holder.avatarText,
            DrawableUtils.colorForName(item.displayName)
        )
        holder.headmanBadge.visibility = if (item.isHeadman) View.VISIBLE else View.GONE
        holder.actionHint.visibility = if (showActionHint && onItemClick != null) View.VISIBLE else View.GONE

        val clickable = onItemClick != null
        holder.itemView.isClickable = clickable
        holder.itemView.isFocusable = clickable
        if (clickable) {
            holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = items.size
}

object ClubParticipantUi {

    private val headmanRegex = Regex("\\s*\\(Староста\\)\\s*", RegexOption.IGNORE_CASE)

    fun formatDisplayName(fullName: String): String =
        fullName.replace(headmanRegex, " ").trim().replace(Regex("\\s+"), " ")

    fun isHeadman(fullName: String): Boolean =
        headmanRegex.containsMatchIn(fullName)

    fun initials(displayName: String): String {
        val parts = displayName.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    fun rowsFromIdsAndNames(ids: List<String>, names: List<String>): List<ClubParticipantRow> {
        return ids.zip(names)
            .map { (id, rawName) ->
                val raw = rawName.ifBlank { "—" }
                ClubParticipantRow(
                    id = id,
                    displayName = if (raw == "—") raw else formatDisplayName(raw),
                    isHeadman = isHeadman(rawName)
                )
            }
            .sortedBy { if (it.displayName == "—") "яяя" else it.displayName.lowercase() }
    }
}
