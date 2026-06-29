package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sakovich.collegeapp.data.models.Notification
import com.sakovich.collegeapp.data.models.NotificationType
import com.sakovich.collegeapp.utils.AppLog
import kotlinx.coroutines.tasks.await
import java.util.Date

class NotificationRepository {

    companion object {
        private const val TAG = "NotificationRepository"
    }

    private val db: FirebaseFirestore = Firebase.firestore
    private val notificationsCollection = db.collection("notifications")

    suspend fun createNotification(notification: Notification): String {
        return try {
            val data = notification.toMap()
            AppLog.d(TAG, "📤 Создание уведомления для userId: ${notification.userId}")
            AppLog.d(TAG, "📤 Данные: $data")
            val docRef = notificationsCollection.add(data).await()
            AppLog.d(TAG, "✅ Уведомление создано с ID: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка создания уведомления: ${e.message}")
            throw e
        }
    }

    suspend fun getUserNotifications(userId: String): List<Notification> {
        return try {

            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            AppLog.d(TAG, "📬 Найдено документов в Firebase: ${snapshot.size()}")

            val notifications = snapshot.documents.mapNotNull { doc ->
                val data = doc.data
                AppLog.d(TAG, "📄 Документ ID: ${doc.id}, данные: $data")
                convertToNotification(doc.id, data)
            }

            notifications.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка загрузки уведомлений: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUnreadNotifications(userId: String): List<Notification> {
        return try {

            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            val notifications = snapshot.documents.mapNotNull { doc ->
                convertToNotification(doc.id, doc.data)
            }

            notifications.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка загрузки непрочитанных уведомлений: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUnreadCount(userId: String): Int {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun markAsRead(notificationId: String): Boolean {
        return try {
            notificationsCollection.document(notificationId)
                .update("isRead", true)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun markAllAsRead(userId: String): Boolean {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteNotification(notificationId: String): Boolean {
        return try {
            notificationsCollection.document(notificationId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAllRead(userId: String): Boolean {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", true)
                .get()
                .await()

            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAllByUser(userId: String): Boolean {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createGradeNotification(
        studentId: String,
        studentName: String,
        subject: String,
        grade: Int,
        teacherName: String,
        gradeId: String
    ) {
        AppLog.d(TAG, "📊 Создание уведомления об отметке для studentId: $studentId")
        val notification = Notification(
            userId = studentId,
            title = "📊 Новая отметка",
            message = "Вам выставлена отметка $grade по предмету \"$subject\"",
            type = NotificationType.GRADE,
            isRead = false,
            createdAt = Date(),
            relatedId = gradeId,
            relatedType = "grade"
        )
        AppLog.d(TAG, "📊 Уведомление создано: userId=${notification.userId}, title=${notification.title}")
        createNotification(notification)
    }

    suspend fun createAbsenceNotification(
        studentId: String,
        studentName: String,
        subject: String,
        date: String,
        hours: Int,
        createdByName: String,
        absenceId: String,
        isExcused: Boolean
    ) {
        AppLog.d(TAG, "📋 Создание уведомления о пропуске для studentId: $studentId")
        val status = if (isExcused) "уважительный" else "неуважительный"
        val notification = Notification(
            userId = studentId,
            title = "📋 Пропуск занятия",
            message = "Вам зафиксирован $status пропуск по предмету \"$subject\" ($hours ч)",
            type = NotificationType.ABSENCE,
            isRead = false,
            createdAt = Date(),
            relatedId = absenceId,
            relatedType = "absence"
        )
        AppLog.d(TAG, "📋 Уведомление создано: userId=${notification.userId}, title=${notification.title}")
        createNotification(notification)
    }

    suspend fun createAbsenceUpdatedNotification(
        studentId: String,
        subject: String,
        date: String,
        hours: Int,
        updatedByName: String,
        absenceId: String,
        isExcused: Boolean
    ) {
        val status = if (isExcused) "уважительный" else "неуважительный"
        val notification = Notification(
            userId = studentId,
            title = "✏️ Обновление пропуска",
            message = "Ваш пропуск ($date, \"$subject\", $hours ч) обновлен: $status. Изменил: $updatedByName",
            type = NotificationType.ABSENCE,
            isRead = false,
            createdAt = Date(),
            relatedId = absenceId,
            relatedType = "absence"
        )
        createNotification(notification)
    }

    suspend fun createGroupNotification(
        studentIds: List<String>,
        title: String,
        message: String,
        type: NotificationType,
        relatedId: String = "",
        relatedType: String = "",
        excludeUserId: String = ""
    ) {
        val targets = studentIds.filter { it.isNotBlank() && it != excludeUserId }
        if (targets.isEmpty()) return
        AppLog.d(TAG, "📢 Создание групповых уведомлений для ${targets.size} пользователей")
        val notifications = targets.map { studentId ->
            Notification(
                userId = studentId,
                title = title,
                message = message,
                type = type,
                isRead = false,
                createdAt = Date(),
                relatedId = relatedId,
                relatedType = relatedType
            )
        }

        val batch = db.batch()
        notifications.forEach { notification ->
            val docRef = notificationsCollection.document()
            val data = notification.toMap()
            AppLog.d(TAG, "📤 Добавление в batch: userId=${notification.userId}, title=${notification.title}")
            batch.set(docRef, data)
        }
        batch.commit().await()
        AppLog.d(TAG, "✅ Групповые уведомления созданы")
    }

    private fun convertToNotification(id: String, data: Map<String, Any>?): Notification? {
        if (data == null) {
            AppLog.w(TAG, "⚠️ Данные уведомления пусты для ID: $id")
            return null
        }

        return try {
            val typeStr = data["type"] as? String ?: "SYSTEM"
            val type = try {
                NotificationType.valueOf(typeStr)
            } catch (e: Exception) {
                AppLog.w(TAG, "⚠️ Неизвестный тип уведомления: $typeStr")
                NotificationType.SYSTEM
            }

            val createdAt = when (val createdAtValue = data["createdAt"]) {
                is com.google.firebase.Timestamp -> createdAtValue.toDate()
                is Date -> createdAtValue
                is Long -> Date(createdAtValue)
                else -> {
                    AppLog.w(TAG, "⚠️ Неизвестный тип createdAt: ${createdAtValue?.javaClass?.name}")
                    Date()
                }
            }

            val notification = Notification(
                id = id,
                userId = data["userId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                message = data["message"] as? String ?: "",
                type = type,
                isRead = data["isRead"] as? Boolean ?: false,
                createdAt = createdAt,
                relatedId = data["relatedId"] as? String ?: "",
                relatedType = data["relatedType"] as? String ?: ""
            )

            AppLog.d(TAG, "✅ Уведомление конвертировано: ${notification.title}")
            notification
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Ошибка конвертации уведомления ID $id: ${e.message}")
            null
        }
    }
}
