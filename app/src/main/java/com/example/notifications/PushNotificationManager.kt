package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.data.local.NotificationEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Manages Android Notification Channels, Android 13+ permission checks & settings redirect,
 * multi-device FCM token registration in Firebase Realtime Database,
 * per-user notification read/display synchronization (userNotifications/{userKey}/{notificationId}),
 * one-time General dialog deduplication, and rich local system notification display for Doctor List BD.
 */
object PushNotificationManager {
    private const val TAG = "PushNotifManager"
    const val CHANNEL_ID = "doctor_list_bd_alerts"
    const val TOPIC_ALL_USERS = "all_app_users"

    const val TYPE_GENERAL = "GENERAL"
    const val TYPE_APP_UPDATE = "APP_UPDATE"
    const val TYPE_NEW_DOCTOR = "NEW_DOCTOR"

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_MESSAGE_TYPE = "extra_message_type"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_IMAGE_URL = "extra_image_url"
    const val EXTRA_DOCTOR_ID = "extra_selected_doctor_id"
    const val EXTRA_ACTION_URL = "extra_action_url"
    const val EXTRA_UPDATE_MODE = "extra_update_mode"
    const val EXTRA_VERSION = "extra_version"
    const val EXTRA_DEEP_LINK = "extra_deep_link"

    private const val RTDB_ROOT = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"
    private const val PREFS_NAME = "dlbd_fcm_device_prefs"
    private const val KEY_DEVICE_KEY = "device_fcm_key"
    private const val KEY_LAST_TOKEN = "last_synced_fcm_token"
    private const val KEY_DISPLAYED_GENERAL_IDS = "displayed_general_notification_ids"
    private const val KEY_DELETED_NOTIF_IDS = "deleted_notification_ids_v1"
    private const val KEY_RECEIVED_FCM_IDS = "received_fcm_notification_ids"
    private const val KEY_NOTIF_PERM_ASKED = "notif_permission_asked_v1"

