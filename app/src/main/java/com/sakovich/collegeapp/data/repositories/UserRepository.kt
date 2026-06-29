package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.sakovich.collegeapp.data.models.User
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    suspend fun getUser(userId: String): User? {
        return try {
            val document = usersCollection.document(userId).get().await()
            mapDocumentToUser(document)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getGroupMemberUserIds(groupName: String, excludeUserId: String = ""): List<String> {
        if (groupName.isBlank()) return emptyList()
        return try {
            val snapshot = usersCollection.whereEqualTo("groupName", groupName).get().await()
            snapshot.documents.map { it.id }.filter { it.isNotBlank() && it != excludeUserId }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun updateUser(userId: String, user: User): Boolean {
        return try {
            usersCollection.document(userId).update(user.toMap()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updatePersonalInfo(
        userId: String,
        address: String,
        birthDate: String,
        phone: String,
        parentName: String,
        parentPhone: String,
        parentName2: String,
        parentPhone2: String,
        livesInDormitory: Boolean = false,
        isDisabled: Boolean = false,
        isLargeFamily: Boolean = false,
        fundingType: String = "",
        isLowIncome: Boolean = false,
        isOrphan: Boolean = false,
        isNonResident: Boolean = false
    ): Boolean {
        return try {
            val updates = mapOf(
                "address" to address,
                "birthDate" to birthDate,
                "phone" to phone,
                "parentName" to parentName,
                "parentPhone" to parentPhone,
                "parentName2" to parentName2,
                "parentPhone2" to parentPhone2,
                "livesInDormitory" to livesInDormitory,
                "isDisabled" to isDisabled,
                "isLargeFamily" to isLargeFamily,
                "fundingType" to fundingType,
                "isLowIncome" to isLowIncome,
                "isOrphan" to isOrphan,
                "isNonResident" to isNonResident
            )
            usersCollection.document(userId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deletePersonalInfo(userId: String): Boolean {
        return try {
            val updates = mapOf(
                "address" to "",
                "birthDate" to "",
                "phone" to "",
                "parentName" to "",
                "parentPhone" to "",
                "parentName2" to "",
                "parentPhone2" to "",
                "livesInDormitory" to false,
                "isDisabled" to false,
                "isLargeFamily" to false,
                "fundingType" to "",
                "isLowIncome" to false,
                "isOrphan" to false,
                "isNonResident" to false
            )
            usersCollection.document(userId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getTeachers(): List<User> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.documents.mapNotNull { document ->
                if (!document.exists()) return@mapNotNull null
                val role = document.getString("role") ?: ""
                if (role != "teacher") return@mapNotNull null
                User(
                    id = document.id,
                    email = document.getString("email") ?: "",
                    fullName = document.getString("fullName") ?: "",
                    role = role,
                    group = document.getString("group") ?: "",
                    groupId = document.getString("groupId") ?: "",
                    groupName = document.getString("groupName") ?: "",
                    address = document.getString("address") ?: "",
                    birthDate = document.getString("birthDate") ?: "",
                    phone = document.getString("phone") ?: "",
                    parentName = document.getString("parentName") ?: "",
                    parentPhone = document.getString("parentPhone") ?: "",
                    parentName2 = document.getString("parentName2") ?: "",
                    parentPhone2 = document.getString("parentPhone2") ?: "",
                    mealAutoPlanEnabled = document.getBoolean("mealAutoPlanEnabled") ?: false,
                    mealAutoPlanLastAppliedWeek = document.getString("mealAutoPlanLastAppliedWeek") ?: "",
                    livesInDormitory = document.getBoolean("livesInDormitory") ?: false,
                    isDisabled = document.getBoolean("isDisabled") ?: false,
                    isLargeFamily = document.getBoolean("isLargeFamily") ?: false,
                    fundingType = document.getString("fundingType") ?: "",
                    isLowIncome = document.getBoolean("isLowIncome") ?: false,
                    isOrphan = document.getBoolean("isOrphan") ?: false,
                    isNonResident = document.getBoolean("isNonResident") ?: false
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStudentsByGroupName(groupName: String): List<User> {
        if (groupName.isBlank()) return emptyList()
        return try {
            val snapshot = usersCollection
                .whereIn("role", listOf("student", "headman"))
                .whereEqualTo("groupName", groupName)
                .get()
                .await()
            snapshot.documents.mapNotNull { mapDocumentToUser(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Учащиеся группы по названию и document id (как в расписании и журнале). */
    suspend fun getStudentsForGroup(groupName: String, groupId: String = ""): List<User> {
        val byName = if (groupName.isNotBlank()) getStudentsByGroupName(groupName) else emptyList()
        val resolvedId = groupId.ifBlank {
            if (groupName.isBlank()) "" else GroupRepository.groupNameToDocumentId(groupName)
        }
        val byId = if (resolvedId.isNotBlank()) getStudentsByGroupId(resolvedId) else emptyList()
        return (byName + byId).distinctBy { it.id }
    }

    suspend fun getStudentsByGroupId(groupId: String): List<User> {
        if (groupId.isBlank()) return emptyList()
        return try {
            val snapshot = usersCollection
                .whereIn("role", listOf("student", "headman"))
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            snapshot.documents.mapNotNull { mapDocumentToUser(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAllUsers(): List<User> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.documents.mapNotNull { mapDocumentToUser(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateMealAutoPlan(
        userId: String,
        enabled: Boolean,
        lastAppliedWeek: String
    ): Boolean {
        return try {
            usersCollection.document(userId).update(
                mapOf(
                    "mealAutoPlanEnabled" to enabled,
                    "mealAutoPlanLastAppliedWeek" to lastAppliedWeek
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mapDocumentToUser(document: DocumentSnapshot): User? {
        if (!document.exists()) return null
        return User(
            id = document.id,
            email = document.getString("email") ?: "",
            fullName = document.getString("fullName") ?: "",
            role = document.getString("role") ?: "student",
            gender = document.getString("gender") ?: User.GENDER_MALE,
            group = document.getString("group") ?: "",
            groupId = document.getString("groupId") ?: "",
            groupName = document.getString("groupName") ?: "",
            address = document.getString("address") ?: "",
            birthDate = document.getString("birthDate") ?: "",
            phone = document.getString("phone") ?: "",
            parentName = document.getString("parentName") ?: "",
            parentPhone = document.getString("parentPhone") ?: "",
            parentName2 = document.getString("parentName2") ?: "",
            parentPhone2 = document.getString("parentPhone2") ?: "",
            mealAutoPlanEnabled = document.getBoolean("mealAutoPlanEnabled") ?: false,
            mealAutoPlanLastAppliedWeek = document.getString("mealAutoPlanLastAppliedWeek") ?: "",
            livesInDormitory = document.getBoolean("livesInDormitory") ?: false,
            isDisabled = document.getBoolean("isDisabled") ?: false,
            isLargeFamily = document.getBoolean("isLargeFamily") ?: false,
            fundingType = document.getString("fundingType") ?: "",
            isLowIncome = document.getBoolean("isLowIncome") ?: false,
            isOrphan = document.getBoolean("isOrphan") ?: false,
            isNonResident = document.getBoolean("isNonResident") ?: false
        )
    }
}
