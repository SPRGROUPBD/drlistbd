package com.example.data.remote

import android.util.Log
import com.example.ui.ads.AdScreenTargeting
import com.example.ui.ads.AdTargetScreens
import com.example.ui.ads.DynamicAdPackage
import com.example.ui.ads.FairAdRotationEngine
import com.example.ui.ads.ManagedAdvertiserCampaign
import com.example.ui.ads.StrictPlacementTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class DynamicAdSystemSnapshot(
    val packages: List<DynamicAdPackage> = emptyList(),
    val campaigns: List<ManagedAdvertiserCampaign> = emptyList(),
    val rotationCounters: Map<String, Long> = emptyMap()
)

/**
 * Firebase RTDB REST integration for the Dynamic Package Management & Fair Rotation System
 * on existing project `octor-aad88`.
 *
 * Reads:
 * - adPackages/
 * - advertisers/
 * - advertisements/
 * - adScreenTargeting/
 * - adRotationCounters/
 *
 * Writes:
 * - adRotationCounters/{placement}/{screenId} (atomic ETag conditional compare-and-swap)
 * - adDisplayLogs/{logId} (deduplicated real impression & click telemetry)
 */
object DynamicAdRemoteService {
    private const val TAG = "DynamicAdRemote"
    private const val RTDB_ROOT = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"

