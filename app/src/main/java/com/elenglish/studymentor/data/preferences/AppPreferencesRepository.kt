package com.elenglish.studymentor.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elenglish.studymentor.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local, non-sensitive UI preferences.
 *
 * Theme is an Android-owned preference per the roadmap ownership table. No token,
 * credential or protected learning value is ever written to DataStore.
 */
@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.fromStorageValue(preferences[KEY_THEME_MODE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.storageValue }
    }

    /**
     * Daily reminder settings.
     *
     * Device-local only. The shared-settings contract covers locale and daily XP
     * target and says nothing about reminders, so synchronising them would be
     * inventing a field the backend does not have.
     */
    val reminder: Flow<ReminderPreference> = dataStore.data.map { preferences ->
        ReminderPreference(
            enabled = preferences[KEY_REMINDER_ENABLED] ?: false,
            hour = (preferences[KEY_REMINDER_HOUR] ?: DEFAULT_HOUR).coerceIn(0, 23),
            minute = (preferences[KEY_REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
        )
    }

    suspend fun setReminder(preference: ReminderPreference) {
        dataStore.edit { preferences ->
            preferences[KEY_REMINDER_ENABLED] = preference.enabled
            preferences[KEY_REMINDER_HOUR] = preference.hour
            preferences[KEY_REMINDER_MINUTE] = preference.minute
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        const val DEFAULT_HOUR = 20
    }
}

/** A daily reminder at a local wall-clock time. */
data class ReminderPreference(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
) {
    val localTime: LocalTime get() = LocalTime.of(hour, minute)
}
