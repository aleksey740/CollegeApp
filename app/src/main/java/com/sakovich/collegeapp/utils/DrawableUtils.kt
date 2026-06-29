package com.sakovich.collegeapp.utils

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat

object DrawableUtils {

    val avatarPalette: List<String> = listOf(
        "#8B5CF6",
        "#A78BFA",
        "#10B981",
        "#F59E0B",
        "#EF4444",
        "#EC4899",
        "#06B6D4"
    )

    fun colorForName(name: String, palette: List<String> = avatarPalette): String {
        if (palette.isEmpty()) return "#8B5CF6"
        val index = (name.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }

  /** Меняет цвет фона без изменения общего drawable из XML (constant state). */
    fun setViewBackgroundColor(view: View, @ColorInt color: Int) {
        val base = view.background ?: return
        val drawable = base.mutate()
        when (drawable) {
            is GradientDrawable -> drawable.setColor(color)
            else -> DrawableCompat.setTint(drawable, color)
        }
        view.background = drawable
    }

    fun setViewBackgroundColorHex(view: View, hex: String) {
        setViewBackgroundColor(view, android.graphics.Color.parseColor(hex))
    }
}
