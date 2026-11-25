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
 * Admin Push Notification backend, persists them immediately to the local Notification Inbox,
 * and shows a rich Android system notification.
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
        val message = data["message"]?.trim().orEmpty()
            .ifBlank { data["body"]?.trim().orEmpty() }
            .ifBlank { notif?.body?.trim().orEmpty() }
        val messageType = data["messageType"]?.trim().orEmpty().uppercase().ifBlank { "GENERAL" }
        val selectedDoctorId = data["selectedDoctorId"]?.trim().orEmpty()
        val selectedDoctorName = data["selectedDoctorName"]?.trim().orEmpty()
        val imageUrl = PushNotificationManager.sanitizeHttpUrl(
            data["imageUrl"]?.trim().orEmpty().ifBlank { notif?.imageUrl?.toString().orEmpty() }
        )
        val deepLink = PushNotificationManager.sanitizeDeepLink(data["deepLink"]?.trim().orEmpty())

        serviceScope.launch {
            runCatching {
                val db = AppDatabase.getInstance(applicationContext)
                db.notificationDao().insert(
                    NotificationEntity(
                        id = notificationId,
                        titleBn = title,
                        titleEn = title,
                        messageBn = message,
                        messageEn = message,
                        targetRole = "ALL",
                        messageType = messageType,
                        selectedDoctorId = selectedDoctorId,
                        selectedDoctorName = selectedDoctorName,
                        imageUrl = imageUrl,
                        deepLink = deepLink,
                        isRead = false,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            PushNotificationManager.showSystemNotification(
                context = applicationContext,
                notificationId = notificationId,
                title = title,
                message = message,
                messageType = messageType,
                selectedDoctorId = selectedDoctorId,
                imageUrl = imageUrl,
                deepLink = deepLink
            )
        }
    }
}