    suspend fun fetchDynamicAdSystem(): Result<DynamicAdSystemSnapshot> = withContext(Dispatchers.IO) {
        try {
            val pkgRoot = getJsonObject("$RTDB_ROOT/adPackages.json")
            val advRoot = getJsonObject("$RTDB_ROOT/advertisers.json")
            val adsRoot = getJsonObject("$RTDB_ROOT/advertisements.json")
            val targetRoot = getJsonObject("$RTDB_ROOT/adScreenTargeting.json")
            val counterRoot = getJsonObject("$RTDB_ROOT/adRotationCounters.json")

            val packages = mutableListOf<DynamicAdPackage>()
            val packageMap = mutableMapOf<String, DynamicAdPackage>()
            if (pkgRoot != null) {
                val keys = pkgRoot.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val o = pkgRoot.optJSONObject(id) ?: continue
                    val pt = o.optString("placementType", "").trim()
                    if (!StrictPlacementTypes.isAllowed(pt)) continue
                    val (defW, defH) = StrictPlacementTypes.defaultDimensions(pt)
                    val pkg = DynamicAdPackage(
                        packageId = id,
                        packageName = o.optString("packageName", "").trim(),
                        placementType = pt,
                        width = o.optInt("width", defW).takeIf { it > 0 } ?: defW,
                        height = o.optInt("height", defH).takeIf { it > 0 } ?: defH,
                        durationDays = o.optInt("durationDays", 30).coerceAtLeast(1),
                        price = o.optDouble("price", 0.0),
                        description = o.optString("description", "").trim(),
                        status = o.optString("status", "active").trim(),
                        createdAt = o.optLong("createdAt", 0L),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                    packages.add(pkg)
                    packageMap[id] = pkg
                }
            }

            val campaigns = mutableListOf<ManagedAdvertiserCampaign>()
            if (adsRoot != null) {
                val keys = adsRoot.keys()
                while (keys.hasNext()) {
                    val adKey = keys.next()
                    val adObj = adsRoot.optJSONObject(adKey) ?: continue
                    val advertiserId = adObj.optString("advertiserId", adObj.optString("advertiserUid", adKey)).trim()
                    val placementType = normalizePlacementType(
                        adObj.optString("placementType", adObj.optString("placement", ""))
                    )
                    if (!StrictPlacementTypes.isAllowed(placementType)) continue
                    val advObj = advRoot?.optJSONObject(advertiserId)
                    val tgtObj = targetRoot?.optJSONObject(advertiserId) ?: adObj.optJSONObject("screenTargeting")
                    val targeting = parseTargeting(tgtObj, placementType)
                    val pkgId = adObj.optString("packageId", advObj?.optString("packageId", "") ?: "").trim()
                    val pkg = packageMap[pkgId]
                    val (defW, defH) = StrictPlacementTypes.defaultDimensions(placementType)

                    campaigns.add(
                        ManagedAdvertiserCampaign(
                            advertiserId = advertiserId,
                            advertisementId = adObj.optString("adId", adKey).trim(),
                            advertiserName = advObj?.optString("advertiserName", advObj.optString("name", ""))?.trim().orEmpty(),
                            companyName = advObj?.optString("companyName", advObj.optString("businessName", ""))?.trim()
                                .orEmpty()
                                .ifBlank { adObj.optString("companyName", adObj.optString("title", "Sponsor")) },
                            logoUrl = advObj?.optString("logoUrl", advObj.optString("photo", ""))?.trim().orEmpty(),
                            imageUrl = adObj.optString("imageUrl", "").trim(),
                            title = adObj.optString("title", "").trim(),
                            description = adObj.optString("description", "").trim(),
                            destinationUrl = adObj.optString("destinationUrl", adObj.optString("websiteUrl", "")).trim(),
                            placementType = placementType,
                            width = adObj.optInt("width", pkg?.width ?: defW),
                            height = adObj.optInt("height", pkg?.height ?: defH),
                            advertiserStatus = advObj?.optString("status", adObj.optString("status", "Active"))?.trim().orEmpty(),
                            adStatus = adObj.optString("status", "Pending").trim(),
                            paymentStatus = advObj?.optString("paymentStatus", adObj.optString("paymentStatus", "Paid"))?.trim().orEmpty(),
                            packageId = pkgId,
                            packageValid = pkg == null || pkg.isActive || adObj.optBoolean("hasPurchaseSnapshot", true),
                            startDate = adObj.optLong("startDate", 0L),
                            endDate = adObj.optLong("endDate", 0L),
                            createdAt = advObj?.optLong("createdAt", adObj.optLong("createdAt", 0L))
                                ?: adObj.optLong("createdAt", 0L),
                            targeting = targeting
                        )
                    )
                }
            }

            val counters = parseCounters(counterRoot)
            FairAdRotationEngine.seedCounters(counters)

            Result.success(
                DynamicAdSystemSnapshot(
                    packages = packages,
                    campaigns = campaigns,
                    rotationCounters = counters
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchDynamicAdSystem failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Atomically increments `adRotationCounters/{placementGroup}/{screenId}` in Firebase RTDB
     * using HTTP ETag conditional PUT (REST equivalent of RTDB transaction) to prevent concurrent skips/duplicates.
     */
    suspend fun atomicIncrementCounterInRtdb(
        placementType: String,
        screenId: String,
        fallbackLocalValue: Long
    ): Long = withContext(Dispatchers.IO) {
        val group = when (placementType) {
            StrictPlacementTypes.BANNER_300x50 -> "banner"
            StrictPlacementTypes.NATIVE_300x200 -> "native"
            StrictPlacementTypes.POPUP_300x200 -> "popup"
            else -> return@withContext fallbackLocalValue
        }
        val cleanScreen = screenId.replace(Regex("[^a-zA-Z0-9_]"), "")
        val url = "$RTDB_ROOT/adRotationCounters/$group/$cleanScreen.json"

        repeat(3) {
            var getConn: HttpURLConnection? = null
            var putConn: HttpURLConnection? = null
            try {
                getConn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("X-Firebase-ETag", "true")
                }
                val etag = getConn.getHeaderField("ETag")
                val body = if (getConn.responseCode in 200..299) {
                    getConn.inputStream.bufferedReader().use { it.readText() }.trim()
                } else "0"
                val remoteVal = parseCounterValue(body)
                val nextVal = maxOf(remoteVal + 1L, fallbackLocalValue)

                val payload = JSONObject().apply {
                    put("index", nextVal)
                    put("updatedAt", System.currentTimeMillis())
                }.toString()

                putConn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    if (!etag.isNullOrBlank()) {
                        setRequestProperty("if-match", etag)
                    }
                }
                OutputStreamWriter(putConn.outputStream, Charsets.UTF_8).use { it.write(payload) }
                val code = putConn.responseCode
                if (code in 200..299) {
                    return@withContext nextVal
                }
                if (code != 412) {
                    return@withContext fallbackLocalValue
                }
            } catch (_: Exception) {
                return@withContext fallbackLocalValue
            } finally {
                getConn?.disconnect()
                putConn?.disconnect()
            }
        }
        fallbackLocalValue
    }

    /**
     * Logs a real visible impression or click into `adDisplayLogs/{logId}` for Super Admin reporting.
     */
    suspend fun logAdDisplayEvent(
        advertiserId: String,
        advertisementId: String,
        placementType: String,
        screenId: String,
        eventType: String // IMPRESSION | CLICK
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val logId = "log_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val payload = JSONObject().apply {
                put("logId", logId)
                put("advertiserId", advertiserId)
                put("advertisementId", advertisementId)
                put("placementType", placementType)
                put("screenId", screenId)
                put("eventType", eventType)
                put("timestamp", System.currentTimeMillis())
            }
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$RTDB_ROOT/adDisplayLogs/$logId.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                conn.responseCode
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun normalizePlacementType(raw: String): String = when (raw.trim().lowercase()) {
        "banner_300x50", "banner", "300x50" -> StrictPlacementTypes.BANNER_300x50
        "native_300x200", "native", "native_banner" -> StrictPlacementTypes.NATIVE_300x200
        "popup_300x200", "popup", "app_open_popup" -> StrictPlacementTypes.POPUP_300x200
        else -> raw.trim()
    }

    private fun parseTargeting(obj: JSONObject?, placementType: String): AdScreenTargeting {
        if (obj == null) {
            return when (placementType) {
                StrictPlacementTypes.BANNER_300x50 -> AdScreenTargeting(bannerAllScreens = true)
                StrictPlacementTypes.POPUP_300x200 -> AdScreenTargeting(popupAppLaunch = true)
                else -> AdScreenTargeting()
            }
        }
        val bannerObj = obj.optJSONObject("banner")
        val nativeObj = obj.optJSONObject("native")
        val popupObj = obj.optJSONObject("popup")

        val bannerAll = bannerObj?.optBoolean("allScreens", false) ?: false
        val bannerScreens = buildSet {
            if (bannerObj != null) {
                for (scr in AdTargetScreens.BANNER_SUPPORTED_SCREENS) {
                    if (bannerObj.optBoolean(scr, false)) add(scr)
                }
            }
        }
        val nativeScreens = buildSet {
            if (nativeObj != null) {
                for (scr in AdTargetScreens.NATIVE_SUPPORTED_SCREENS) {
                    if (nativeObj.optBoolean(scr, false)) add(scr)
                }
            }
        }
        val popupLaunch = popupObj?.optBoolean("appLaunch", false)
            ?: (placementType == StrictPlacementTypes.POPUP_300x200)

        return AdScreenTargeting(
            bannerAllScreens = bannerAll,
            bannerScreens = bannerScreens,
            nativeScreens = nativeScreens,
            popupAppLaunch = popupLaunch
        )
    }

    private fun parseCounters(root: JSONObject?): Map<String, Long> {
        if (root == null) return emptyMap()
        val out = mutableMapOf<String, Long>()
        val mapping = listOf(
            "banner" to StrictPlacementTypes.BANNER_300x50,
            "native" to StrictPlacementTypes.NATIVE_300x200,
            "popup" to StrictPlacementTypes.POPUP_300x200
        )
        for ((groupKey, placementType) in mapping) {
            val gObj = root.optJSONObject(groupKey) ?: continue
            val screenKeys = gObj.keys()
            while (screenKeys.hasNext()) {
                val screenId = screenKeys.next()
                val raw = gObj.opt(screenId)
                val value = when (raw) {
                    is Number -> raw.toLong()
                    is JSONObject -> raw.optLong("index", 0L)
                    else -> 0L
                }
                if (value >= 0L) {
                    out[FairAdRotationEngine.counterKey(placementType, screenId)] = value
                }
            }
        }
        return out
    }

    private fun parseCounterValue(body: String): Long {
        if (body.isBlank() || body == "null") return 0L
        body.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        return runCatching { JSONObject(body).optLong("index", 0L).coerceAtLeast(0L) }.getOrDefault(0L)
    }

    private fun getJsonObject(urlString: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            if (body.isEmpty() || body == "null") null else JSONObject(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
