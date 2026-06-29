package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.repositories.UserRepository

object AbsenceFormat {

    fun creatorRoleLabel(role: String): String = when (role.lowercase()) {
        "teacher", "admin" -> "Куратор"
        "headman" -> "Староста"
        else -> ""
    }

    fun creatorLine(role: String, name: String): String {
        val label = creatorRoleLabel(role)
        val displayName = name.trim().ifBlank { "—" }
        return if (label.isNotEmpty()) "$label: $displayName" else displayName
    }

    fun creatorLineWithIcon(role: String, name: String): String =
        "✍️ ${creatorLine(role, name)}"

    suspend fun enrichAbsences(
        absences: List<Absence>,
        userRepository: UserRepository
    ): List<Absence> {
        val ids = absences.map { it.createdBy }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return absences

        val namesById = mutableMapOf<String, String>()
        for (id in ids) {
            userRepository.getUser(id)?.fullName?.takeIf { it.isNotBlank() }?.let { namesById[id] = it }
        }
        if (namesById.isEmpty()) return absences

        return absences.map { absence ->
            val stored = absence.createdByName.trim()
            val needsResolve = stored.isBlank() || stored.contains('@')
            if (!needsResolve || absence.createdBy.isBlank()) return@map absence
            val resolved = namesById[absence.createdBy] ?: return@map absence
            absence.copy(createdByName = resolved)
        }
    }
}
