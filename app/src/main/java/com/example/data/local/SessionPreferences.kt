package com.example.data.local

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "doctor_dir_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class AdvertiserSession(
    val id: String = "",
    val businessName: String = "",
    val ownerName: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val category: String = "",
    val photoUrl: String = "",
    val websiteUrl: String = "",
    val authProvider: String = "LEGACY"
) {
    val isLoggedIn: Boolean get() = id.isNotBlank()
}

class SessionPreferences(private val context: Context) {
    private val keyUserId = stringPreferencesKey("current_user_id")
    private val keyLang = stringPreferencesKey("app_lang")
    private val keyDarkTheme = booleanPreferencesKey("app_dark_theme")
    private val keySelectedHomeDistrict = stringPreferencesKey("selected_home_district")
    private val keyDeviceHash = stringPreferencesKey("device_install_hash")
    private val keyLastPopupDate = stringPreferencesKey("last_app_open_sponsor_popup_date")
    private val keyPendingOfflineDisplays = intPreferencesKey("pending_offline_sponsor_displays")
    private val keyPendingOfflineSessions = intPreferencesKey("pending_offline_sessions")

    // Server-authoritative sync timestamp & monotonic anchor (prevents offline clock rollback abuse)
    private val keyLastServerSyncAtMs = longPreferencesKey("last_server_sync_at_ms")
    private val keyLastServerSyncElapsedMs = longPreferencesKey("last_server_sync_elapsed_ms")

    // Optional Advertiser-only session (normal patients NEVER need an account)
    private val keyAdvId = stringPreferencesKey("adv_session_id")
    private val keyAdvBusiness = stringPreferencesKey("adv_session_business")
    private val keyAdvOwner = stringPreferencesKey("adv_session_owner")
    private val keyAdvMobile = stringPreferencesKey("adv_session_mobile")
    private val keyAdvEmail = stringPreferencesKey("adv_session_email")
    private val keyAdvAddress = stringPreferencesKey("adv_session_address")
    private val keyAdvCategory = stringPreferencesKey("adv_session_category")
    private val keyAdvPhoto = stringPreferencesKey("adv_session_photo")
    private val keyAdvWebsite = stringPreferencesKey("adv_session_website")
    private val keyAdvProvider = stringPreferencesKey("adv_session_provider")

    // Persisted fair ad rotation counters (survives app restart & offline sessions)
    private val keyRotationCountersJson = stringPreferencesKey("fair_rotation_counters_json")

    // Cached UID-scoped visitor favorites (hospitals, diagnostics, chambers) synced with Firebase RTDB
    private val keyFavUid = stringPreferencesKey("cached_fav_uid")
    private val keyFavHospitals = stringPreferencesKey("cached_fav_hospitals")
    private val keyFavDiagnostics = stringPreferencesKey("cached_fav_diagnostics")
    private val keyFavChambers = stringPreferencesKey("cached_fav_chambers")

    private val safeData: Flow<Preferences> = context.dataStore.data.catch { emit(emptyPreferences()) }

    val currentUserId: Flow<String?> = safeData.map { prefs ->
        prefs[keyUserId]?.takeIf { it.isNotBlank() }
    }

    val currentLang: Flow<AppLang> = safeData.map { prefs ->
        if (prefs[keyLang] == AppLang.EN.name) AppLang.EN else AppLang.BN
    }

    val isDarkTheme: Flow<Boolean> = safeData.map { prefs ->
        prefs[keyDarkTheme] ?: false
    }

    val selectedHomeDistrict: Flow<String?> = safeData.map { prefs ->
        prefs[keySelectedHomeDistrict]?.trim()?.takeIf { it.isNotEmpty() }
    }

    val lastAppOpenPopupDate: Flow<String> = safeData.map { prefs ->
        prefs[keyLastPopupDate].orEmpty()
    }

    val lastServerSyncAtMs: Flow<Long> = safeData.map { prefs ->
        prefs[keyLastServerSyncAtMs] ?: 0L
    }

