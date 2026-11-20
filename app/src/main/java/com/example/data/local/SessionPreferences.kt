package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doctor_dir_prefs")

class SessionPreferences(private val context: Context) {
    private val keyUserId = stringPreferencesKey("current_user_id")
    private val keyLang = stringPreferencesKey("app_lang")
    private val keyGuestId = stringPreferencesKey("guest_device_id")
    private val keyLastPopupDate = stringPreferencesKey("last_app_open_sponsor_popup_date")
    private val keyPendingOfflineDisplays = intPreferencesKey("pending_offline_sponsor_displays")
    private val keyPendingOfflineSessions = intPreferencesKey("pending_offline_sessions")

    val currentUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyUserId]?.takeIf { it.isNotBlank() }
    }

    val currentLang: Flow<AppLang> = context.dataStore.data.map { prefs ->
        if (prefs[keyLang] == AppLang.EN.name) AppLang.EN else AppLang.BN
    }

    val lastAppOpenPopupDate: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyLastPopupDate].orEmpty()
    }

    suspend fun getLastAppOpenPopupDateOnce(): String = lastAppOpenPopupDate.first()

    suspend fun setLastAppOpenPopupDate(dateStr: String) {
        context.dataStore.edit { prefs ->
            prefs[keyLastPopupDate] = dateStr
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
}

