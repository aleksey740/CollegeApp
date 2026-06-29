package com.sakovich.collegeapp.utils

object PersonNameFormat {

    /** «Иванов Иван Иванович» → «Иванов И. И.» */
    fun shortFio(fullName: String): String {
        val clean = fullName
            .replace(Regex("\\s*\\(Староста\\)\\s*", RegexOption.IGNORE_CASE), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return ""
        val parts = clean.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 3 -> "${parts[0]} ${parts[1].first()}. ${parts[2].first()}."
            parts.size == 2 -> "${parts[0]} ${parts[1].first()}."
            else -> clean
        }
    }
}
