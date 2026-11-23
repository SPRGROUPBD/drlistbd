package com.example.data.remote

import android.util.Log
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleUserAccount
import com.example.data.local.AdvertiserSession
import com.example.ui.ads.SponsorCampaignSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class FavoriteCategory(val nodeKey: String) {
    DOCTOR("doctors"),
    HOSPITAL("hospitals"),
    DIAGNOSTIC("diagnostics"),
    CHAMBER("chambers")
}

data class UserFavoritesSnapshot(
    val doctorIds: Set<String> = emptySet(),
    val hospitalIds: Set<String> = emptySet(),
    val diagnosticIds: Set<String> = emptySet(),
    val chamberIds: Set<String> = emptySet()
)

data class GoogleAdvertiserProfile(
    val uid: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val companyName: String,
    val category: String,
    val address: String,
    val photoUrl: String,
    val websiteUrl: String,
    val status: String,
    val createdAt: Long
) {
    fun toAdvertiserSession(): AdvertiserSession = AdvertiserSession(
        id = uid,
        businessName = companyName,
        ownerName = fullName,
        mobile = phone,
        email = email,
        address = address,
        category = category,
        photoUrl = photoUrl,
        websiteUrl = websiteUrl,
        authProvider = "GOOGLE"
    )
}

/**
 * Authenticated UID-scoped Firebase Realtime Database sync service for:
 * - users/{uid}
 * - visitors/{uid}
 * - favorites/{uid}/{doctors, hospitals, diagnostics, chambers}
 * - advertisers/{uid} (plus compatibility mirror in directory/advertisers/{uid})
 * - advertisements/{adId}
 */
object FirebaseUserSyncService {
    private const val TAG = "FirebaseUserSync"
    private const val RTDB_ROOT = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"

    private suspend fun buildAuthedUrl(path: String): String {
        val cleanPath = path.trimStart('/')
        val token = GoogleAuthManager.getIdToken(false)
        return if (!token.isNullOrBlank()) {
            "$RTDB_ROOT/$cleanPath.json?auth=${URLEncoder.encode(token, "UTF-8")}"
        } else {
            "$RTDB_ROOT/$cleanPath.json"
        }
    }

    suspend fun ensureVisitorAccount(user: GoogleUserAccount): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val existingUser = getJsonObject(buildAuthedUrl("users/${user.uid}"))
            val existingRole = existingUser?.optString("role", "")?.takeIf { it.isNotBlank() } ?: "visitor"
            val createdAt = existingUser?.optLong("createdAt", 0L)?.takeIf { it > 0L } ?: now
            val existingStatus = existingUser?.optString("status", "")?.takeIf { it.isNotBlank() } ?: "ACTIVE"

            val userPayload = JSONObject().apply {
                put("uid", user.uid)
                put("name", user.displayName.take(120))
                put("email", user.email.take(160))
                put("photo", user.photoUrl.take(500))
                put("role", existingRole)
                put("createdAt", createdAt)
                put("updatedAt", now)
                put("status", existingStatus)
            }
            val visitorExisting = getJsonObject(buildAuthedUrl("visitors/${user.uid}"))
            val visitorCreated = visitorExisting?.optLong("createdAt", 0L)?.takeIf { it > 0L } ?: now
            val visitorPayload = JSONObject().apply {
                put("uid", user.uid)
                put("name", user.displayName.take(120))
                put("email", user.email.take(160))
                put("photo", user.photoUrl.take(500))
                put("createdAt", visitorCreated)
                put("lastLoginAt", now)
            }

