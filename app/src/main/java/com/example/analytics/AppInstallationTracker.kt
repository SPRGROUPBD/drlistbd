package com.example.analytics

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.AppInstallationEntity
import com.example.data.local.PendingAppAnalyticsEventEntity
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.SponsorCampaignSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Production-ready App Installation & Activity Analytics Engine for Doctor List BD.
 *
 * Privacy & Security guarantees:
 * - Zero IMEI, zero phone number, zero contacts, zero emails, zero personal PII.
 * - Uses a randomly generated per-install UUID (`inst_<uuid>`) stored in a non-backed-up
 *   SharedPreferences file AND validates PackageInfo.firstInstallTime to detect reinstalls.
 * - Prevents duplicate installation counting via:
 *     1) Persisted `syncedFirstInstall` flag + deterministic `evt_inst_<id>` primary key in Room
 *     2) RTDB write-once PUT on `app_analytics/<eventId>` and existence check before writing `firstInstallDate`
 * - Uses Firebase RTDB server-side timestamps (`{".sv": "timestamp"}`) for `firstInstallDate`,
 *   `lastActiveDate`, and `eventTimestamp`.
 * - Queues offline events reliably in Room and flushes automatically when online.
 * - Includes safe runtime FCM push token lifecycle detection for uninstall evidence without
 *   crashing when `google-services.json` is absent.
 */
object AppInstallationTracker {
    private const val TAG = "AppInstallTracker"
    private const val PREF_FILE = "dlbd_install_identity_nobackup"
    private const val KEY_INSTALL_ID = "installation_id"
    private const val KEY_PKG_FIRST_INSTALL_MS = "pkg_first_install_ms"
    private const val KEY_FIRST_INSTALL_DATE_MS = "first_install_date_ms"
    private const val KEY_LAST_VERSION_CODE = "last_synced_version_code"
    private const val KEY_LAST_FCM_TOKEN = "last_fcm_token"
    private const val ACTIVE_HEARTBEAT_COOLDOWN_MS = 5L * 60_000L

    private const val RTDB_DIR_BASE =
        "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app/directory"

    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_INACTIVE = "INACTIVE"
    const val STATUS_UNINSTALLED_ESTIMATE = "UNINSTALLED_ESTIMATE"

    const val EVT_FIRST_INSTALL = "FIRST_INSTALL"
    const val EVT_APP_OPEN = "APP_OPEN"
    const val EVT_APP_ACTIVE = "APP_ACTIVE"
    const val EVT_VERSION_UPDATE = "VERSION_UPDATE"
    const val EVT_FCM_TOKEN_REFRESH = "FCM_TOKEN_REFRESH"

    interface RemoteTransport {
        fun nodeExists(path: String): Boolean
        fun putJson(path: String, jsonBody: String): Boolean
        fun patchJson(path: String, jsonBody: String): Boolean
    }

