package com.sakovich.collegeapp.presentation.teacher.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.Group

class GroupsAdapter(
    private val groups: List<Group>,
    private val onGroupClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    private val colors = listOf(
        "#8B5CF6",
        "#8B5CF6",
        "#10B981",
        "#F59E0B",
        "#EF4444",
        "#EC4899",
        "#06B6D4"
    )

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorBar: View = itemView.findViewById(R.id.colorBar)
        val groupNameText: TextView = itemView.findViewById(R.id.groupNameText)
        val studentCountText: TextView = itemView.findViewById(R.id.studentCountText)
        val headmanLayout: LinearLayout = itemView.findViewById(R.id.headmanLayout)
        val headmanText: TextView = itemView.findViewById(R.id.headmanText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]

        holder.groupNameText.text = group.name

        val count = group.studentCount
        val studentsWord = when {
            count % 100 in 11..19 -> "учащихся"
            count % 10 == 1 -> "учащийся"
            count % 10 in 2..4 -> "учащегося"
            else -> "учащихся"
        }
        holder.studentCountText.text = "$count $studentsWord"

        val colorIndex = position % colors.size
        holder.colorBar.setBackgroundColor(Color.parseColor(colors[colorIndex]))

        if (group.headmanName.isNotEmpty()) {
            holder.headmanLayout.visibility = View.VISIBLE
            holder.headmanText.text = "Староста"
        } else {
            holder.headmanLayout.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onGroupClick(group)
        }
    }

    override fun getItemCount() = groups.size
}
