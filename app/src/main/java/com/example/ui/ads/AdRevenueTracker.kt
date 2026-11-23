package com.example.ui.ads

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.PendingAdViewEventEntity
import com.example.data.local.SessionPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Direct Sponsor Advertisement View Analytics & Offline Queue Sync Engine.
 * - Counts impressions ONLY when an ad actually becomes visible on screen (viewport visibility verified).
 * - Enforces 30-second per-ad/placement duplicate protection + per-day/per-device unique view tracking.
 * - Queues offline view events in Room `pending_ad_view_events` with unique `eventKey` dedup,
 *   and synchronizes automatically upon reconnection with zero third-party ad SDKs.
 */
object AdRevenueTracker {
    private const val TAG = "SponsorViewAnalytics"
    private const val RTDB_DIR_BASE =
        "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app/directory"
    private const val DUPLICATE_COOLDOWN_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory duplicate impression shield: key = "adId|slotKey" -> last recorded timestamp
    private val lastImpressionBySlot = ConcurrentHashMap<String, Long>()

    // Per-day unique view shield in current process: key = "dateKey|adId"
    private val uniqueSeenToday = ConcurrentHashMap.newKeySet<String>()

    /**
     * Evaluates duplicate protection and records a visible Direct Sponsor impression.
     * - Online: pushes directly to RTDB analytics nodes (and queues locally if push fails).
     * - Offline: queues in Room `pending_ad_view_events` with deterministic `eventKey` for dedup.
     */
    suspend fun recordVisibleImpression(
        db: AppDatabase,
        prefs: SessionPreferences,
        adId: String,
        packageId: String,
        slotKey: String,
        isOnline: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        if (adId.isBlank()) return@withContext false

        val dedupKey = "$adId|$slotKey"
        val prev = lastImpressionBySlot[dedupKey] ?: 0L
        if (nowMs - prev < DUPLICATE_COOLDOWN_MS) {
            return@withContext false
        }
        lastImpressionBySlot[dedupKey] = nowMs

        val dateKey = SponsorCampaignSelector.todayDateKey(nowMs)
        val deviceHash = prefs.getOrCreateDeviceHash()
        val uniqueKey = "$dateKey|$adId"
        val isUniqueCandidate = uniqueSeenToday.add(uniqueKey)
        // 30-second bucket ensures duplicate events cannot double-insert into Room queue
        val bucket = nowMs / DUPLICATE_COOLDOWN_MS
        val eventKey = "imp_${sanitizeKey(adId)}_${sanitizeKey(slotKey)}_${dateKey}_$bucket"

        val event = PendingAdViewEventEntity(
            eventKey = eventKey,
            adId = adId,
            packageId = packageId,
            dateKey = dateKey,
            deviceHash = deviceHash,
            isUnique = isUniqueCandidate,
            eventType = "IMPRESSION",
            wasOffline = !isOnline,
            createdAt = nowMs
        )

        if (isOnline && ConnectivityMonitor.isOnline.value) {
            val pushed = pushViewEventToServer(event)
            if (!pushed) {
                db.pendingAdViewEventDao().insertIfAbsent(event)
            }
        } else {
            db.pendingAdViewEventDao().insertIfAbsent(event)
        }
        true
    }

    /**
     * Records a legitimate user click on a Direct Sponsor CTA (Website/Call).
     */
    suspend fun recordSponsorClick(
        db: AppDatabase,
        prefs: SessionPreferences,
        adId: String,
        packageId: String,
        isOnline: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (adId.isBlank()) return@withContext
        val dateKey = SponsorCampaignSelector.todayDateKey(nowMs)
        val deviceHash = prefs.getOrCreateDeviceHash()
        val eventKey = "clk_${sanitizeKey(adId)}_${nowMs / 5_000L}"
        val event = PendingAdViewEventEntity(
            eventKey = eventKey,
            adId = adId,
            packageId = packageId,
            dateKey = dateKey,
            deviceHash = deviceHash,
            isUnique = false,
            eventType = "CLICK",
            wasOffline = !isOnline,
            createdAt = nowMs
        )
        if (isOnline && ConnectivityMonitor.isOnline.value) {
            val pushed = pushViewEventToServer(event)
            if (!pushed) {
                db.pendingAdViewEventDao().insertIfAbsent(event)
            }
        } else {
            db.pendingAdViewEventDao().insertIfAbsent(event)
        }
    }

