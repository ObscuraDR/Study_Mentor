package com.elenglish.studymentor.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.data.preferences.AppPreferencesRepository
import com.elenglish.studymentor.data.preferences.ReminderPreference
import com.elenglish.studymentor.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderUiState(
    val preference: ReminderPreference = ReminderPreference(),
    /** Whether the OS currently lets the app post notifications. */
    val permissionGranted: Boolean = false,
    /** True when the user asked for reminders but the OS permission is missing. */
    val awaitingPermission: Boolean = false,
)

/**
 * Daily reminder settings.
 *
 * The switch reflects what is actually scheduled. If the OS permission is
 * missing the reminder is not enabled and the UI says so, rather than showing a
 * switch that is on while nothing would ever fire.
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val preferences: AppPreferencesRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    init {
        viewModelScope.launch {
            preferences.reminder.collect { stored ->
                _uiState.update { it.copy(preference = stored) }
            }
        }
    }

    /** Reports the current OS permission state, and reconciles scheduling with it. */
    fun onPermissionStateChanged(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        val state = _uiState.value
        if (!granted && state.preference.enabled) {
            // Permission was revoked in system settings; nothing can fire, so
            // stop claiming a reminder is set.
            viewModelScope.launch {
                preferences.setReminder(state.preference.copy(enabled = false))
                scheduler.cancel()
            }
        }
    }

    fun onEnabledChange(enabled: Boolean) {
        if (enabled && !_uiState.value.permissionGranted) {
            _uiState.update { it.copy(awaitingPermission = true) }
            return
        }
        applyReminder(_uiState.value.preference.copy(enabled = enabled))
    }

    fun onTimeChange(hour: Int, minute: Int) {
        applyReminder(_uiState.value.preference.copy(hour = hour, minute = minute))
    }

    /** Called once the permission request has been answered. */
    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted, awaitingPermission = false) }
        if (granted) applyReminder(_uiState.value.preference.copy(enabled = true))
    }

    private fun applyReminder(preference: ReminderPreference) {
        viewModelScope.launch {
            preferences.setReminder(preference)
            if (preference.enabled) {
                scheduler.schedule(preference.localTime)
            } else {
                scheduler.cancel()
            }
        }
    }
}
