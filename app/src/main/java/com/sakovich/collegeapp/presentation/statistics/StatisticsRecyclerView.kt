package com.sakovich.collegeapp.presentation.statistics

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.setupStatisticsListInScrollView() {
    layoutManager = LinearLayoutManager(context)
    isNestedScrollingEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
}

fun RecyclerView.updateStatisticsListHeight() {
    val listAdapter = adapter ?: return
    if (listAdapter.itemCount == 0) {
        layoutParams = layoutParams.apply { height = 0 }
        requestLayout()
        return
    }
    val widthForMeasure = (width - paddingLeft - paddingRight).coerceAtLeast(0)
    val widthSpec = View.MeasureSpec.makeMeasureSpec(
        widthForMeasure,
        View.MeasureSpec.EXACTLY
    )
    var totalHeight = paddingTop + paddingBottom
    for (i in 0 until listAdapter.itemCount) {
        val viewType = listAdapter.getItemViewType(i)
        val holder = listAdapter.createViewHolder(this, viewType)
        listAdapter.bindViewHolder(holder, i)
        holder.itemView.measure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
        val marginVert = lp?.let { it.topMargin + it.bottomMargin } ?: 0
        totalHeight += holder.itemView.measuredHeight + marginVert
    }
    layoutParams = layoutParams.apply { height = totalHeight }
    requestLayout()
}