    /**
     * Flushes all queued offline view events from Room to Firebase RTDB upon reconnection.
     * Deletes each event ONLY after verified server HTTP 200 acceptance.
     */
    suspend fun flushQueuedViewEvents(db: AppDatabase): Int = withContext(Dispatchers.IO) {
        if (!ConnectivityMonitor.isOnline.value) return@withContext 0
        val pending = db.pendingAdViewEventDao().getPendingOnce()
        var syncedCount = 0
        for (event in pending) {
            if (!ConnectivityMonitor.isOnline.value) break
            if (pushViewEventToServer(event)) {
                db.pendingAdViewEventDao().deleteByKey(event.eventKey)
                syncedCount++
            }
        }
        if (syncedCount > 0) {
            Log.d(TAG, "Synchronized $syncedCount queued Direct Sponsor view events.")
        }
        syncedCount
    }

    fun recordSponsorDownload(sponsorId: String) {
        if (sponsorId.isBlank() || !ConnectivityMonitor.isOnline.value) return
        scope.launch {
            val dateKey = SponsorCampaignSelector.todayDateKey()
            val safeId = sanitizeKey(sponsorId)
            patchIncrement("$RTDB_DIR_BASE/ad_analytics/daily/$dateKey/$safeId.json", "cacheDownloads", 1)
            patchIncrement("$RTDB_DIR_BASE/ad_metrics/$dateKey/sponsors/$safeId.json", "downloads", 1)
        }
    }

    fun trackOnlineSession() {
        if (!ConnectivityMonitor.isOnline.value) return
        scope.launch {
            val dateKey = SponsorCampaignSelector.todayDateKey()
            patchIncrement("$RTDB_DIR_BASE/ad_metrics/$dateKey/sessions.json", "onlineSessions", 1)
        }
    }

    suspend fun flushOfflineLocalCounters(prefs: SessionPreferences) = withContext(Dispatchers.IO) {
        if (!ConnectivityMonitor.isOnline.value) return@withContext
        val (displays, sessions) = prefs.consumePendingOfflineStats()
        if (displays <= 0 && sessions <= 0) return@withContext
        val dateKey = SponsorCampaignSelector.todayDateKey()
        if (displays > 0) {
            patchIncrement("$RTDB_DIR_BASE/ad_metrics/$dateKey/offline_local.json", "offlineSponsorDisplays", displays)
        }
        if (sessions > 0) {
            patchIncrement("$RTDB_DIR_BASE/ad_metrics/$dateKey/offline_local.json", "offlineSessions", sessions)
        }
    }

    internal fun normalizeNetworkKey(raw: String): String? = when (raw.trim().lowercase()) {
        "startio", "start.io", "start_io" -> "startio"
        "unity", "unityads", "unity_ads" -> "unity"
        "wortise", "wortiseads", "wortise_ads" -> "wortise"
        else -> null
    }

    internal fun normalizeFormatKey(raw: String): String = when (raw.trim().lowercase()) {
        "banner" -> "banner"
        "native", "mrec" -> "native"
        "interstitial" -> "interstitial"
        "app_open", "appopen" -> "app_open"
        "rewarded", "rewarded_video" -> "rewarded"
        else -> "banner"
    }

    internal fun clearMemoryCooldownsForTesting() {
        lastImpressionBySlot.clear()
        uniqueSeenToday.clear()
    }

