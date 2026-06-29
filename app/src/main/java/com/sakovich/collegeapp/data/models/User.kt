package com.sakovich.collegeapp.data.models

data class User(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val role: String = "student",
    val gender: String = GENDER_MALE,
    val group: String = "",
    val groupId: String = "",
    val groupName: String = "",

    val address: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val parentName2: String = "",
    val parentPhone2: String = "",
    val mealAutoPlanEnabled: Boolean = false,
    val mealAutoPlanLastAppliedWeek: String = "",

    val livesInDormitory: Boolean = false,
    val isDisabled: Boolean = false,
    val isLargeFamily: Boolean = false,
    val fundingType: String = "",
    val isLowIncome: Boolean = false,
    val isOrphan: Boolean = false,
    val isNonResident: Boolean = false
) {
    fun isTeacher(): Boolean = role == "teacher"
    fun isHeadman(): Boolean = role == "headman"
    fun isStudent(): Boolean = role == "student"
    fun isAdmin(): Boolean = role == "admin"
    fun isFemaleGender(): Boolean = gender == GENDER_FEMALE

    fun canEditEvents(): Boolean = isTeacher() || isHeadman()

    fun roleBadgeLabel(): String = when {
        isTeacher() || isAdmin() -> "👨‍🏫 Куратор"
        isHeadman() -> "⭐ Староста"
        isFemaleGender() -> "🎓 Учащаяся"
        else -> "🎓 Учащийся"
    }

    fun roleBadgeColorHex(): String = when {
        isTeacher() || isHeadman() || isAdmin() -> "#8B5CF6"
        else -> "#10B981"
    }

    companion object {
        const val GENDER_MALE = "male"
        const val GENDER_FEMALE = "female"
    }

    fun hasPersonalInfo(): Boolean {
        return address.isNotEmpty() || birthDate.isNotEmpty() || phone.isNotEmpty() ||
                parentName.isNotEmpty() || parentPhone.isNotEmpty() ||
                parentName2.isNotEmpty() || parentPhone2.isNotEmpty() ||
                livesInDormitory || isDisabled || isLargeFamily || fundingType.isNotEmpty() ||
                isLowIncome || isOrphan || isNonResident
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "email" to email,
            "fullName" to fullName,
            "role" to role,
            "gender" to gender,
            "group" to group,
            "groupId" to groupId,
            "groupName" to groupName,
            "address" to address,
            "birthDate" to birthDate,
            "phone" to phone,
            "parentName" to parentName,
            "parentPhone" to parentPhone,
            "parentName2" to parentName2,
            "parentPhone2" to parentPhone2,
            "mealAutoPlanEnabled" to mealAutoPlanEnabled,
            "mealAutoPlanLastAppliedWeek" to mealAutoPlanLastAppliedWeek,
            "livesInDormitory" to livesInDormitory,
            "isDisabled" to isDisabled,
            "isLargeFamily" to isLargeFamily,
            "fundingType" to fundingType,
            "isLowIncome" to isLowIncome,
            "isOrphan" to isOrphan,
            "isNonResident" to isNonResident
        )
    }
}