    data class UserInboxSyncSnapshot(
        val readIds: Set<String>,
        val displayedIds: Set<String>,
        val deletedIds: Set<String>,
        val directRecords: List<Pair<String, JSONObject>>
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _foregroundGeneralEvents = MutableSharedFlow<NotificationEntity>(extraBufferCapacity = 4)
    val foregroundGeneralEvents: SharedFlow<NotificationEntity> = _foregroundGeneralEvents.asSharedFlow()

    fun emitForegroundGeneral(entity: NotificationEntity) {
        _foregroundGeneralEvents.tryEmit(entity)
    }

    fun normalizeType(raw: String?): String {
        val cleaned = raw?.trim().orEmpty().uppercase()
        return when (cleaned) {
            "APP_UPDATE", "APP UPDATE", "UPDATE" -> TYPE_APP_UPDATE
            "NEW_DOCTOR", "NEW DOCTOR", "DOCTOR" -> TYPE_NEW_DOCTOR
            else -> TYPE_GENERAL
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Doctor List BD Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "General announcements, app updates, and newly added doctors from Doctor List BD."
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAskedNotificationPermissionBefore(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIF_PERM_ASKED, false)
    }

    fun markNotificationPermissionAsked(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTIF_PERM_ASKED, true).apply()
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallback) }
            }
    }

    fun openExternalUpdateUrl(context: Context, rawUrl: String): Boolean {
        val safeUrl = sanitizeActionUrl(rawUrl)
        if (safeUrl.isBlank()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse { err ->
            Log.w(TAG, "Unable to open update URL: ${err.message}")
            false
        }
    }

    fun getOrCreateDeviceKey(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_KEY, null).orEmpty()
        if (existing.length >= 6) return existing
        val created = "dev_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        prefs.edit().putString(KEY_DEVICE_KEY, created).apply()
        return created
    }

    /**
     * Returns the stable RTDB userNotifications / fcmTokens key:
     * Authenticated Firebase UID when signed in, or guest_{deviceKey} when browsing as guest.
     */
    fun getUserNotificationKey(context: Context, explicitUid: String? = null): String {
        val uid = explicitUid?.trim().orEmpty().ifBlank {
            GoogleAuthManager.currentUser.value?.uid.orEmpty().trim()
        }
        return if (uid.isNotBlank()) uid else "guest_${getOrCreateDeviceKey(context)}"
    }

    @Synchronized
    fun hasDisplayedGeneral(context: Context, notificationId: String): Boolean {
        val id = notificationId.trim()
        if (id.isBlank()) return true
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DISPLAYED_GENERAL_IDS, emptySet()) ?: emptySet()
        return id in set
    }

    @Synchronized
    fun getDisplayedGeneralIds(context: Context): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DISPLAYED_GENERAL_IDS, emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    fun markGeneralDisplayedLocally(context: Context, notificationId: String) {
        val id = notificationId.trim()
        if (id.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_DISPLAYED_GENERAL_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (existing.size > 300) {
            val trimmed = existing.toList().takeLast(200).toMutableSet()
            trimmed.add(id)
            prefs.edit().putStringSet(KEY_DISPLAYED_GENERAL_IDS, trimmed).apply()
        } else {
            existing.add(id)
            prefs.edit().putStringSet(KEY_DISPLAYED_GENERAL_IDS, existing).apply()
        }
    }

    @Synchronized
    fun getDeletedNotificationIds(context: Context): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DELETED_NOTIF_IDS, emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    fun markNotificationDeletedLocally(context: Context, notificationId: String) {
        val id = notificationId.trim()
        if (id.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_DELETED_NOTIF_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val updated = if (existing.size > 400) existing.toList().takeLast(250).toMutableSet() else existing
        updated.add(id)
        prefs.edit().putStringSet(KEY_DELETED_NOTIF_IDS, updated).apply()
    }

    /**
     * Marks a received FCM notificationId as processed to prevent duplicate system notifications
     * if FCM redelivers the same notificationId.
     * Returns true if this is the first time seeing this notificationId.
     */
    @Synchronized
    fun claimReceivedFcmId(context: Context, notificationId: String): Boolean {
        val id = notificationId.trim()
        if (id.isBlank()) return true
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_RECEIVED_FCM_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (id in existing) return false
        val updated = if (existing.size > 300) existing.toList().takeLast(200).toMutableSet() else existing
        updated.add(id)
        prefs.edit().putStringSet(KEY_RECEIVED_FCM_IDS, updated).apply()
        return true
    }

    /**
     * Syncs the user's personal read / displayed / deleted status to RTDB under userNotifications/{userKey}/{notificationId}.
     */
    fun syncUserNotificationState(
        context: Context,
        notificationId: String,
        type: String = TYPE_GENERAL,
        isRead: Boolean = false,
        isDisplayed: Boolean = false,
        isDeleted: Boolean = false
    ) {
        val id = notificationId.trim()
        if (id.isBlank()) return
        val appCtx = context.applicationContext
        ioScope.launch {
            runCatching {
                val userKey = getUserNotificationKey(appCtx)
                val idToken = runCatching { GoogleAuthManager.getIdToken(false) }.getOrNull()
                val now = System.currentTimeMillis()
                val payload = JSONObject().apply {
                    put("notificationId", id)
                    put("type", normalizeType(type))
                    put("isRead", isRead)
                    put("isDisplayed", isDisplayed)
                    put("isDeleted", isDeleted)
                    put("receivedAt", now)
                    put("updatedAt", now)
                }
                putJson(buildRtdbUrl("userNotifications/$userKey/$id", idToken), payload.toString())
            }
        }
    }

    /**
     * Fetches the current user's notification read/display/deleted states AND any direct per-user
     * inbox notifications stored under userNotifications/{userKey}.
     */
    suspend fun fetchUserInboxSyncSnapshot(context: Context): UserInboxSyncSnapshot = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val readIds = mutableSetOf<String>()
        val displayedIds = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()
        val directRecords = mutableListOf<Pair<String, JSONObject>>()
        runCatching {
            val userKey = getUserNotificationKey(appCtx)
            val idToken = runCatching { GoogleAuthManager.getIdToken(false) }.getOrNull()
            val url = buildRtdbUrl("userNotifications/$userKey", idToken)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val body = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
            conn.disconnect()
            if (body.isNotBlank() && body != "null") {
                val obj = JSONObject(body)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val rec = obj.optJSONObject(key) ?: continue
                    if (rec.optBoolean("isRead", false)) readIds.add(key)
                    if (rec.optBoolean("isDisplayed", false)) {
                        displayedIds.add(key)
                        markGeneralDisplayedLocally(appCtx, key)
                    }
                    if (rec.optBoolean("isDeleted", false)) {
                        deletedIds.add(key)
                        markNotificationDeletedLocally(appCtx, key)
                    } else if (rec.optString("title", "").isNotBlank() || rec.optString("titleBn", "").isNotBlank()) {
                        directRecords.add(key to rec)
                    }
                }
            }
        }
        displayedIds.addAll(getDisplayedGeneralIds(appCtx))
        deletedIds.addAll(getDeletedNotificationIds(appCtx))
        UserInboxSyncSnapshot(readIds, displayedIds, deletedIds, directRecords)
    }

    /**
     * Backward-compatible wrapper returning (readIds, displayedIds).
     */
    suspend fun fetchUserNotificationStates(context: Context): Pair<Set<String>, Set<String>> {
        val snap = fetchUserInboxSyncSnapshot(context)
        return snap.readIds to snap.displayedIds
    }

    /**
     * Fetches the current FCM registration token, subscribes to the broadcast topic,
     * and uploads the token to Firebase RTDB for all-users delivery and multi-device tracking.
     */
    fun syncCurrentFcmToken(context: Context, userUid: String? = null) {
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                if (FirebaseApp.getApps(appCtx).isEmpty()) return@launch
                val messaging = FirebaseMessaging.getInstance()
                messaging.isAutoInitEnabled = true
                runCatching { messaging.subscribeToTopic(TOPIC_ALL_USERS).await() }
                    .onSuccess { Log.i(TAG, "Subscribed to FCM topic: $TOPIC_ALL_USERS") }
                    .onFailure { err -> Log.w(TAG, "Topic subscribe warning: ${err.message}") }
                val token = runCatching { messaging.token.await() }.getOrNull().orEmpty().trim()
                if (token.length >= 20) {
                    Log.i(TAG, "FCM Registration Token: $token")
                    registerTokenInRtdb(appCtx, token, userUid)
                }
            } catch (t: Throwable) {
                Log.d(TAG, "FCM token sync deferred: ${t.message}")
            }
        }
    }

    fun onTokenRefreshed(context: Context, newToken: String) {
        val appCtx = context.applicationContext
        val clean = newToken.trim()
        if (clean.length < 20) return
        Log.i(TAG, "FCM Token refreshed: $clean")
        ioScope.launch {
            runCatching { FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL_USERS).await() }
            registerTokenInRtdb(appCtx, clean, GoogleAuthManager.currentUser.value?.uid)
        }
    }

    private suspend fun registerTokenInRtdb(context: Context, token: String, explicitUid: String?) {
        withContext(Dispatchers.IO) {
            val deviceKey = getOrCreateDeviceKey(context)
            val uid = explicitUid?.trim().orEmpty().ifBlank {
                GoogleAuthManager.currentUser.value?.uid.orEmpty()
            }
            val userKey = getUserNotificationKey(context, uid)
            val now = System.currentTimeMillis()
            val idToken = runCatching { GoogleAuthManager.getIdToken(false) }.getOrNull()

            val globalPayload = JSONObject().apply {
                put("token", token)
                put("deviceKey", deviceKey)
                put("uid", uid)
                put("platform", "android")
                put("appVersion", BuildConfig.VERSION_NAME)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("active", true)
                put("updatedAt", now)
            }
            putJson(buildRtdbUrl("fcm_tokens/$deviceKey", idToken), globalPayload.toString())

            val scopedTokenPayload = JSONObject().apply {
                put("token", token)
                put("deviceKey", deviceKey)
                put("platform", "android")
                put("appVersion", BuildConfig.VERSION_NAME)
                put("active", true)
                put("updatedAt", now)
            }
            putJson(buildRtdbUrl("fcmTokens/$userKey/$deviceKey", idToken), scopedTokenPayload.toString())

            if (uid.isNotBlank() && !idToken.isNullOrBlank()) {
                val userTokenPayload = JSONObject().apply {
                    put("token", token)
                    put("platform", "android")
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("active", true)
                    put("updatedAt", now)
                }
                putJson(buildRtdbUrl("users/$uid/fcmTokens/$deviceKey", idToken), userTokenPayload.toString())
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_TOKEN, token)
                .apply()
        }
    }

    /**
     * Removes the current device's FCM token from user-scoped RTDB paths on logout.
     * Must be invoked BEFORE Firebase.auth.signOut() so the Firebase ID token authorizes the delete.
     */
    suspend fun unregisterCurrentDeviceTokenOnLogout(context: Context, explicitUid: String? = null) {
        val appCtx = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching {
                val deviceKey = getOrCreateDeviceKey(appCtx)
                val uid = explicitUid?.trim().orEmpty().ifBlank {
                    GoogleAuthManager.currentUser.value?.uid.orEmpty().trim()
                }
                val idToken = runCatching { GoogleAuthManager.getIdToken(false) }.getOrNull()
                if (uid.isNotBlank() && !idToken.isNullOrBlank()) {
                    deleteJson(buildRtdbUrl("users/$uid/fcmTokens/$deviceKey", idToken))
                    deleteJson(buildRtdbUrl("fcmTokens/$uid/$deviceKey", idToken))
                    deleteJson(buildRtdbUrl("fcm_tokens/$deviceKey", idToken))
                }
                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_LAST_TOKEN)
                    .apply()
                Log.i(TAG, "Unregistered device FCM token on logout (deviceKey=$deviceKey)")
            }.onFailure { err ->
                Log.w(TAG, "Logout FCM token cleanup warning: ${err.message}")
            }
        }
    }

    suspend fun showSystemNotification(
        context: Context,
        notificationId: String,
        title: String,
        message: String,
        messageType: String,
        selectedDoctorId: String = "",
        imageUrl: String = "",
        actionUrl: String = "",
        updateMode: String = "",
        version: String = "",
        deepLink: String = ""
    ) {
        val appCtx = context.applicationContext
        ensureNotificationChannel(appCtx)
        if (!hasNotificationPermission(appCtx)) return

        val safeTitle = title.trim().ifBlank { "Doctor List BD" }
        val safeMsg = message.trim()
        val safeType = normalizeType(messageType)
        val safeImageUrl = sanitizeHttpUrl(imageUrl)
        val safeActionUrl = sanitizeActionUrl(actionUrl)
        val safeDeepLink = sanitizeDeepLink(deepLink)

        val tapIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_MESSAGE_TYPE, safeType)
            putExtra(EXTRA_TITLE, safeTitle)
            putExtra(EXTRA_BODY, safeMsg)
            if (safeImageUrl.isNotBlank()) putExtra(EXTRA_IMAGE_URL, safeImageUrl)
            if (selectedDoctorId.isNotBlank()) putExtra(EXTRA_DOCTOR_ID, selectedDoctorId.trim())
            if (safeActionUrl.isNotBlank()) putExtra(EXTRA_ACTION_URL, safeActionUrl)
            if (updateMode.isNotBlank()) putExtra(EXTRA_UPDATE_MODE, updateMode.trim().uppercase())
            if (version.isNotBlank()) putExtra(EXTRA_VERSION, version.trim())
            if (safeDeepLink.isNotBlank()) putExtra(EXTRA_DEEP_LINK, safeDeepLink)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            appCtx,
            notificationId.hashCode(),
            tapIntent,
            pendingFlags
        )

        val bitmap: Bitmap? = if (safeImageUrl.isNotBlank()) {
            withContext(Dispatchers.IO) { downloadSmallBitmap(safeImageUrl) }
        } else null

        val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(appCtx, R.color.notification_accent))
            .setContentTitle(safeTitle)
            .setContentText(safeMsg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(safeMsg)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(safeMsg))
        }

        try {
            NotificationManagerCompat.from(appCtx).notify(notificationId, 0, builder.build())
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${se.message}")
        }
    }

    fun sanitizeHttpUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            trimmed
        } else ""
    }

    fun sanitizeActionUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("market://", ignoreCase = true)
        ) trimmed else ""
    }

    fun sanitizeDeepLink(raw: String): String {
        val trimmed = raw.trim()
        return if (
            trimmed.startsWith("app://", ignoreCase = true) ||
            trimmed.startsWith("doctorlistbd://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("market://", ignoreCase = true)
        ) trimmed else ""
    }

    private fun downloadSmallBitmap(urlStr: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildRtdbUrl(path: String, idToken: String?): String {
        val clean = path.trimStart('/')
        return if (!idToken.isNullOrBlank()) {
            "$RTDB_ROOT/$clean.json?auth=${URLEncoder.encode(idToken, "UTF-8")}"
        } else {
            "$RTDB_ROOT/$clean.json"
        }
    }

    private fun putJson(urlString: String, body: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 7000
                readTimeout = 7000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun deleteJson(urlString: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
