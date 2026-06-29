package com.sakovich.collegeapp.utils

import com.sakovich.collegeapp.data.models.User

object ContentOwnershipRules {

    fun canModify(currentUser: User, createdBy: String, createdByRole: String): Boolean {
        val ownerRole = createdByRole.ifBlank {
            if (createdBy.isNotBlank() && createdBy == currentUser.id) currentUser.role else ""
        }
        return when {
            currentUser.isTeacher() || currentUser.isAdmin() -> {
                when (ownerRole) {
                    "headman" -> true
                    "teacher", "admin" -> currentUser.isAdmin() || createdBy == currentUser.id || createdBy.isBlank()
                    else -> createdBy.isBlank() || createdBy == currentUser.id
                }
            }
            currentUser.isHeadman() -> {
                when (ownerRole) {
                    "teacher", "admin" -> false
                    else -> createdBy.isBlank() || createdBy == currentUser.id
                }
            }
            else -> false
        }
    }
}