            val okUser = putJson(buildAuthedUrl("users/${user.uid}"), userPayload.toString())
            val okVisitor = putJson(buildAuthedUrl("visitors/${user.uid}"), visitorPayload.toString())
            if (okUser && okVisitor) Result.success(Unit)
            else Result.failure(IllegalStateException("Could not sync visitor profile online."))
        } catch (e: Exception) {
            Log.w(TAG, "ensureVisitorAccount failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchUserFavorites(uid: String): Result<UserFavoritesSnapshot> = withContext(Dispatchers.IO) {
        try {
            if (uid.isBlank()) return@withContext Result.success(UserFavoritesSnapshot())
            val root = getJsonObject(buildAuthedUrl("favorites/$uid"))
                ?: return@withContext Result.success(UserFavoritesSnapshot())

            fun extractKeys(nodeName: String): Set<String> {
                val obj = root.optJSONObject(nodeName) ?: return emptySet()
                val out = LinkedHashSet<String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (v != null && v != JSONObject.NULL && v != false) {
                        out.add(k)
                    }
                }
                return out
            }

            Result.success(
                UserFavoritesSnapshot(
                    doctorIds = extractKeys(FavoriteCategory.DOCTOR.nodeKey),
                    hospitalIds = extractKeys(FavoriteCategory.HOSPITAL.nodeKey),
                    diagnosticIds = extractKeys(FavoriteCategory.DIAGNOSTIC.nodeKey),
                    chamberIds = extractKeys(FavoriteCategory.CHAMBER.nodeKey)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserFavorites failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncFavoriteItem(
        uid: String,
        category: FavoriteCategory,
        itemId: String,
        saved: Boolean,
        label: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = sanitizeFirebaseKey(itemId)
            if (uid.isBlank() || cleanId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid UID or item ID"))
            }
            val url = buildAuthedUrl("favorites/$uid/${category.nodeKey}/$cleanId")
            val ok = if (saved) {
                val payload = JSONObject().apply {
                    put("id", cleanId)
                    put("label", label.take(160))
                    put("savedAt", System.currentTimeMillis())
                }
                putJson(url, payload.toString())
            } else {
                deleteJson(url)
            }
            if (ok) Result.success(Unit)
            else Result.failure(IllegalStateException("Failed to sync favorite to Firebase"))
        } catch (e: Exception) {
            Log.w(TAG, "syncFavoriteItem failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchGoogleAdvertiser(uid: String): Result<GoogleAdvertiserProfile?> = withContext(Dispatchers.IO) {
        try {
            if (uid.isBlank()) return@withContext Result.success(null)
            val obj = getJsonObject(buildAuthedUrl("advertisers/$uid"))
                ?: getJsonObject(buildAuthedUrl("directory/advertisers/$uid"))
                ?: return@withContext Result.success(null)

            val company = obj.optString("companyName", obj.optString("businessName", "")).trim()
            if (company.isBlank()) return@withContext Result.success(null)

            Result.success(
                GoogleAdvertiserProfile(
                    uid = uid,
                    fullName = obj.optString("advertiserName", obj.optString("name", obj.optString("ownerName", ""))).trim(),
                    email = obj.optString("email", "").trim(),
                    phone = obj.optString("phone", obj.optString("mobile", "")).trim(),
                    companyName = company,
                    category = obj.optString("category", "Healthcare").trim(),
                    address = obj.optString("address", "").trim(),
                    photoUrl = obj.optString("photo", obj.optString("logoUrl", "")).trim(),
                    websiteUrl = obj.optString("websiteUrl", "").trim(),
                    status = obj.optString("status", "Active").trim(),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchGoogleAdvertiser failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerGoogleAdvertiser(
        user: GoogleUserAccount,
        fullName: String,
        phone: String,
        companyName: String,
        category: String,
        address: String,
        websiteUrl: String
    ): Result<GoogleAdvertiserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanName = fullName.trim().ifBlank { user.displayName }
            val cleanPhone = phone.replace(Regex("[^0-9+]"), "").trim()
            val cleanCompany = companyName.trim()
            val cleanCat = category.trim().ifBlank { "Healthcare" }
            val cleanAddr = address.trim()
            val cleanWeb = websiteUrl.trim()

            if (cleanName.length < 2) return@withContext Result.failure(IllegalArgumentException("Full Name is required."))
            if (cleanCompany.length < 2) return@withContext Result.failure(IllegalArgumentException("Business / Company Name is required."))
            if (!SponsorCampaignSelector.isValidBangladeshMobile(cleanPhone)) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid Bangladesh phone number (01XXXXXXXXX)."))
            }
            if (cleanAddr.length < 3) return@withContext Result.failure(IllegalArgumentException("Business Address is required."))
            if (cleanWeb.isNotBlank() && !SponsorCampaignSelector.isValidWebsiteUrl(cleanWeb)) {
                return@withContext Result.failure(IllegalArgumentException("Website URL must start with https:// or http://"))
            }

            val now = System.currentTimeMillis()
            val existing = getJsonObject(buildAuthedUrl("advertisers/${user.uid}"))
            val createdAt = existing?.optLong("createdAt", 0L)?.takeIf { it > 0L } ?: now
            val status = existing?.optString("status", "")?.takeIf { it.isNotBlank() } ?: "Pending"

            val userPayload = JSONObject().apply {
                put("uid", user.uid)
                put("name", cleanName.take(120))
                put("email", user.email.take(160))
                put("photo", user.photoUrl.take(500))
                put("role", "advertiser")
                put("createdAt", createdAt)
                put("updatedAt", now)
                put("status", "ACTIVE")
            }
            val advPayload = JSONObject().apply {
                put("uid", user.uid)
                put("id", user.uid)
                put("advertiserName", cleanName.take(120))
                put("name", cleanName.take(120))
                put("ownerName", cleanName.take(120))
                put("contactPerson", cleanName.take(120))
                put("email", user.email.take(160))
                put("phone", cleanPhone.take(32))
                put("mobile", cleanPhone.take(32))
                put("companyName", cleanCompany.take(160))
                put("businessName", cleanCompany.take(160))
                put("category", cleanCat.take(80))
                put("address", cleanAddr.take(250))
                put("photo", user.photoUrl.take(500))
                put("logoUrl", user.photoUrl.take(500))
                put("websiteUrl", cleanWeb.take(300))
                put("authProvider", "GOOGLE")
                put("paymentStatus", "Unpaid")
                put("status", status)
                put("createdAt", createdAt)
                put("updatedAt", now)
            }

            val ok1 = putJson(buildAuthedUrl("users/${user.uid}"), userPayload.toString())
            val ok2 = putJson(buildAuthedUrl("advertisers/${user.uid}"), advPayload.toString())
            putJson(buildAuthedUrl("directory/advertisers/${user.uid}"), advPayload.toString())

            if (!ok1 || !ok2) {
                return@withContext Result.failure(IllegalStateException("Could not save advertiser account. Check connection and try again."))
            }

            Result.success(
                GoogleAdvertiserProfile(
                    uid = user.uid,
                    fullName = cleanName,
                    email = user.email,
                    phone = cleanPhone,
                    companyName = cleanCompany,
                    category = cleanCat,
                    address = cleanAddr,
                    photoUrl = user.photoUrl,
                    websiteUrl = cleanWeb,
                    status = status,
                    createdAt = createdAt
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "registerGoogleAdvertiser failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun mirrorAdvertisementNode(
        adId: String,
        advertiserUid: String,
        title: String,
        description: String,
        imageUrl: String,
        websiteUrl: String,
        placement: String,
        status: String = "Pending",
        createdAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("adId", adId)
                put("advertiserUid", advertiserUid)
                put("title", title.take(160))
                put("description", description.take(500))
                put("imageUrl", imageUrl)
                put("destinationUrl", websiteUrl.take(300))
                put("websiteUrl", websiteUrl.take(300))
                put("placementType", placement.take(80))
                put("placement", placement.take(80))
                put("status", status)
                put("createdAt", createdAt)
                put("updatedAt", System.currentTimeMillis())
            }
            putJson(buildAuthedUrl("advertisements/$adId"), payload.toString())
        }
    }

    fun sanitizeFirebaseKey(raw: String): String =
        raw.trim().replace(Regex("[.#$\\[\\]/\\s]+"), "_").take(120)

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

    private fun putJson(urlString: String, jsonBody: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
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
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
