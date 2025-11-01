package com.samsunggalaxy.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bmi_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "light" or "dark"
        val UNIT_SYSTEM = stringPreferencesKey("unit_system") // "metric" or "imperial"
        val LANGUAGE = stringPreferencesKey("language") // "en" or "vi"
        val CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
        val ACTIVITY_LEVEL = intPreferencesKey("activity_level") // 0-4
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "light"
    }

    val unitSystem: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UNIT_SYSTEM] ?: "metric"
    }

    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE] ?: "en"
    }

    val currentProfileId: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_PROFILE_ID] ?: 0L
    }

    val activityLevel: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ACTIVITY_LEVEL] ?: 0
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
}