    private fun pushViewEventToServer(event: PendingAdViewEventEntity): Boolean {
        val safeAdId = sanitizeKey(event.adId)
        val safePkgId = sanitizeKey(event.packageId.ifBlank { "admin_direct" })
        val dateKey = event.dateKey
        val placementChannel = inferPlacementChannel(event.eventKey)

        if (event.eventType == "CLICK") {
            val okDaily = patchIncrement("$RTDB_DIR_BASE/ad_analytics/daily/$dateKey/$safeAdId.json", "clicks", 1)
            patchIncrement("$RTDB_DIR_BASE/ad_analytics/totals/$safeAdId.json", "clicks", 1)
            patchIncrement("$RTDB_DIR_BASE/ad_analytics/placements/$placementChannel.json", "clicks", 1)
            return okDaily
        }

        // Server-side duplicate protection check for unique view per device per day
        var countUniqueNow = false
        if (event.isUnique && event.deviceHash.isNotBlank()) {
            val uniqueUrl = "$RTDB_DIR_BASE/ad_analytics/unique_keys/$dateKey/$safeAdId/${sanitizeKey(event.deviceHash)}.json"
            val alreadyExists = checkJsonExists(uniqueUrl)
            if (!alreadyExists) {
                if (putBooleanTrue(uniqueUrl)) {
                    countUniqueNow = true
                }
            }
        }

        val payload = JSONObject().apply {
            put("totalImpressions", JSONObject().put(".sv", JSONObject().put("increment", 1)))
            if (countUniqueNow) {
                put("uniqueViews", JSONObject().put(".sv", JSONObject().put("increment", 1)))
            }
            if (event.wasOffline) {
                put("offlineSyncedViews", JSONObject().put(".sv", JSONObject().put("increment", 1)))
            }
            put("packageId", safePkgId)
            put("updatedAt", System.currentTimeMillis())
        }.toString()

        val okDaily = patchRaw("$RTDB_DIR_BASE/ad_analytics/daily/$dateKey/$safeAdId.json", payload)
        if (!okDaily) return false

        // Update advertisement-wise totals, placement-wise report, sponsor counter, and package-wise report
        patchRaw("$RTDB_DIR_BASE/ad_analytics/totals/$safeAdId.json", payload)
        patchRaw("$RTDB_DIR_BASE/ad_analytics/packages/$safePkgId.json", payload)
        patchRaw("$RTDB_DIR_BASE/ad_analytics/placements/$placementChannel.json", payload)
        val sponsorInc = JSONObject().apply {
            put("totalViews", JSONObject().put(".sv", JSONObject().put("increment", 1)))
            if (countUniqueNow) {
                put("uniqueViews", JSONObject().put(".sv", JSONObject().put("increment", 1)))
            }
        }.toString()
        patchRaw("$RTDB_DIR_BASE/sponsors/$safeAdId.json", sponsorInc)
        return true
    }

    private fun inferPlacementChannel(eventKey: String): String {
        val k = eventKey.lowercase()
        return when {
            "popup" in k || "fullscreen" in k -> "POPUP"
            "native" in k -> "NATIVE_300x200"
            "about" in k -> "ABOUT_US"
            else -> "BANNER"
        }
    }

    private fun checkJsonExists(urlStr: String): Boolean {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText().trim() } else "null"
            conn.disconnect()
            body.isNotEmpty() && body != "null"
        } catch (_: Exception) {
            false
        }
    }

    private fun putBooleanTrue(urlStr: String): Boolean {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write("true") }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun patchIncrement(urlStr: String, field: String, delta: Int): Boolean {
        val payload = JSONObject()
            .put(field, JSONObject().put(".sv", JSONObject().put("increment", delta)))
            .put("updatedAt", System.currentTimeMillis())
            .toString()
        return patchRaw(urlStr, payload)
    }

    private fun patchRaw(urlStr: String, payload: String): Boolean {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.d(TAG, "Deferred analytics sync ($urlStr): ${e.message}")
            false
        }
    }

    private fun sanitizeKey(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(56)
}
