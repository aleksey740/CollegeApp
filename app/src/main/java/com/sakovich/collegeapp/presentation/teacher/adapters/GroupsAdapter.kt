package com.sakovich.collegeapp.presentation.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Group

class GroupsAdapter(
    private val groups: List<Group>,
    private val onGroupClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val groupNameText: TextView = itemView.findViewById(R.id.groupNameText)
        val studentCountText: TextView = itemView.findViewById(R.id.studentCountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]

        holder.groupNameText.text = group.name
        holder.studentCountText.text = "Студентов: ${group.studentCount}"

        holder.itemView.setOnClickListener {
            onGroupClick(group)
        }
    }

    override fun getItemCount() = groups.size
}