    val advertiserSession: Flow<AdvertiserSession> = safeData.map { prefs ->
        AdvertiserSession(
            id = prefs[keyAdvId].orEmpty(),
            businessName = prefs[keyAdvBusiness].orEmpty(),
            ownerName = prefs[keyAdvOwner].orEmpty(),
            mobile = prefs[keyAdvMobile].orEmpty(),
            email = prefs[keyAdvEmail].orEmpty(),
            address = prefs[keyAdvAddress].orEmpty(),
            category = prefs[keyAdvCategory].orEmpty(),
            photoUrl = prefs[keyAdvPhoto].orEmpty(),
            websiteUrl = prefs[keyAdvWebsite].orEmpty(),
            authProvider = prefs[keyAdvProvider].orEmpty().ifBlank { "LEGACY" }
        )
    }

    suspend fun loadRotationCounters(): Map<String, Long> {
        val raw = runCatching { safeData.map { it[keyRotationCountersJson].orEmpty() }.first() }.getOrDefault("")
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, obj.optLong(k, 0L).coerceAtLeast(0L))
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun saveRotationCounters(map: Map<String, Long>) {
        runCatching {
            val obj = org.json.JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            context.dataStore.edit { it[keyRotationCountersJson] = obj.toString() }
        }
    }

    suspend fun loadCachedFacilityFavorites(uid: String): Triple<Set<String>, Set<String>, Set<String>> {
        val prefs = runCatching { safeData.first() }.getOrElse { return Triple(emptySet(), emptySet(), emptySet()) }
        if (prefs[keyFavUid] != uid) return Triple(emptySet(), emptySet(), emptySet())
        fun parseCsv(s: String?) = s.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return Triple(
            parseCsv(prefs[keyFavHospitals]),
            parseCsv(prefs[keyFavDiagnostics]),
            parseCsv(prefs[keyFavChambers])
        )
    }

