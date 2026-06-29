package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.sakovich.collegeapp.data.models.AdminSemesterTemplate
import com.sakovich.collegeapp.data.models.CatalogTeacher
import com.sakovich.collegeapp.data.models.GroupLimits
import com.sakovich.collegeapp.data.models.SubjectForGroup
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.utils.SemesterStatsHelper
import com.sakovich.collegeapp.utils.SubjectSemesterFilterHelper
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale

class AdminRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val groupsCollection = db.collection("admin_groups")
    private val subjectsCollection = db.collection("admin_subjects")
    private val semestersCollection = db.collection("admin_semesters")
    private val teachersCollection = db.collection("admin_teachers")
    private val groupLimitsCollection = db.collection("group_limits")

    suspend fun getAllUsers(): List<User> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.documents.mapNotNull { doc ->
                if (!doc.exists()) return@mapNotNull null
                User(
                    id = doc.id,
                    email = doc.getString("email") ?: "",
                    fullName = doc.getString("fullName") ?: "",
                    role = doc.getString("role") ?: "student",
                    group = doc.getString("group") ?: "",
                    groupId = doc.getString("groupId") ?: "",
                    groupName = doc.getString("groupName") ?: "",
                    address = doc.getString("address") ?: "",
                    birthDate = doc.getString("birthDate") ?: "",
                    phone = doc.getString("phone") ?: "",
                    parentName = doc.getString("parentName") ?: "",
                    parentPhone = doc.getString("parentPhone") ?: "",
                    mealAutoPlanEnabled = doc.getBoolean("mealAutoPlanEnabled") ?: false,
                    mealAutoPlanLastAppliedWeek = doc.getString("mealAutoPlanLastAppliedWeek") ?: "",
                    livesInDormitory = doc.getBoolean("livesInDormitory") ?: false,
                    isDisabled = doc.getBoolean("isDisabled") ?: false,
                    isLargeFamily = doc.getBoolean("isLargeFamily") ?: false,
                    fundingType = doc.getString("fundingType") ?: "",
                    isLowIncome = doc.getBoolean("isLowIncome") ?: false,
                    isOrphan = doc.getBoolean("isOrphan") ?: false,
                    isNonResident = doc.getBoolean("isNonResident") ?: false
                )
            }.sortedBy { it.fullName.lowercase(Locale.getDefault()) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun updateUserRoleAndGroup(
        userId: String,
        role: String,
        groupName: String
    ): Boolean {
        return try {
            val safeGroupName = groupName.trim()
            val groupId = GroupRepository.groupNameToDocumentId(safeGroupName)
            usersCollection.document(userId).update(
                mapOf(
                    "role" to role,
                    "group" to safeGroupName,
                    "groupId" to groupId,
                    "groupName" to safeGroupName
                )
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getGroupLimits(groupId: String): GroupLimits? {
        if (groupId.isBlank()) return null
        return try {
            val doc = groupLimitsCollection.document(groupId).get().await()
            if (!doc.exists()) return null
            val teacherLimit = doc.getLong("teacherLimit")?.toInt()
            val studentLimit = doc.getLong("studentLimit")?.toInt()
            val headmanLimit = doc.getLong("headmanLimit")?.toInt()
            GroupLimits(
                teacherLimit = teacherLimit,
                studentLimit = studentLimit,
                headmanLimit = headmanLimit
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setGroupLimits(groupId: String, limits: GroupLimits?): Boolean {
        if (groupId.isBlank()) return false
        return try {
            val docRef = groupLimitsCollection.document(groupId)
            if (limits == null) {
                docRef.delete().await()
            } else {
                val data = hashMapOf<String, Any>()
                limits.teacherLimit?.let { data["teacherLimit"] = it.toLong() }
                limits.studentLimit?.let { data["studentLimit"] = it.toLong() }
                limits.headmanLimit?.let { data["headmanLimit"] = it.toLong() }
                if (data.isEmpty()) {
                    docRef.delete().await()
                } else {
                    docRef.set(data).await()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun userDocumentBelongsToGroup(doc: DocumentSnapshot, targetGroupId: String): Boolean {
        if (targetGroupId.isBlank()) return false
        val storedId = doc.getString("groupId")?.trim().orEmpty()
        val name = doc.getString("groupName")?.trim().orEmpty()
            .ifBlank { doc.getString("group")?.trim().orEmpty() }
        val computedId = if (name.isNotBlank()) GroupRepository.groupNameToDocumentId(name) else ""
        if (storedId.isNotBlank() && storedId == targetGroupId) return true
        if (computedId.isNotBlank() && computedId == targetGroupId) return true
        return false
    }

    suspend fun countRoleInGroup(groupId: String, role: String): Int {
        if (groupId.isBlank()) return 0
        return try {
            val snapshot = usersCollection
                .whereEqualTo("role", role)
                .get()
                .await()

            snapshot.documents.count { doc -> userDocumentBelongsToGroup(doc, groupId) }
        } catch (_: Exception) {
            0
        }
    }

    suspend fun canRegisterToGroup(role: String, groupId: String): Boolean {
        if (groupId.isBlank()) return true

        val limits = getGroupLimits(groupId)
        if (limits == null) return true

        val teacherCount = countRoleInGroup(groupId, "teacher")
        val studentCount = countRoleInGroup(groupId, "student")
        val headmanCount = countRoleInGroup(groupId, "headman")

        fun isAllowed(limit: Int?, current: Int): Boolean {
            return limit?.let { current < it } ?: true
        }

        return when (role) {
            "teacher" -> isAllowed(limits.teacherLimit, teacherCount)
            "headman" -> {
                val studentTotal = studentCount + headmanCount
                val okHeadman = isAllowed(limits.headmanLimit, headmanCount)
                val okStudents = isAllowed(limits.studentLimit, studentTotal)
                okHeadman && okStudents
            }
            "student" -> isAllowed(limits.studentLimit, studentCount + headmanCount)
            else -> true
        }
    }

    suspend fun canUpdateUserRoleAndGroup(
        userId: String,
        currentRole: String,
        currentGroupId: String,
        newRole: String,
        newGroupId: String
    ): Boolean {
        if (newGroupId.isBlank()) return true
        if (newRole == "admin") return false
        val limits = getGroupLimits(newGroupId) ?: return true

        val teacherCount = countRoleInGroup(newGroupId, "teacher")
        val studentCount = countRoleInGroup(newGroupId, "student")
        val headmanCount = countRoleInGroup(newGroupId, "headman")

        val adjustedTeacherCount = if (currentGroupId == newGroupId && currentRole == "teacher") teacherCount - 1 else teacherCount
        val adjustedStudentCount = if (currentGroupId == newGroupId && currentRole == "student") studentCount - 1 else studentCount
        val adjustedHeadmanCount = if (currentGroupId == newGroupId && currentRole == "headman") headmanCount - 1 else headmanCount

        fun isAllowed(limit: Int?, current: Int): Boolean {
            return limit?.let { current < it } ?: true
        }

        return when (newRole) {
            "teacher" -> isAllowed(limits.teacherLimit, adjustedTeacherCount)
            "student" -> isAllowed(limits.studentLimit, adjustedStudentCount + adjustedHeadmanCount)
            "headman" -> {
                val studentTotal = adjustedStudentCount + adjustedHeadmanCount
                val okHeadman = isAllowed(limits.headmanLimit, adjustedHeadmanCount)
                val okStudents = isAllowed(limits.studentLimit, studentTotal)
                okHeadman && okStudents
            }
            else -> true
        }
    }

    suspend fun getCatalogGroups(): List<String> {
        val fromCatalog = try {
            groupsCollection.get().await().documents
                .mapNotNull { it.getString("name")?.trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
        val fromUsers = try {
            usersCollection.get().await().documents
                .mapNotNull { it.getString("groupName")?.trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
        return (fromCatalog + fromUsers).distinct().sorted()
    }

    suspend fun addGroup(groupName: String): Boolean {
        return try {
            val name = groupName.trim()
            if (name.isBlank()) return false
            groupsCollection.document(normalizeId(name)).set(mapOf("name" to name)).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun removeGroup(groupName: String): Boolean {
        return try {
            groupsCollection.document(normalizeId(groupName)).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseSubjectSemesters(doc: DocumentSnapshot): Pair<List<String>, List<String>> {
        @Suppress("UNCHECKED_CAST")
        val ids = (doc.get("semesterIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        @Suppress("UNCHECKED_CAST")
        val names = (doc.get("semesterNames") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        if (ids.isNotEmpty()) {
            val paired = ids.zip(names.ifEmpty { List(ids.size) { "" } })
            return paired.map { it.first } to paired.map { it.second }
        }
        val legacyId = doc.getString("semesterId")?.trim().orEmpty()
        val legacyName = doc.getString("semesterName")?.trim().orEmpty()
        return if (legacyId.isNotBlank()) {
            listOf(legacyId) to listOf(legacyName.ifBlank { legacyId })
        } else {
            emptyList<String>() to emptyList()
        }
    }

    private fun subjectSemestersPayload(ids: List<String>, names: List<String>): Map<String, Any> =
        mapOf(
            "semesterIds" to ids,
            "semesterNames" to names,
            "semesterId" to "",
            "semesterName" to ""
        )

    suspend fun getSubjects(): List<SubjectForGroup> {
        return try {
            subjectsCollection.get().await().documents
                .mapNotNull { doc ->
                    val name = doc.getString("name")?.trim() ?: return@mapNotNull null
                    val groupId = doc.getString("groupId") ?: return@mapNotNull null
                    val groupName = doc.getString("groupName") ?: ""
                    if (name.isBlank() || groupId.isBlank()) return@mapNotNull null
                    val (semesterIds, semesterNames) = parseSubjectSemesters(doc)
                    SubjectForGroup(
                        name = name,
                        groupId = groupId,
                        groupName = groupName,
                        semesterIds = semesterIds,
                        semesterNames = semesterNames
                    )
                }
                .sortedWith(compareBy({ it.groupName }, { it.name }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getSubjectsForGroup(groupId: String): List<String> {
        if (groupId.isBlank()) return emptyList()
        return getSubjects()
            .filter { it.groupId == groupId }
            .map { it.name }
            .distinct()
            .sorted()
    }

    suspend fun getSubjectNamesForGroupInDateRange(
        groupId: String,
        rangeStart: Date?,
        rangeEnd: Date?
    ): List<String> {
        if (groupId.isBlank()) return emptyList()
        val subjects = getSubjects().filter { it.groupId == groupId }
        if (rangeStart == null && rangeEnd == null) {
            return subjects.map { it.name }.distinct().sorted()
        }
        val semesters = getSemestersForGroup(groupId)
        return SubjectSemesterFilterHelper.filterSubjectNames(subjects, semesters, rangeStart, rangeEnd)
    }

    suspend fun getSubjectNamesForGroupOnDate(groupId: String, dateStr: String): List<String> {
        if (groupId.isBlank()) return emptyList()
        val date = SemesterStatsHelper.parseDate(dateStr) ?: return getSubjectsForGroup(groupId)
        return getSubjectNamesForGroupInDateRange(groupId, date, date)
    }

    suspend fun addSubject(subjectName: String, groupId: String, groupName: String): Boolean {
        return try {
            val name = subjectName.trim()
            if (name.isBlank() || groupId.isBlank()) return false
            val docId = normalizeId("${name}_$groupId")
            subjectsCollection.document(docId).set(
                mapOf(
                    "name" to name,
                    "groupId" to groupId,
                    "groupName" to groupName
                ) + subjectSemestersPayload(emptyList(), emptyList())
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    enum class AssignSubjectSemesterResult {
        SUCCESS,
        SUBJECT_NOT_FOUND,
        DUPLICATE,
        ERROR
    }

    suspend fun unassignSubjectSemester(
        subjectName: String,
        groupId: String,
        semesterId: String
    ): AssignSubjectSemesterResult {
        val name = subjectName.trim()
        if (name.isBlank() || groupId.isBlank() || semesterId.isBlank()) {
            return AssignSubjectSemesterResult.ERROR
        }
        val docId = normalizeId("${name}_$groupId")
        return try {
            val doc = subjectsCollection.document(docId).get().await()
            if (!doc.exists()) return AssignSubjectSemesterResult.SUBJECT_NOT_FOUND
            val (ids, names) = parseSubjectSemesters(doc)
            if (semesterId !in ids) return AssignSubjectSemesterResult.ERROR
            val paired = ids.zip(names).filter { it.first != semesterId }
            subjectsCollection.document(docId).update(
                subjectSemestersPayload(
                    paired.map { it.first },
                    paired.map { it.second }
                )
            ).await()
            AssignSubjectSemesterResult.SUCCESS
        } catch (_: Exception) {
            AssignSubjectSemesterResult.ERROR
        }
    }

    suspend fun assignSubjectSemester(
        subjectName: String,
        groupId: String,
        semester: AdminSemesterTemplate
    ): AssignSubjectSemesterResult {
        val name = subjectName.trim()
        if (name.isBlank() || groupId.isBlank() || semester.id.isBlank()) return AssignSubjectSemesterResult.ERROR
        if (semester.groupId.isNotBlank() && semester.groupId != groupId) {
            return AssignSubjectSemesterResult.ERROR
        }
        val docId = normalizeId("${name}_$groupId")
        return try {
            val doc = subjectsCollection.document(docId).get().await()
            if (!doc.exists()) return AssignSubjectSemesterResult.SUBJECT_NOT_FOUND
            val (ids, names) = parseSubjectSemesters(doc)
            if (semester.id in ids) return AssignSubjectSemesterResult.DUPLICATE
            val newIds = ids + semester.id
            val newNames = names + semester.name.trim()
            subjectsCollection.document(docId).update(subjectSemestersPayload(newIds, newNames)).await()
            AssignSubjectSemesterResult.SUCCESS
        } catch (_: Exception) {
            AssignSubjectSemesterResult.ERROR
        }
    }

    suspend fun removeSubject(subjectName: String, groupId: String): Boolean {
        return try {
            val docId = normalizeId("${subjectName.trim()}_$groupId")
            subjectsCollection.document(docId).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateSubject(oldName: String, groupId: String, newName: String): Boolean {
        val newNameTrimmed = newName.trim()
        if (newNameTrimmed.isBlank()) return false
        val oldId = normalizeId("${oldName.trim()}_$groupId")
        return try {
            val doc = subjectsCollection.document(oldId).get().await()
            if (!doc.exists()) return false
            val groupName = doc.getString("groupName") ?: ""
            val (semesterIds, semesterNames) = parseSubjectSemesters(doc)
            subjectsCollection.document(oldId).delete().await()
            val newId = normalizeId("${newNameTrimmed}_$groupId")
            subjectsCollection.document(newId).set(
                mapOf(
                    "name" to newNameTrimmed,
                    "groupId" to groupId,
                    "groupName" to groupName
                ) + subjectSemestersPayload(semesterIds, semesterNames)
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getSemesters(): List<AdminSemesterTemplate> {
        return try {
            semestersCollection.get().await().documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val startDate = doc.getString("startDate") ?: ""
                val endDate = doc.getString("endDate") ?: ""
                val groupId = doc.getString("groupId") ?: ""
                val groupName = doc.getString("groupName") ?: ""
                AdminSemesterTemplate(
                    id = doc.id,
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    groupId = groupId,
                    groupName = groupName
                )
            }.sortedWith(compareBy({ it.groupName }, { it.name.lowercase(Locale.getDefault()) }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getSemestersForGroup(groupId: String): List<AdminSemesterTemplate> {
        if (groupId.isBlank()) return emptyList()
        return try {
            semestersCollection.get().await().documents
                .mapNotNull { doc ->
                    if (doc.getString("groupId") != groupId) return@mapNotNull null
                    val name = doc.getString("name") ?: return@mapNotNull null
                    AdminSemesterTemplate(
                        id = doc.id,
                        name = name,
                        startDate = doc.getString("startDate") ?: "",
                        endDate = doc.getString("endDate") ?: ""
                    )
                }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addSemester(semester: AdminSemesterTemplate, groupId: String, groupName: String): Boolean {
        return try {
            if (semester.name.isBlank() || groupId.isBlank()) return false
            val docId = normalizeId("${semester.name.trim()}_$groupId")
            semestersCollection.document(docId).set(
                mapOf(
                    "name" to semester.name.trim(),
                    "startDate" to semester.startDate.trim(),
                    "endDate" to semester.endDate.trim(),
                    "groupId" to groupId,
                    "groupName" to groupName
                )
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun detachSemesterFromAllSubjects(semesterId: String) {
        if (semesterId.isBlank()) return
        try {
            subjectsCollection.get().await().documents.forEach { doc ->
                val (ids, names) = parseSubjectSemesters(doc)
                if (semesterId !in ids) return@forEach
                val paired = ids.zip(names)
                val filtered = paired.filter { it.first != semesterId }
                val newIds = filtered.map { it.first }
                val newNames = filtered.map { it.second }
                subjectsCollection.document(doc.id).update(
                    subjectSemestersPayload(newIds, newNames)
                ).await()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun removeSemester(semesterId: String): Boolean {
        return try {
            detachSemesterFromAllSubjects(semesterId)
            semestersCollection.document(semesterId).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateSemester(
        oldSemester: AdminSemesterTemplate,
        semesterNumber: Int,
        startDate: String,
        endDate: String
    ): Boolean {
        if (semesterNumber <= 0) return false
        val start = startDate.trim()
        val end = endDate.trim()
        if (start.isBlank() || end.isBlank()) return false
        val groupId = oldSemester.groupId.trim()
        if (groupId.isBlank() || oldSemester.id.isBlank()) return false

        val newName = "$semesterNumber семестр"
        val oldId = oldSemester.id
        val newId = normalizeId("${newName}_$groupId")

        return try {
            if (newId != oldId) {
                val existing = semestersCollection.document(newId).get().await()
                if (existing.exists()) return false
            }
            val payload = mapOf(
                "name" to newName,
                "startDate" to start,
                "endDate" to end,
                "groupId" to groupId,
                "groupName" to oldSemester.groupName.trim()
            )
            if (newId == oldId) {
                semestersCollection.document(oldId).set(payload).await()
            } else {
                semestersCollection.document(newId).set(payload).await()
                semestersCollection.document(oldId).delete().await()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getCatalogTeachers(): List<CatalogTeacher> {
        return try {
            teachersCollection.get().await().documents.mapNotNull { doc ->
                val fullName = doc.getString("fullName")?.trim() ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val subjectIds = (doc.get("subjectIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                CatalogTeacher(id = doc.id, fullName = fullName, subjectIds = subjectIds)
            }.sortedWith(compareBy({ it.fullName.lowercase(Locale.getDefault()) }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAllCatalogTeacherNames(): List<String> {
        return getCatalogTeachers().map { it.fullName }.distinct().sorted()
    }

    suspend fun getCatalogTeacherNamesForGroup(groupId: String): List<String> {
        if (groupId.isBlank()) return emptyList()
        val subjects = getSubjects().filter { it.groupId == groupId }
        val subjectIdsForGroup = subjects.map { normalizeId("${it.name}_${it.groupId}") }.toSet()
        if (subjectIdsForGroup.isEmpty()) return emptyList()
        return getCatalogTeachers()
            .filter { teacher -> teacher.subjectIds.any { it in subjectIdsForGroup } }
            .map { it.fullName }
            .distinct()
            .sorted()
    }

    suspend fun getCatalogTeacherNamesForSubject(subjectName: String, groupId: String): List<String> {
        if (subjectName.isBlank() || groupId.isBlank()) return emptyList()
        val subjectId = getSubjectDocumentId(subjectName, groupId)
        return getCatalogTeachers()
            .filter { teacher -> subjectId in teacher.subjectIds }
            .map { it.fullName }
            .distinct()
            .sorted()
    }

    suspend fun addCatalogTeacher(fullName: String, subjectIds: List<String>): Boolean {
        return try {
            val name = fullName.trim()
            if (name.isBlank()) return false
            val docId = normalizeId("${name}_${System.currentTimeMillis()}")
            teachersCollection.document(docId).set(
                mapOf(
                    "fullName" to name,
                    "subjectIds" to subjectIds.filter { it.isNotBlank() }.distinct()
                )
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateCatalogTeacher(id: String, fullName: String, subjectIds: List<String>): Boolean {
        return try {
            val name = fullName.trim()
            if (name.isBlank() || id.isBlank()) return false
            teachersCollection.document(id).update(
                mapOf(
                    "fullName" to name,
                    "subjectIds" to subjectIds.filter { it.isNotBlank() }.distinct()
                )
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun removeCatalogTeacher(id: String): Boolean {
        return try {
            if (id.isBlank()) return false
            teachersCollection.document(id).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getSubjectDocumentId(name: String, groupId: String): String =
        normalizeId("${name.trim()}_${groupId.trim()}")

    private fun normalizeId(input: String): String {
        return input.trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "item" }
    }
}
