package com.example.ui.ads

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Production Hybrid Ad Telemetry tracker for Doctor List BD.
 * - Reports ONLY legitimate online SDK callbacks (requests, loads, errors, impressions, clicks, completed)
 *   for Unity Ads & Start.io.
 * - Tracks online Direct Sponsor performance (impressions, clicks, banner downloads) per Sponsor ID.
 * - Tracks offline local displays & offline sessions SEPARATELY under `offline_local` so offline displays
 *   are NEVER counted as verified advertising network impressions or artificial clicks.
 */
object AdRevenueTracker {
    private const val TAG = "AdRevenueTracker"
    private const val RTDB_METRICS_BASE =
        "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app/directory/ad_metrics"

    const val NETWORK_STARTIO = "startio"
    const val NETWORK_UNITY = "unity"

    const val FORMAT_BANNER = "banner"
    const val FORMAT_NATIVE = "native"
    const val FORMAT_APP_OPEN = "app_open"
    const val FORMAT_REWARDED = "rewarded"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun todayDhakaKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        return sdf.format(Date())
    }

    // --- Ad Network SDK Callbacks (Online Only) ---
    fun recordRequest(network: String, format: String) {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("$network/$format", "requests", 1)
    }

    fun recordLoadSuccess(network: String, format: String) {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("$network/$format", "loads", 1)
    }

    fun recordNetworkError(network: String, format: String) {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("$network/$format", "errors", 1)
    }

    fun recordImpression(network: String, format: String) {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("$network/$format", "impressions", 1)
    }

    fun recordClick(network: String, format: String) {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("$network/$format", "clicks", 1)
    }

    fun recordCompleted(network: String, format: String) {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("$network/$format", "completed", 1)
    }

    // --- Direct Sponsor Telemetry (Online Only) ---
    fun recordSponsorOnlineDisplay(sponsorId: String) {
        if (!ConnectivityMonitor.isOnline.value || sponsorId.isBlank()) return
        incrementPath("sponsors/${sanitizeKey(sponsorId)}", "onlineImpressions", 1)
    }

    fun recordSponsorOnlineClick(sponsorId: String) {
        if (!ConnectivityMonitor.isOnline.value || sponsorId.isBlank()) return
        incrementPath("sponsors/${sanitizeKey(sponsorId)}", "onlineClicks", 1)
    }

    fun recordSponsorDownload(sponsorId: String) {
        if (sponsorId.isBlank()) return
        incrementPath("sponsors/${sanitizeKey(sponsorId)}", "downloads", 1)
    }

    // --- Session & Strictly Separated Local Offline Counters ---
    fun recordOnlineSession() {
        if (!ConnectivityMonitor.isOnline.value) return
        incrementPath("sessions", "onlineSessions", 1)
    }

    /**
     * Flushes locally recorded offline events once connectivity resumes,
     * stored strictly under `offline_local` (never merged with verified network impressions).
     */
    fun syncSeparatedOfflineStats(offlineSponsorDisplays: Int, offlineSessions: Int) {
        if (!ConnectivityMonitor.isOnline.value) return
        if (offlineSponsorDisplays > 0) {
            incrementPath("offline_local", "offlineSponsorDisplays", offlineSponsorDisplays)
        }
        if (offlineSessions > 0) {
            incrementPath("offline_local", "offlineSessions", offlineSessions)
        }
    }

    private fun sanitizeKey(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)

    private fun incrementPath(subPath: String, field: String, delta: Int) {
        if (delta <= 0) return
        scope.launch {
            try {
                val dateKey = todayDhakaKey()
                val endpoint = "$RTDB_METRICS_BASE/$dateKey/$subPath.json"
                val incrementObj = JSONObject().put(".sv", JSONObject().put("increment", delta))
                val payload = JSONObject()
                    .put(field, incrementObj)
                    .put("updatedAt", System.currentTimeMillis())
                    .toString()

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                if (code !in 200..299) {
                    Log.w(TAG, "Metric sync returned HTTP $code for $subPath/$field")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Offline metric skipped ($subPath/$field): ${e.message}")
            }
        }
    }
}
