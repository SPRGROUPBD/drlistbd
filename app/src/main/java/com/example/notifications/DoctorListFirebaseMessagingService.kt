package com.example.notifications

import com.example.data.local.AppDatabase
import com.example.data.local.NotificationEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives real Firebase Cloud Messaging (FCM) push notifications from the Doctor List BD
 * Notification backend, deduplicates deliveries, persists them to Room & userNotifications,
 * emits foreground General events, and displays a rich Android system notification.
 */
class DoctorListFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushNotificationManager.onTokenRefreshed(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val notif = remoteMessage.notification

        val notificationId = data["notificationId"]?.trim().orEmpty()
            .ifBlank { remoteMessage.messageId?.trim().orEmpty() }
            .ifBlank { "fcm_${System.currentTimeMillis()}" }

        val title = data["title"]?.trim().orEmpty()
            .ifBlank { notif?.title?.trim().orEmpty() }
            .ifBlank { "Doctor List BD" }

        val body = data["body"]?.trim().orEmpty()
            .ifBlank { data["message"]?.trim().orEmpty() }
            .ifBlank { notif?.body?.trim().orEmpty() }

        val messageType = PushNotificationManager.normalizeType(
            data["type"]?.trim().orEmpty().ifBlank { data["messageType"] }
        )

        val doctorId = data["doctorId"]?.trim().orEmpty()
            .ifBlank { data["selectedDoctorId"]?.trim().orEmpty() }

        val doctorName = data["selectedDoctorName"]?.trim().orEmpty()
            .ifBlank { data["doctorName"]?.trim().orEmpty() }

        val imageUrl = PushNotificationManager.sanitizeHttpUrl(
            data["imageUrl"]?.trim().orEmpty().ifBlank { notif?.imageUrl?.toString().orEmpty() }
        )

        val actionUrl = PushNotificationManager.sanitizeActionUrl(
            data["actionUrl"]?.trim().orEmpty().ifBlank { data["updateUrl"]?.trim().orEmpty() }
        )

        val updateMode = data["updateMode"]?.trim().orEmpty().uppercase().let {
            if (it == "IMMEDIATE" || it == "FLEXIBLE") it else ""
        }

        val version = data["version"]?.trim().orEmpty()
            .ifBlank { data["updateVersion"]?.trim().orEmpty() }

        val deepLink = PushNotificationManager.sanitizeDeepLink(
            data["deepLink"]?.trim().orEmpty().ifBlank { actionUrl }
        )

        // Prevent duplicate system notification display if FCM delivers the same notificationId twice
        val isFirstDelivery = PushNotificationManager.claimReceivedFcmId(applicationContext, notificationId)

        serviceScope.launch {
            val alreadyDisplayed = PushNotificationManager.hasDisplayedGeneral(applicationContext, notificationId)
            val entity = NotificationEntity(
                id = notificationId,
                titleBn = title,
                titleEn = title,
                messageBn = body,
                messageEn = body,
                targetRole = "ALL",
                messageType = messageType,
                selectedDoctorId = doctorId,
                selectedDoctorName = doctorName,
                imageUrl = imageUrl,
                deepLink = deepLink,
                actionUrl = actionUrl,
                updateMode = updateMode,
                version = version,
                isDisplayed = alreadyDisplayed,
                isRead = alreadyDisplayed,
                createdAt = System.currentTimeMillis()
            )

            runCatching {
                val db = AppDatabase.getInstance(applicationContext)
                val existing = db.notificationDao().getById(notificationId)
                db.notificationDao().insert(
                    entity.copy(
                        isRead = existing?.isRead ?: alreadyDisplayed,
                        isDisplayed = existing?.isDisplayed ?: alreadyDisplayed
                    )
                )
            }

            PushNotificationManager.syncUserNotificationState(
                context = applicationContext,
                notificationId = notificationId,
                type = messageType,
                isRead = alreadyDisplayed,
                isDisplayed = alreadyDisplayed
            )

            // If foreground and GENERAL (and not yet displayed), emit to show in-app dialog once
            if (messageType == PushNotificationManager.TYPE_GENERAL && !alreadyDisplayed) {
                PushNotificationManager.emitForegroundGeneral(entity)
            }

            if (isFirstDelivery) {
                PushNotificationManager.showSystemNotification(
                    context = applicationContext,
                    notificationId = notificationId,
                    title = title,
                    message = body,
                    messageType = messageType,
                    selectedDoctorId = doctorId,
                    imageUrl = imageUrl,
                    actionUrl = actionUrl,
                    updateMode = updateMode,
                    version = version,
                    deepLink = deepLink
                )
            }
        }
    }
}
