package com.example.data.local

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doctor_dir_prefs")

data class AdvertiserSession(
    val id: String = "",
    val businessName: String = "",
    val ownerName: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = ""
) {
    val isLoggedIn: Boolean get() = id.isNotBlank()
}

class SessionPreferences(private val context: Context) {
    private val keyUserId = stringPreferencesKey("current_user_id")
    private val keyLang = stringPreferencesKey("app_lang")
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

    val currentUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyUserId]?.takeIf { it.isNotBlank() }
    }

    val currentLang: Flow<AppLang> = context.dataStore.data.map { prefs ->
        if (prefs[keyLang] == AppLang.EN.name) AppLang.EN else AppLang.BN
    }

    val lastAppOpenPopupDate: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyLastPopupDate].orEmpty()
    }

    val lastServerSyncAtMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[keyLastServerSyncAtMs] ?: 0L
    }

    val advertiserSession: Flow<AdvertiserSession> = context.dataStore.data.map { prefs ->
        AdvertiserSession(
            id = prefs[keyAdvId].orEmpty(),
            businessName = prefs[keyAdvBusiness].orEmpty(),
            ownerName = prefs[keyAdvOwner].orEmpty(),
            mobile = prefs[keyAdvMobile].orEmpty(),
            email = prefs[keyAdvEmail].orEmpty(),
            address = prefs[keyAdvAddress].orEmpty()
        )
    }

    suspend fun getOrCreateDeviceHash(): String {
        val existing = context.dataStore.data.map { it[keyDeviceHash] }.first()
        if (!existing.isNullOrBlank()) return existing
        val raw = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val hash = digest.take(12).joinToString("") { "%02x".format(it) }
        context.dataStore.edit { it[keyDeviceHash] = hash }
        return hash
    }

    suspend fun saveServerSyncAnchor(serverTimeMs: Long, elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
        if (serverTimeMs <= 0L) return
        context.dataStore.edit { prefs ->
            prefs[keyLastServerSyncAtMs] = serverTimeMs
            prefs[keyLastServerSyncElapsedMs] = elapsedRealtimeMs
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
        val prefs = context.dataStore.data.first()
        val lastServerMs = prefs[keyLastServerSyncAtMs] ?: 0L
        val lastElapsedMs = prefs[keyLastServerSyncElapsedMs] ?: 0L
        return computeAuthoritativeNowMs(
            deviceWallClockMs = deviceWallClockMs,
            lastServerSyncAtMs = lastServerMs,
            lastServerSyncElapsedMs = lastElapsedMs,
            currentElapsedMs = currentElapsedMs
        )
    }

    suspend fun getLastAppOpenPopupDateOnce(): String = lastAppOpenPopupDate.first()

    suspend fun setLastAppOpenPopupDate(dateStr: String) {
        context.dataStore.edit { prefs ->
            prefs[keyLastPopupDate] = dateStr
        }
    }

    suspend fun saveAdvertiserSession(session: AdvertiserSession) {
        context.dataStore.edit { prefs ->
            prefs[keyAdvId] = session.id
            prefs[keyAdvBusiness] = session.businessName
            prefs[keyAdvOwner] = session.ownerName
            prefs[keyAdvMobile] = session.mobile
            prefs[keyAdvEmail] = session.email
            prefs[keyAdvAddress] = session.address
        }
    }

    suspend fun clearAdvertiserSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAdvId)
            prefs.remove(keyAdvBusiness)
            prefs.remove(keyAdvOwner)
            prefs.remove(keyAdvMobile)
            prefs.remove(keyAdvEmail)
            prefs.remove(keyAdvAddress)
        }
    }

    suspend fun recordLocalOfflineSponsorDisplay() {
        context.dataStore.edit { prefs ->
            prefs[keyPendingOfflineDisplays] = (prefs[keyPendingOfflineDisplays] ?: 0) + 1
        }
    }

    suspend fun recordLocalOfflineSession() {
        context.dataStore.edit { prefs ->
            prefs[keyPendingOfflineSessions] = (prefs[keyPendingOfflineSessions] ?: 0) + 1
        }
    }

    suspend fun consumePendingOfflineStats(): Pair<Int, Int> {
        var displays = 0
        var sessions = 0
        context.dataStore.edit { prefs ->
            displays = prefs[keyPendingOfflineDisplays] ?: 0
            sessions = prefs[keyPendingOfflineSessions] ?: 0
            prefs[keyPendingOfflineDisplays] = 0
            prefs[keyPendingOfflineSessions] = 0
        }
        return displays to sessions
    }

    suspend fun setUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId.isNullOrBlank()) prefs.remove(keyUserId) else prefs[keyUserId] = userId
        }
    }

    suspend fun setLanguage(lang: AppLang) {
        context.dataStore.edit { prefs ->
            prefs[keyLang] = lang.name
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
