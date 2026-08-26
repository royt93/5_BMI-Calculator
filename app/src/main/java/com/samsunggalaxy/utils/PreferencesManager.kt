package com.samsunggalaxy.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bmi_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // ThemeHelper.SYSTEM/LIGHT/DARK
        val UNIT_SYSTEM = stringPreferencesKey("unit_system") // UnitFormatter.METRIC/IMPERIAL
        val LANGUAGE = stringPreferencesKey("language") // ISO 639-1 code: en, vi, es, pt, ar, hi, zh, id, tr, ru, it, nl, fr, de, ja, ko, th
        val CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
        val ACTIVITY_LEVEL = intPreferencesKey("activity_level") // 0-4
        val IS_LANGUAGE_SELECTED = booleanPreferencesKey("is_language_selected")
        // EPIC-08 T08.1 — daily weigh-in reminder, off by default (opt-in, avoids notification spam).
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        // EPIC-09 T09.2 — Health Connect bidirectional weight sync, off by default (opt-in,
        // avoids requesting a sensitive health permission nobody asked for).
        val HEALTH_CONNECT_SYNC_ENABLED = booleanPreferencesKey("health_connect_sync_enabled")
        val LAST_HEALTH_CONNECT_SYNC_TIMESTAMP = longPreferencesKey("last_health_connect_sync_timestamp")
    }

    val themeMode: Flow<String> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[THEME_MODE] ?: ThemeHelper.SYSTEM
    }

    val unitSystem: Flow<String> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[UNIT_SYSTEM] ?: UnitFormatter.METRIC
    }

    val language: Flow<String> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[LANGUAGE] ?: "en"
    }

    val currentProfileId: Flow<Long> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[CURRENT_PROFILE_ID] ?: 0L
    }

    val activityLevel: Flow<Int> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[ACTIVITY_LEVEL] ?: 0
    }

    val isLanguageSelected: Flow<Boolean> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[IS_LANGUAGE_SELECTED] ?: false
    }

    val reminderEnabled: Flow<Boolean> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[REMINDER_ENABLED] ?: false
    }

    // Default 08:00 — before breakfast, a common weigh-in habit-forming time.
    val reminderHour: Flow<Int> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[REMINDER_HOUR] ?: 8
    }

    val reminderMinute: Flow<Int> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[REMINDER_MINUTE] ?: 0
    }

    val healthConnectSyncEnabled: Flow<Boolean> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[HEALTH_CONNECT_SYNC_ENABLED] ?: false
    }

    val lastHealthConnectSyncTimestamp: Flow<Long?> = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
        preferences[LAST_HEALTH_CONNECT_SYNC_TIMESTAMP]
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setUnitSystem(system: String) {
        context.dataStore.edit { preferences ->
            preferences[UNIT_SYSTEM] = system
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = lang
        }
    }

    suspend fun setCurrentProfileId(id: Long) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_PROFILE_ID] = id
        }
    }

    suspend fun setActivityLevel(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVITY_LEVEL] = level
        }
    }

    suspend fun markLanguageSelected() {
        context.dataStore.edit { preferences ->
            preferences[IS_LANGUAGE_SELECTED] = true
        }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[REMINDER_HOUR] = hour
            preferences[REMINDER_MINUTE] = minute
        }
    }

    suspend fun setHealthConnectSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HEALTH_CONNECT_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setLastHealthConnectSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_HEALTH_CONNECT_SYNC_TIMESTAMP] = timestamp
        }
    }
}
