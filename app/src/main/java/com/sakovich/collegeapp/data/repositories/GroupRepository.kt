package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.Group
import com.sakovich.collegeapp.data.models.Student
import kotlinx.coroutines.tasks.await
import java.util.Locale

class GroupRepository {
    private val db: FirebaseFirestore = Firebase.firestore

    companion object {

        fun groupNameToDocumentId(groupName: String): String {
            return groupName.trim()
                .lowercase(Locale.getDefault())
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .trim('_')
                .ifBlank { "item" }
        }

        fun effectiveGroupIdForUser(groupName: String, groupId: String, legacyGroup: String = ""): String {
            val name = groupName.trim().ifBlank { legacyGroup.trim() }
            return if (name.isNotBlank()) groupNameToDocumentId(name) else groupId.trim()
        }
    }

    suspend fun getAllGroups(): List<Group> {
        return try {
            val groupsCollection = db.collection("admin_groups")
            val usersCollection = db.collection("users")

            val groupDocs = groupsCollection.get().await().documents
            val usersSnapshot = usersCollection
                .whereIn("role", listOf("student", "headman"))
                .get()
                .await()

            val countByGroupId = usersSnapshot.documents
                .mapNotNull { it.getString("groupId")?.takeIf { id -> id.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
            val groupNameByGroupId = usersSnapshot.documents
                .associate { doc ->
                    val gid = doc.getString("groupId") ?: ""
                    val gname = doc.getString("groupName") ?: ""
                    gid to gname
                }
                .filter { it.key.isNotBlank() }

            val fromCatalog = groupDocs.map { doc ->
                Group(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    studentCount = countByGroupId[doc.id] ?: 0
                )
            }
            val catalogIds = fromCatalog.mapTo(mutableSetOf()) { it.id }
            val onlyInUsers = countByGroupId.keys
                .filter { it !in catalogIds }
                .map { gid ->
                    Group(
                        id = gid,
                        name = groupNameByGroupId[gid] ?: gid,
                        studentCount = countByGroupId[gid] ?: 0
                    )
                }
            (fromCatalog + onlyInUsers).sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStudentsByGroup(groupName: String): List<Student> {
        return try {
            val groupId = groupNameToDocumentId(groupName)
            val usersCollection = db.collection("users")
            val studentRole = usersCollection.whereEqualTo("role", "student")
            val headmanRole = usersCollection.whereEqualTo("role", "headman")

            val byNameStudent = studentRole.whereEqualTo("groupName", groupName).get().await()
            val byNameHeadman = headmanRole.whereEqualTo("groupName", groupName).get().await()
            val byIdStudent = studentRole.whereEqualTo("groupId", groupId).get().await()
            val byIdHeadman = headmanRole.whereEqualTo("groupId", groupId).get().await()

            val studentsDocList = (byNameStudent.documents + byIdStudent.documents).distinctBy { it.id }
            val headmanDocList = (byNameHeadman.documents + byIdHeadman.documents).distinctBy { it.id }

            val students = studentsDocList.map { document ->
                Student(
                    id = document.id,
                    email = document.getString("email") ?: "",
                    fullName = document.getString("fullName") ?: "",
                    groupId = document.getString("groupId") ?: "",
                    groupName = document.getString("groupName") ?: "",
                    avatar = document.getString("avatar") ?: "",
                    isHeadman = false
                )
            }

            val headmen = headmanDocList.map { document ->
                Student(
                    id = document.id,
                    email = document.getString("email") ?: "",
                    fullName = "${document.getString("fullName") ?: ""} (Староста)",
                    groupId = document.getString("groupId") ?: "",
                    groupName = document.getString("groupName") ?: "",
                    avatar = document.getString("avatar") ?: "",
                    isHeadman = true
                )
            }

            (headmen + students).sortedBy { it.fullName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStudentsByGroupId(groupId: String): List<Student> {
        if (groupId.isBlank()) return emptyList()
        return try {
            val usersCollection = db.collection("users")
            val students = usersCollection.whereEqualTo("role", "student").whereEqualTo("groupId", groupId).get().await()
            val headmen = usersCollection.whereEqualTo("role", "headman").whereEqualTo("groupId", groupId).get().await()
            val studentList = students.documents.map { doc ->
                Student(
                    id = doc.id,
                    email = doc.getString("email") ?: "",
                    fullName = doc.getString("fullName") ?: "",
                    groupId = doc.getString("groupId") ?: "",
                    groupName = doc.getString("groupName") ?: "",
                    avatar = doc.getString("avatar") ?: "",
                    isHeadman = false
                )
            }
            val headmanList = headmen.documents.map { doc ->
                Student(
                    id = doc.id,
                    email = doc.getString("email") ?: "",
                    fullName = "${doc.getString("fullName") ?: ""} (Староста)",
                    groupId = doc.getString("groupId") ?: "",
                    groupName = doc.getString("groupName") ?: "",
                    avatar = doc.getString("avatar") ?: "",
                    isHeadman = true
                )
            }
            (headmanList + studentList).sortedBy { it.fullName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTeacherGroups(teacherId: String): List<Group> {
        return try {

            val teacherDoc = db.collection("users").document(teacherId).get().await()
            val teacherGroup = teacherDoc.getString("groupName") ?: ""

            if (teacherGroup.isNotEmpty()) {
                val groupId = groupNameToDocumentId(teacherGroup)
                val studentsCount = getStudentsByGroup(teacherGroup).size
                listOf(
                    Group(
                        id = groupId,
                        name = teacherGroup,
                        studentCount = studentsCount
                    )
                )
            } else {

                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
