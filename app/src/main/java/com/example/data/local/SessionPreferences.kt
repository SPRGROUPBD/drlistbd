package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doctor_dir_prefs")

class SessionPreferences(private val context: Context) {
    private val keyUserId = stringPreferencesKey("current_user_id")
    private val keyLang = stringPreferencesKey("app_lang")
    private val keyGuestId = stringPreferencesKey("guest_device_id")

    val currentUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyUserId]?.takeIf { it.isNotBlank() }
    }

    val currentLang: Flow<AppLang> = context.dataStore.data.map { prefs ->
        if (prefs[keyLang] == AppLang.EN.name) AppLang.EN else AppLang.BN
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
