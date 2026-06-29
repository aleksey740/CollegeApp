const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const ANDROID_CHANNEL_ID = "collegeapp_general_notifications";

/**
 * Отправляет FCM push при добавлении документа в notifications/{id}.
 * Токен берётся из users/{userId}.fcmToken.
 *
 * Документы с localOnly: true не пушатся (локальные напоминания питания/кружков).
 */
exports.sendNotificationPush = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const data = snapshot.data();
    if (!data || data.localOnly === true || data.skipPush === true) {
      return;
    }

    const userId = data.userId;
    if (!userId) {
      console.warn("sendNotificationPush: missing userId", event.params.notificationId);
      return;
    }

    const userDoc = await getFirestore().collection("users").doc(userId).get();
    if (!userDoc.exists) {
      console.warn("sendNotificationPush: user not found", userId);
      return;
    }

    const fcmToken = userDoc.get("fcmToken");
    if (!fcmToken) {
      console.warn("sendNotificationPush: no fcmToken for user", userId);
      return;
    }

    const notificationId = event.params.notificationId;
    const title = data.title || "Новое уведомление";
    const message = data.message || "";

    try {
      await getMessaging().send({
        token: fcmToken,
        notification: {
          title,
          body: message,
        },
        data: {
          notificationId,
          title,
          message,
          type: String(data.type || "SYSTEM"),
          relatedId: String(data.relatedId || ""),
          relatedType: String(data.relatedType || ""),
        },
        android: {
          priority: "high",
          notification: {
            channelId: ANDROID_CHANNEL_ID,
            priority: "high",
          },
        },
      });
      console.log("sendNotificationPush: sent to", userId, notificationId);
    } catch (error) {
      console.error("sendNotificationPush: failed for", userId, notificationId, error);
      const code = error && error.code;
      if (code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token") {
        await userDoc.ref.update({
          fcmToken: null,
          fcmUpdatedAt: null,
        });
      }
    }
  }
);
