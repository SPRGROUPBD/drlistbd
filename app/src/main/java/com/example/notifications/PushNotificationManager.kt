package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.auth.GoogleAuthManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * Manages Android Notification Channels, Android 13+ permission checks,
 * multi-device FCM token registration in Firebase Realtime Database,
 * and rich local system notification display for Doctor List BD.
 */
object PushNotificationManager {
    private const val TAG = "PushNotifManager"
    const val CHANNEL_ID = "doctor_list_bd_alerts"
    const val TOPIC_ALL_USERS = "all_app_users"

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_MESSAGE_TYPE = "extra_message_type"
    const val EXTRA_DOCTOR_ID = "extra_selected_doctor_id"
    const val EXTRA_DEEP_LINK = "extra_deep_link"

    private const val RTDB_ROOT = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"
    private const val PREFS_NAME = "dlbd_fcm_device_prefs"
    private const val KEY_DEVICE_KEY = "device_fcm_key"
    private const val KEY_LAST_TOKEN = "last_synced_fcm_token"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Doctor List BD Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Announcements, app updates, and newly added doctors from Doctor List BD."
            enableLights(true)
            enableVibration(true)
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

    fun getOrCreateDeviceKey(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_KEY, null).orEmpty()
        if (existing.length >= 6) return existing
        val created = "dev_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        prefs.edit().putString(KEY_DEVICE_KEY, created).apply()
        return created
    }

    /**
     * Fetches the current FCM registration token, subscribes to the broadcast topic,
     * and uploads the token to Firebase RTDB for both all-users delivery and per-user multi-device tracking.
     */
    fun syncCurrentFcmToken(context: Context, userUid: String? = null) {
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                if (FirebaseApp.getApps(appCtx).isEmpty()) return@launch
                val messaging = FirebaseMessaging.getInstance()
                runCatching { messaging.subscribeToTopic(TOPIC_ALL_USERS).await() }
                val token = runCatching { messaging.token.await() }.getOrNull().orEmpty().trim()
                if (token.length >= 20) {
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
        ioScope.launch {
            registerTokenInRtdb(appCtx, clean, GoogleAuthManager.currentUser.value?.uid)
        }
    }

    private suspend fun registerTokenInRtdb(context: Context, token: String, explicitUid: String?) {
        withContext(Dispatchers.IO) {
            val deviceKey = getOrCreateDeviceKey(context)
            val uid = explicitUid?.trim().orEmpty().ifBlank {
                GoogleAuthManager.currentUser.value?.uid.orEmpty()
            }
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

    suspend fun showSystemNotification(
        context: Context,
        notificationId: String,
        title: String,
        message: String,
        messageType: String,
        selectedDoctorId: String = "",
        imageUrl: String = "",
        deepLink: String = ""
    ) {
        val appCtx = context.applicationContext
        ensureNotificationChannel(appCtx)
        if (!hasNotificationPermission(appCtx)) return

        val safeTitle = title.trim().ifBlank { "Doctor List BD" }
        val safeMsg = message.trim()
        val safeImageUrl = sanitizeHttpUrl(imageUrl)
        val safeDeepLink = sanitizeDeepLink(deepLink)

        val tapIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_MESSAGE_TYPE, messageType)
            if (selectedDoctorId.isNotBlank()) putExtra(EXTRA_DOCTOR_ID, selectedDoctorId)
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(safeTitle)
            .setContentText(safeMsg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
            NotificationManagerCompat.from(appCtx).notify(notificationId.hashCode(), builder.build())
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

    fun sanitizeDeepLink(raw: String): String {
        val trimmed = raw.trim()
        return if (
            trimmed.startsWith("app://", ignoreCase = true) ||
            trimmed.startsWith("doctorlistbd://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
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
}