    suspend fun saveCachedFacilityFavorites(
        uid: String,
        hospitals: Set<String>,
        diagnostics: Set<String>,
        chambers: Set<String>
    ) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyFavUid] = uid
                prefs[keyFavHospitals] = hospitals.joinToString(",")
                prefs[keyFavDiagnostics] = diagnostics.joinToString(",")
                prefs[keyFavChambers] = chambers.joinToString(",")
            }
        }
    }

    suspend fun clearCachedFacilityFavorites() {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs.remove(keyFavUid)
                prefs.remove(keyFavHospitals)
                prefs.remove(keyFavDiagnostics)
                prefs.remove(keyFavChambers)
            }
        }
    }

    suspend fun getOrCreateDeviceHash(): String {
        val existing = runCatching { safeData.map { it[keyDeviceHash] }.first() }.getOrNull()
        if (!existing.isNullOrBlank()) return existing
        val raw = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val hash = digest.take(12).joinToString("") { "%02x".format(it) }
        runCatching { context.dataStore.edit { it[keyDeviceHash] = hash } }
        return hash
    }

    suspend fun saveServerSyncAnchor(serverTimeMs: Long, elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
        if (serverTimeMs <= 0L) return
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyLastServerSyncAtMs] = serverTimeMs
                prefs[keyLastServerSyncElapsedMs] = elapsedRealtimeMs
            }
        }
    }

    /**
     * Computes server-authoritative current time so an offline device cannot extend expired ads
     * by rolling its local system clock backwards.
     */
    suspend fun getAuthoritativeNowMs(
        deviceWallClockMs: Long = System.currentTimeMillis(),
        currentElapsedMs: Long = SystemClock.elapsedRealtime()
    ): Long {
        val prefs = runCatching { safeData.first() }.getOrNull() ?: return deviceWallClockMs
        val lastServerMs = prefs[keyLastServerSyncAtMs] ?: 0L
        val lastElapsedMs = prefs[keyLastServerSyncElapsedMs] ?: 0L
        return computeAuthoritativeNowMs(
            deviceWallClockMs = deviceWallClockMs,
            lastServerSyncAtMs = lastServerMs,
            lastServerSyncElapsedMs = lastElapsedMs,
            currentElapsedMs = currentElapsedMs
        )
    }

    suspend fun getLastAppOpenPopupDateOnce(): String = runCatching { lastAppOpenPopupDate.first() }.getOrDefault("")

    suspend fun setLastAppOpenPopupDate(dateStr: String) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyLastPopupDate] = dateStr
            }
        }
    }

    suspend fun saveAdvertiserSession(session: AdvertiserSession) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyAdvId] = session.id
                prefs[keyAdvBusiness] = session.businessName
                prefs[keyAdvOwner] = session.ownerName
                prefs[keyAdvMobile] = session.mobile
                prefs[keyAdvEmail] = session.email
                prefs[keyAdvAddress] = session.address
                prefs[keyAdvCategory] = session.category
                prefs[keyAdvPhoto] = session.photoUrl
                prefs[keyAdvWebsite] = session.websiteUrl
                prefs[keyAdvProvider] = session.authProvider
            }
        }
    }

    suspend fun clearAdvertiserSession() {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs.remove(keyAdvId)
                prefs.remove(keyAdvBusiness)
                prefs.remove(keyAdvOwner)
                prefs.remove(keyAdvMobile)
                prefs.remove(keyAdvEmail)
                prefs.remove(keyAdvAddress)
                prefs.remove(keyAdvCategory)
                prefs.remove(keyAdvPhoto)
                prefs.remove(keyAdvWebsite)
                prefs.remove(keyAdvProvider)
            }
        }
    }

    suspend fun recordLocalOfflineSponsorDisplay() {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyPendingOfflineDisplays] = (prefs[keyPendingOfflineDisplays] ?: 0) + 1
            }
        }
    }

    suspend fun recordLocalOfflineSession() {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyPendingOfflineSessions] = (prefs[keyPendingOfflineSessions] ?: 0) + 1
            }
        }
    }

    suspend fun consumePendingOfflineStats(): Pair<Int, Int> {
        var displays = 0
        var sessions = 0
        runCatching {
            context.dataStore.edit { prefs ->
                displays = prefs[keyPendingOfflineDisplays] ?: 0
                sessions = prefs[keyPendingOfflineSessions] ?: 0
                prefs[keyPendingOfflineDisplays] = 0
                prefs[keyPendingOfflineSessions] = 0
            }
        }
        return displays to sessions
    }

    suspend fun setUserId(userId: String?) {
        runCatching {
            context.dataStore.edit { prefs ->
                if (userId.isNullOrBlank()) prefs.remove(keyUserId) else prefs[keyUserId] = userId
            }
        }
    }

    suspend fun setLanguage(lang: AppLang) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyLang] = lang.name
            }
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[keyDarkTheme] = isDark
            }
        }
    }

    suspend fun setSelectedHomeDistrict(district: String?) {
        runCatching {
            context.dataStore.edit { prefs ->
                val clean = district?.trim().orEmpty()
                if (clean.isEmpty()) prefs.remove(keySelectedHomeDistrict)
                else prefs[keySelectedHomeDistrict] = clean
            }
        }
    }

    companion object {
        /**
         * Pure deterministic calculation of server-authoritative time:
         * - If never synced, falls back to deviceWallClockMs.
         * - If synced and monotonic elapsed time moved forward, advances server clock monotonically.
         * - Takes max(deviceWallClockMs, projectedServerMs) so rolling the device clock backward
         *   can NEVER un-expire a server-expired advertisement.
         */
        fun computeAuthoritativeNowMs(
            deviceWallClockMs: Long,
            lastServerSyncAtMs: Long,
            lastServerSyncElapsedMs: Long,
            currentElapsedMs: Long
        ): Long {
            if (lastServerSyncAtMs <= 0L) return deviceWallClockMs
            val deltaElapsed = (currentElapsedMs - lastServerSyncElapsedMs).coerceAtLeast(0L)
            val projectedServerMs = lastServerSyncAtMs + deltaElapsed
            return maxOf(deviceWallClockMs, projectedServerMs)
        }
    }
}
