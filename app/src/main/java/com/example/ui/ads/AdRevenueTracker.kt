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
 * Lightweight, real-time Ad Telemetry tracker for Doctor List BD.
 * Reports ONLY real SDK impression, click, and completed-reward callbacks from
 * Start.io & Unity Ads to Firebase Realtime Database using atomic server increments
 * so the Admin Panel displays accurate live Start.io & Unity Ads performance & revenue.
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

    private fun todayUtcKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        return sdf.format(Date())
    }

    fun recordImpression(network: String, format: String) {
        incrementField(network, format, "impressions")
    }

    fun recordClick(network: String, format: String) {
        incrementField(network, format, "clicks")
    }

    fun recordCompleted(network: String, format: String) {
        incrementField(network, format, "completed")
    }

    private fun incrementField(network: String, format: String, field: String) {
        scope.launch {
            try {
                val dateKey = todayUtcKey()
                val endpoint = "$RTDB_METRICS_BASE/$dateKey/$network/$format.json"
                val incrementObj = JSONObject().put(".sv", JSONObject().put("increment", 1))
                val payload = JSONObject()
                    .put(field, incrementObj)
                    .put("updatedAt", System.currentTimeMillis())
                    .toString()

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    // Use POST + X-HTTP-Method-Override: PATCH for full Android HttpURLConnection compatibility
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
                    Log.w(TAG, "Metric sync returned HTTP $code for $network/$format/$field")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Offline metric skipped ($network/$format/$field): ${e.message}")
            }
        }
    }
}