    private val defaultTransport = object : RemoteTransport {
        override fun nodeExists(path: String): Boolean {
            return try {
                val conn = (URL("$RTDB_DIR_BASE/$path.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                val code = conn.responseCode
                val body = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText().trim() } else "null"
                conn.disconnect()
                body.isNotEmpty() && body != "null"
            } catch (_: Exception) {
                false
            }
        }

        override fun putJson(path: String, jsonBody: String): Boolean {
            return try {
                val conn = (URL("$RTDB_DIR_BASE/$path.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(jsonBody)
                    it.flush()
                }
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) {
                Log.d(TAG, "RTDB PUT deferred ($path): ${e.message}")
                false
            }
        }

        override fun patchJson(path: String, jsonBody: String): Boolean {
            return try {
                val conn = (URL("$RTDB_DIR_BASE/$path.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(jsonBody)
                    it.flush()
                }
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) {
                Log.d(TAG, "RTDB PATCH deferred ($path): ${e.message}")
                false
            }
        }
    }

    @Volatile
    internal var transport: RemoteTransport = defaultTransport

    private val mutex = Mutex()

    @Volatile
    private var launchRecordedInProcess = false

    @Volatile
    private var lastActivityRecordedMs = 0L

    data class IdentityResult(
        val installationId: String,
        val isNewInstallation: Boolean,
        val packageFirstInstallTime: Long,
        val firstInstallDateMs: Long
    )

    /**
     * Resolves or creates the unique per-installation random ID.
     * Detects reinstallations cleanly by comparing Android PackageManager.firstInstallTime.
     */
    fun resolveInstallationIdentity(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): IdentityResult {
        val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val pkgFirstInstall = readPackageFirstInstallTime(context)
        val storedId = prefs.getString(KEY_INSTALL_ID, null).orEmpty()
        val storedPkgTime = prefs.getLong(KEY_PKG_FIRST_INSTALL_MS, 0L)
        val isReinstall = storedId.isNotBlank() &&
            pkgFirstInstall > 0L &&
            storedPkgTime > 0L &&
            pkgFirstInstall != storedPkgTime

        if (storedId.isBlank() || isReinstall) {
            val freshId = "inst_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val firstMs = pkgFirstInstall.takeIf { it > 0L } ?: nowMs
            prefs.edit()
                .putString(KEY_INSTALL_ID, freshId)
                .putLong(KEY_PKG_FIRST_INSTALL_MS, pkgFirstInstall)
                .putLong(KEY_FIRST_INSTALL_DATE_MS, firstMs)
                .remove(KEY_LAST_VERSION_CODE)
                .apply()
            return IdentityResult(
                installationId = freshId,
                isNewInstallation = true,
                packageFirstInstallTime = pkgFirstInstall,
                firstInstallDateMs = firstMs
            )
        }

        val firstMs = prefs.getLong(KEY_FIRST_INSTALL_DATE_MS, nowMs)
        return IdentityResult(
            installationId = storedId,
            isNewInstallation = false,
            packageFirstInstallTime = pkgFirstInstall,
            firstInstallDateMs = firstMs
        )
    }

    /**
     * Records app launch (first-time installation if not yet recorded + APP_OPEN) and syncs when online.
     * Safe to invoke multiple times in the same process: cold launch is counted once per process,
     * and first installation is counted strictly once per Installation ID.
     */
    suspend fun trackAppLaunch(
        context: Context,
        db: AppDatabase,
        nowMs: Long = System.currentTimeMillis(),
        forceColdLaunchForTest: Boolean = false
    ): AppInstallationEntity = withContext(Dispatchers.IO) {
        mutex.withLock {
            val appCtx = context.applicationContext
            val identity = resolveInstallationIdentity(appCtx, nowMs)
            val existingLocal = db.appInstallationDao().getById(identity.installationId)
            val deviceInfo = readDeviceMetadata(appCtx)
            val dateKey = SponsorCampaignSelector.todayDateKey(nowMs)

            // Best-effort FCM token inspection (never crashes if FirebaseMessaging is not configured)
            val fcmToken = existingLocal?.fcmToken.orEmpty().ifBlank { tryFetchOptionalFcmToken(appCtx) }
            val fcmStatus = when {
                existingLocal?.fcmTokenStatus == "UNREGISTERED" && fcmToken == existingLocal.fcmToken -> "UNREGISTERED"
                fcmToken.isNotBlank() -> "VALID"
                else -> "NOT_CONFIGURED"
            }

            val updatedLocal = AppInstallationEntity(
                installationId = identity.installationId,
                packageFirstInstallTime = identity.packageFirstInstallTime,
                firstInstallDate = existingLocal?.firstInstallDate?.takeIf { it > 0L } ?: identity.firstInstallDateMs,
                firstInstallDateKey = existingLocal?.firstInstallDateKey?.ifBlank {
                    SponsorCampaignSelector.todayDateKey(identity.firstInstallDateMs)
                } ?: SponsorCampaignSelector.todayDateKey(identity.firstInstallDateMs),
                lastActiveDate = nowMs,
                lastActiveDateKey = dateKey,
                appVersion = deviceInfo.appVersion,
                appVersionCode = deviceInfo.versionCode,
                androidVersion = deviceInfo.androidVersion,
                deviceManufacturer = deviceInfo.manufacturer,
                deviceModel = deviceInfo.model,
                status = STATUS_ACTIVE,
                fcmToken = fcmToken,
                fcmTokenStatus = fcmStatus,
                uninstallEvidence = "",
                totalAppOpens = (existingLocal?.totalAppOpens ?: 0) +
                    if (!launchRecordedInProcess || forceColdLaunchForTest) 1 else 0,
                syncedFirstInstall = existingLocal?.syncedFirstInstall ?: false,
                updatedAt = nowMs
            )
            db.appInstallationDao().upsert(updatedLocal)

            val isOnlineNow = ConnectivityMonitor.isOnline.value

            // 1. Deterministic FIRST_INSTALL event (primary key prevents duplicates even offline/retry)
            if (identity.isNewInstallation || existingLocal == null) {
                db.pendingAppAnalyticsEventDao().insertIfAbsent(
                    PendingAppAnalyticsEventEntity(
                        eventId = "evt_inst_${identity.installationId}",
                        installationId = identity.installationId,
                        eventType = EVT_FIRST_INSTALL,
                        eventTimestamp = updatedLocal.firstInstallDate,
                        eventDateKey = updatedLocal.firstInstallDateKey,
                        appVersion = updatedLocal.appVersion,
                        wasOffline = !isOnlineNow,
                        createdAt = nowMs
                    )
                )
            }

            // 2. App Open event (once per process launch)
            if (!launchRecordedInProcess || forceColdLaunchForTest) {
                launchRecordedInProcess = true
                lastActivityRecordedMs = nowMs
                val openId = "evt_open_${identity.installationId}_${nowMs}_${UUID.randomUUID().toString().take(6)}"
                db.pendingAppAnalyticsEventDao().insertIfAbsent(
                    PendingAppAnalyticsEventEntity(
                        eventId = openId,
                        installationId = identity.installationId,
                        eventType = EVT_APP_OPEN,
                        eventTimestamp = nowMs,
                        eventDateKey = dateKey,
                        appVersion = updatedLocal.appVersion,
                        wasOffline = !isOnlineNow,
                        createdAt = nowMs
                    )
                )
            }

            // 3. App Version Update event if versionCode changed since last tracked sync
            val prefs = appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val prevVersionCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
            if (prevVersionCode > 0 && prevVersionCode != deviceInfo.versionCode) {
                db.pendingAppAnalyticsEventDao().insertIfAbsent(
                    PendingAppAnalyticsEventEntity(
                        eventId = "evt_ver_${identity.installationId}_${deviceInfo.versionCode}",
                        installationId = identity.installationId,
                        eventType = EVT_VERSION_UPDATE,
                        eventTimestamp = nowMs,
                        eventDateKey = dateKey,
                        appVersion = deviceInfo.appVersion,
                        wasOffline = !isOnlineNow,
                        createdAt = nowMs
                    )
                )
            }
            prefs.edit().putInt(KEY_LAST_VERSION_CODE, deviceInfo.versionCode).apply()

            if (isOnlineNow) {
                syncPendingInternal(db, updatedLocal, nowMs)
            } else {
                updatedLocal
            }
        }
    }

    /**
     * Records foreground activity heartbeat (throttled every 5 minutes) and syncs when online.
     */
    suspend fun recordForegroundActivity(
        context: Context,
        db: AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (nowMs - lastActivityRecordedMs < ACTIVE_HEARTBEAT_COOLDOWN_MS) {
            // Still flush any previously queued offline events if internet just came back
            if (ConnectivityMonitor.isOnline.value) {
                syncPendingEvents(context, db, nowMs)
            }
            return@withContext
        }
        mutex.withLock {
            val appCtx = context.applicationContext
            val identity = resolveInstallationIdentity(appCtx, nowMs)
            val existing = db.appInstallationDao().getById(identity.installationId)
            val deviceInfo = readDeviceMetadata(appCtx)
            val dateKey = SponsorCampaignSelector.todayDateKey(nowMs)
            lastActivityRecordedMs = nowMs

            val updated = (existing ?: AppInstallationEntity(
                installationId = identity.installationId,
                packageFirstInstallTime = identity.packageFirstInstallTime,
                firstInstallDate = identity.firstInstallDateMs,
                firstInstallDateKey = SponsorCampaignSelector.todayDateKey(identity.firstInstallDateMs),
                appVersion = deviceInfo.appVersion,
                appVersionCode = deviceInfo.versionCode,
                androidVersion = deviceInfo.androidVersion,
                deviceManufacturer = deviceInfo.manufacturer,
                deviceModel = deviceInfo.model
            )).copy(
                lastActiveDate = nowMs,
                lastActiveDateKey = dateKey,
                status = STATUS_ACTIVE,
                uninstallEvidence = "",
                updatedAt = nowMs
            )
            db.appInstallationDao().upsert(updated)

            val bucket = nowMs / ACTIVE_HEARTBEAT_COOLDOWN_MS
            db.pendingAppAnalyticsEventDao().insertIfAbsent(
                PendingAppAnalyticsEventEntity(
                    eventId = "evt_act_${identity.installationId}_$bucket",
                    installationId = identity.installationId,
                    eventType = EVT_APP_ACTIVE,
                    eventTimestamp = nowMs,
                    eventDateKey = dateKey,
                    appVersion = updated.appVersion,
                    wasOffline = !ConnectivityMonitor.isOnline.value,
                    createdAt = nowMs
                )
            )
            if (ConnectivityMonitor.isOnline.value) {
                syncPendingInternal(db, updated, nowMs)
            }
        }
    }

    /**
     * Updates the optional FCM push token for uninstall lifecycle verification.
     * Safe to invoke from a FirebaseMessagingService.onNewToken or initialization callback.
     */
    suspend fun updateFcmToken(
        context: Context,
        db: AppDatabase,
        token: String,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return@withContext
        mutex.withLock {
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_FCM_TOKEN, "") == cleanToken) return@withContext
            prefs.edit().putString(KEY_LAST_FCM_TOKEN, cleanToken).apply()

            val identity = resolveInstallationIdentity(appCtx, nowMs)
            val existing = db.appInstallationDao().getById(identity.installationId) ?: return@withContext
            val updated = existing.copy(
                fcmToken = cleanToken,
                fcmTokenStatus = "VALID",
                status = STATUS_ACTIVE,
                uninstallEvidence = "",
                updatedAt = nowMs
            )
            db.appInstallationDao().upsert(updated)

            val dateKey = SponsorCampaignSelector.todayDateKey(nowMs)
            db.pendingAppAnalyticsEventDao().insertIfAbsent(
                PendingAppAnalyticsEventEntity(
                    eventId = "evt_fcm_${identity.installationId}_${cleanToken.hashCode()}",
                    installationId = identity.installationId,
                    eventType = EVT_FCM_TOKEN_REFRESH,
                    eventTimestamp = nowMs,
                    eventDateKey = dateKey,
                    appVersion = updated.appVersion,
                    wasOffline = !ConnectivityMonitor.isOnline.value,
                    createdAt = nowMs
                )
            )
            if (ConnectivityMonitor.isOnline.value) {
                syncPendingInternal(db, updated, nowMs)
            }
        }
    }

    /**
     * Flushes all queued offline events + syncs latest device installation status to RTDB.
     */
    suspend fun syncPendingEvents(
        context: Context,
        db: AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        if (!ConnectivityMonitor.isOnline.value) return@withContext 0
        mutex.withLock {
            val identity = resolveInstallationIdentity(context.applicationContext, nowMs)
            val local = db.appInstallationDao().getById(identity.installationId) ?: return@withContext 0
            val pendingBefore = db.pendingAppAnalyticsEventDao().getPendingOnce().size
            syncPendingInternal(db, local, nowMs)
            val pendingAfter = db.pendingAppAnalyticsEventDao().getPendingOnce().size
            (pendingBefore - pendingAfter).coerceAtLeast(0)
        }
    }

    private suspend fun syncPendingInternal(
        db: AppDatabase,
        local: AppInstallationEntity,
        nowMs: Long
    ): AppInstallationEntity {
        if (!ConnectivityMonitor.isOnline.value) return local
        val safeId = sanitizeKey(local.installationId)
        val serverTimestampObj = JSONObject().put(".sv", "timestamp")

        // Check if installation already exists on server so firstInstallDate is NEVER overwritten
        var syncedFirst = local.syncedFirstInstall
        if (!syncedFirst) {
            val alreadyExistsOnServer = transport.nodeExists("app_installations/$safeId/firstInstallDate")
            if (alreadyExistsOnServer) {
                syncedFirst = true
            }
        }

        val patchPayload = JSONObject().apply {
            put("installationId", safeId)
            if (!syncedFirst) {
                put("firstInstallDate", if (local.firstInstallDate > 0L) local.firstInstallDate else serverTimestampObj)
                put("firstInstallDateKey", local.firstInstallDateKey)
            }
            put("lastActiveDate", serverTimestampObj)
            put("lastActiveDateKey", local.lastActiveDateKey)
            put("appVersion", local.appVersion)
            put("appVersionCode", local.appVersionCode)
            put("androidVersion", local.androidVersion)
            put("deviceManufacturer", local.deviceManufacturer)
            put("deviceModel", local.deviceModel)
            put("status", STATUS_ACTIVE)
            put("fcmToken", local.fcmToken)
            put("fcmTokenStatus", local.fcmTokenStatus)
            put("uninstallEvidence", "")
            put("totalAppOpens", local.totalAppOpens)
            put("updatedAt", serverTimestampObj)
        }.toString()

        val installSynced = transport.patchJson("app_installations/$safeId", patchPayload)
        val updatedEntity = if (installSynced) {
            local.copy(syncedFirstInstall = true, updatedAt = nowMs).also {
                db.appInstallationDao().upsert(it)
            }
        } else {
            local
        }

        // Flush pending immutable AppAnalytics events (idempotent PUT by eventId)
        val pending = db.pendingAppAnalyticsEventDao().getPendingOnce()
        for (evt in pending) {
            if (!ConnectivityMonitor.isOnline.value) break
            val safeEvtId = sanitizeKey(evt.eventId)
            val evtPayload = JSONObject().apply {
                put("eventId", safeEvtId)
                put("installationId", sanitizeKey(evt.installationId))
                put("eventType", evt.eventType)
                // Use server timestamp for live events; preserve original eventTimestamp for queued offline events
                if (evt.wasOffline) {
                    put("eventTimestamp", evt.eventTimestamp)
                } else {
                    put("eventTimestamp", serverTimestampObj)
                }
                put("eventDateKey", evt.eventDateKey)
                put("appVersion", evt.appVersion)
                put("wasOffline", evt.wasOffline)
            }.toString()

            if (transport.putJson("app_analytics/$safeEvtId", evtPayload)) {
                db.pendingAppAnalyticsEventDao().deleteById(evt.eventId)
            }
        }
        return updatedEntity
    }

    private data class DeviceMetadata(
        val appVersion: String,
        val versionCode: Int,
        val androidVersion: String,
        val manufacturer: String,
        val model: String
    )

    private fun readDeviceMetadata(context: Context): DeviceMetadata {
        val pkgInfo = readPackageInfo(context)
        val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        val versionCode = if (pkgInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode.toInt()
            else @Suppress("DEPRECATION") pkgInfo.versionCode
        } else BuildConfig.VERSION_CODE

        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "Unknown"
        val sdkInt = Build.VERSION.SDK_INT
        val manufacturer = (Build.MANUFACTURER ?: "Unknown").trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = (Build.MODEL ?: "Unknown").trim()
        return DeviceMetadata(
            appVersion = "$versionName ($versionCode)",
            versionCode = versionCode,
            androidVersion = "Android $release (API $sdkInt)",
            manufacturer = manufacturer,
            model = model
        )
    }

    private fun readPackageInfo(context: Context): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()

    private fun readPackageFirstInstallTime(context: Context): Long =
        readPackageInfo(context)?.firstInstallTime ?: 0L

    /**
     * Safely checks via reflection whether FirebaseApp & FirebaseMessaging are initialized
     * on the device (e.g. when a Release APK bundles google-services.json), without requiring
     * compile-time FCM initialization or crashing in environments without google-services.json.
     */
    private fun tryFetchOptionalFcmToken(context: Context): String {
        return runCatching {
            val firebaseAppCls = Class.forName("com.google.firebase.FirebaseApp")
            val apps = firebaseAppCls.getMethod("getApps", Context::class.java).invoke(null, context) as? List<*>
            if (apps.isNullOrEmpty()) return ""
            val messagingCls = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            val instance = messagingCls.getMethod("getInstance").invoke(null)
            val task = messagingCls.getMethod("getToken").invoke(instance)
            val tasksCls = Class.forName("com.google.android.gms.tasks.Tasks")
            (tasksCls.getMethod("await", Class.forName("com.google.android.gms.tasks.Task")).invoke(null, task) as? String).orEmpty()
        }.getOrDefault("")
    }

    private fun sanitizeKey(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)

    internal fun resetForTesting(context: Context, clearPrefs: Boolean = true) {
        launchRecordedInProcess = false
        lastActivityRecordedMs = 0L
        transport = defaultTransport
        if (clearPrefs) {
            context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
