package com.sakovich.collegeapp.presentation.nutrition

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakovich.collegeapp.R
import com.sakovich.collegeapp.data.models.MealSubscription

class NutritionParticipantsAdapter(
    private var items: List<MealSubscription>
) : RecyclerView.Adapter<NutritionParticipantsAdapter.ParticipantViewHolder>() {

    class ParticipantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal_participant, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.userName
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<MealSubscription>) {
        items = newItems
        notifyDataSetChanged()
    }
}
