package com.sakovich.collegeapp.presentation.clubs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Club
import com.sakovich.collegeapp.data.models.ClubType

class ClubsAdapter(
    private var items: List<Club>,
    private val onClick: (Club) -> Unit
) : RecyclerView.Adapter<ClubsAdapter.ClubViewHolder>() {

    class ClubViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeIndicator: View = view.findViewById(R.id.typeIndicator)
        val typeBadgeText: TextView = view.findViewById(R.id.typeBadgeText)
        val participantsText: TextView = view.findViewById(R.id.participantsText)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val leaderText: TextView = view.findViewById(R.id.leaderText)
        val scheduleText: TextView = view.findViewById(R.id.scheduleText)
        val locationText: TextView = view.findViewById(R.id.locationText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClubViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_club, parent, false)
        return ClubViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClubViewHolder, position: Int) {
        val club = items[position]
        val (indicatorColor, badgeBg) = when (club.type) {
            ClubType.CLUB -> Pair(Color.parseColor("#3B82F6"), R.drawable.bg_role_badge_blue)
            ClubType.SECTION -> Pair(Color.parseColor("#10B981"), R.drawable.bg_badge_section)
            ClubType.ELECTIVE -> Pair(Color.parseColor("#8B5CF6"), R.drawable.bg_role_badge_purple)
        }
        holder.typeIndicator.setBackgroundColor(indicatorColor)
        holder.typeBadgeText.setBackgroundResource(badgeBg)
        holder.typeBadgeText.text = when (club.type) {
            ClubType.CLUB -> "Кружок"
            ClubType.SECTION -> "Секция"
            ClubType.ELECTIVE -> "Факультатив"
        }
        holder.participantsText.text = "${club.participantIds.size}/${club.maxParticipants}"
        holder.nameText.text = club.name
        holder.leaderText.text = "👨‍🏫 ${club.teacherName.ifBlank { "—" }}"
        holder.scheduleText.text = club.schedule.ifBlank { "—" }
        holder.locationText.text = "🏫 ${club.location.ifBlank { "—" }}"
        holder.itemView.setOnClickListener { onClick(club) }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<Club>) {
        items = newItems
        notifyDataSetChanged()
    }
}
