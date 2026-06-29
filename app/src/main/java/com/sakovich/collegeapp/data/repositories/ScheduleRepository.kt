package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.utils.AppLog
import kotlinx.coroutines.tasks.await
import java.util.Date

class ScheduleRepository {

    companion object {
        private const val TAG = "ScheduleRepository"
    }

    private val db = FirebaseFirestore.getInstance()
    val scheduleCollection = db.collection("schedule")
    private val notificationRepository = NotificationRepository()

    suspend fun getScheduleForGroup(group: String): List<ScheduleItem> {
        return try {
            AppLog.d(TAG, "🔥 Запрос расписания для группы: '$group'")
            val query = scheduleCollection
                .whereEqualTo("group", group)
                .orderBy("day", Query.Direction.ASCENDING)
                .orderBy("time", Query.Direction.ASCENDING)
                .get()
                .await()

            query.documents.mapNotNull { document ->
                convertToScheduleItem(document)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка загрузки расписания для группы '$group': ${e.message}")
            emptyList()
        }
    }

    suspend fun getAllSchedule(): List<ScheduleItem> {
        return try {
            AppLog.d(TAG, "🔥 Запрос ВСЕГО расписания")
            val query = scheduleCollection
                .orderBy("day", Query.Direction.ASCENDING)
                .orderBy("time", Query.Direction.ASCENDING)
                .get()
                .await()

            query.documents.mapNotNull { document ->
                convertToScheduleItem(document)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка загрузки всех расписаний: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAllScheduleFromServer(): List<ScheduleItem> {
        return try {
            AppLog.d(TAG, "🔥🔄 Принудительный запрос ВСЕГО расписания с СЕРВЕРА (без кэша)")
            val snapshot = scheduleCollection
                .get(Source.SERVER)
                .await()

            snapshot.documents.mapNotNull { document ->
                convertToScheduleItem(document)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка принудительной загрузки с сервера: ${e.message}")
            emptyList()
        }
    }

    suspend fun getScheduleForGroupFromServer(group: String): List<ScheduleItem> {
        return try {
            AppLog.d(TAG, "🔥🔄 Принудительный запрос расписания для группы '$group' с СЕРВЕРА")
            val query = scheduleCollection
                .whereEqualTo("group", group)
                .orderBy("day", Query.Direction.ASCENDING)
                .orderBy("time", Query.Direction.ASCENDING)
                .get(Source.SERVER)
                .await()

            AppLog.d(TAG, "✅ С сервера получено ${query.documents.size} документов для группы '$group'")

            query.documents.mapNotNull { document ->
                convertToScheduleItem(document)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка загрузки с сервера для группы '$group': ${e.message}")
            emptyList()
        }
    }

    suspend fun getScheduleForGroupFromServerNoOrder(group: String): List<ScheduleItem> {
        if (group.isBlank()) return emptyList()
        return try {
            val snapshot = scheduleCollection
                .whereEqualTo("group", group)
                .get(Source.SERVER)
                .await()
            snapshot.documents.mapNotNull { convertToScheduleItem(it) }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка загрузки расписания группы '$group': ${e.message}")
            emptyList()
        }
    }

    suspend fun addSchedule(schedule: ScheduleItem): String {
        return try {
            AppLog.d(TAG, "💾 Добавление расписания: ${schedule.subject} для группы ${schedule.group}")

            val scheduleData = hashMapOf(
                "day" to schedule.day,
                "date" to schedule.date,
                "time" to schedule.time,
                "subject" to schedule.subject,
                "teacherName" to schedule.teacherName,
                "room" to schedule.room,
                "isSubgroup" to schedule.isSubgroup,
                "teacherName2" to schedule.teacherName2,
                "room2" to schedule.room2,
                "type" to schedule.type.name,
                "group" to schedule.group,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "createdBy" to schedule.createdBy,
                "createdByRole" to schedule.createdByRole,
                "assignedStudentIds" to schedule.assignedStudentIds
            )

            AppLog.d(TAG, "📤 Данные для сохранения: $scheduleData")

            val document = scheduleCollection.add(scheduleData).await()
            sendScheduleNotification(
                schedule = schedule,
                scheduleId = document.id,
                isUpdate = false,
                isDelete = false,
                excludeUserId = schedule.createdBy
            )
            AppLog.d(TAG, "✅ Документ успешно добавлен с ID: ${document.id}")
            document.id
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка добавления расписания: ${e.message}")
            throw e
        }
    }

    private fun convertToScheduleItem(document: com.google.firebase.firestore.DocumentSnapshot): ScheduleItem? {
        return try {
            val id = document.id
            val day = document.getString("day") ?: ""
            val date = document.getString("date") ?: ""
            val time = document.getString("time") ?: ""
            val subject = document.getString("subject") ?: ""
            val teacherName = document.getString("teacherName") ?: ""
            val room = document.getString("room") ?: ""
            val isSubgroup = document.getBoolean("isSubgroup") ?: false
            val teacherName2 = document.getString("teacherName2") ?: ""
            val room2 = document.getString("room2") ?: ""
            val typeString = document.getString("type") ?: "LECTURE"
            val group = document.getString("group") ?: ""

            val createdAt = try {

                val timestamp = document.getTimestamp("createdAt")
                if (timestamp != null) {
                    timestamp.toDate()
                } else {

                    document.getDate("createdAt") ?: Date()
                }
            } catch (e: Exception) {

                AppLog.w(TAG, "⚠️ Ошибка получения createdAt для документа $id: ${e.message}")
                Date()
            }

            val createdBy = document.getString("createdBy") ?: ""
            val createdByRole = document.getString("createdByRole") ?: ""
            val assignedStudentIds = (document.get("assignedStudentIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            val type = try {
                ScheduleType.valueOf(typeString)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "⚠️ Неизвестный тип занятия: '$typeString', используем LECTURE")
                ScheduleType.LECTURE
            }

            ScheduleItem(
                id = id,
                day = day,
                date = date,
                time = time,
                subject = subject,
                teacherName = teacherName,
                room = room,
                isSubgroup = isSubgroup,
                teacherName2 = teacherName2,
                room2 = room2,
                type = type,
                group = group,
                createdAt = createdAt,
                createdBy = createdBy,
                createdByRole = createdByRole,
                assignedStudentIds = assignedStudentIds
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Ошибка конвертации документа ${document.id}: ${e.message}")
            null
        }
    }

    suspend fun updateSchedule(scheduleId: String, schedule: ScheduleItem, editorUserId: String = ""): Boolean {
        return try {
            val scheduleData = hashMapOf(
                "day" to schedule.day,
                "date" to schedule.date,
                "time" to schedule.time,
                "subject" to schedule.subject,
                "teacherName" to schedule.teacherName,
                "room" to schedule.room,
                "isSubgroup" to schedule.isSubgroup,
                "teacherName2" to schedule.teacherName2,
                "room2" to schedule.room2,
                "type" to schedule.type.name,
                "group" to schedule.group,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "createdBy" to schedule.createdBy,
                "createdByRole" to schedule.createdByRole,
                "assignedStudentIds" to schedule.assignedStudentIds
            )
            scheduleCollection.document(scheduleId).set(scheduleData).await()
            sendScheduleNotification(
                schedule = schedule,
                scheduleId = scheduleId,
                isUpdate = true,
                isDelete = false,
                excludeUserId = editorUserId.ifBlank { schedule.createdBy }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun sendScheduleNotification(
        schedule: ScheduleItem,
        scheduleId: String,
        isUpdate: Boolean,
        isDelete: Boolean,
        excludeUserId: String
    ) {
        if (schedule.group.isBlank()) return

        try {
            val userRepository = UserRepository()
            val memberIds = userRepository.getGroupMemberUserIds(schedule.group, excludeUserId)
            if (memberIds.isEmpty()) return

            val title = when {
                isDelete -> "🗑️ Удаление из расписания"
                isUpdate -> "✏️ Изменение расписания"
                else -> "📚 Новое занятие в расписании"
            }
            val message = when {
                isDelete -> "Удалено занятие: ${schedule.subject}, ${schedule.date} ${schedule.time}"
                isUpdate -> "Обновлено занятие: ${schedule.subject}, ${schedule.date} ${schedule.time}, ауд. ${schedule.room}"
                else -> "Добавлено занятие: ${schedule.subject}, ${schedule.date} ${schedule.time}, ауд. ${schedule.room}"
            }

            notificationRepository.createGroupNotification(
                studentIds = memberIds,
                title = title,
                message = message,
                type = com.sakovich.collegeapp.data.models.NotificationType.SCHEDULE,
                relatedId = scheduleId,
                relatedType = "schedule",
                excludeUserId = excludeUserId
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "⚠️ Не удалось отправить уведомления по расписанию: ${e.message}")
        }
    }

    suspend fun deleteSchedule(scheduleId: String, editorUserId: String = ""): Boolean {
        return try {
            val document = scheduleCollection.document(scheduleId).get().await()
            val schedule = convertToScheduleItem(document)
            scheduleCollection.document(scheduleId).delete().await()
            if (schedule != null) {
                sendScheduleNotification(
                    schedule = schedule,
                    scheduleId = scheduleId,
                    isUpdate = false,
                    isDelete = true,
                    excludeUserId = editorUserId
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
