package com.elenglish.studymentor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.session.SessionState
import com.elenglish.studymentor.core.session.SessionStateHolder
import com.elenglish.studymentor.data.preferences.AppPreferencesRepository
import com.elenglish.studymentor.data.session.SessionRepository
import com.elenglish.studymentor.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Root UI state: the theme the shell renders with, and which navigation graph
 * the app should show.
 *
 * [SessionStatus] is derived only from [SessionStateHolder]; the client never
 * assumes an authorised state.
 */
enum class SessionStatus { Restoring, Guest, Authenticated }

data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val sessionStatus: SessionStatus = SessionStatus.Restoring,
) {
    val isAuthenticated: Boolean get() = sessionStatus == SessionStatus.Authenticated
}

@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: AppPreferencesRepository,
    sessionStateHolder: SessionStateHolder,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> =
        combine(preferences.themeMode, sessionStateHolder.state) { themeMode, session ->
            AppUiState(
                themeMode = themeMode,
                sessionStatus = when (session) {
                    SessionState.Unknown -> SessionStatus.Restoring
                    SessionState.NotAuthenticated -> SessionStatus.Guest
                    is SessionState.Authenticated -> SessionStatus.Authenticated
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppUiState(),
        )

    init {
        // Ask the backend whether the stored refresh token still represents a
        // live session. Until it answers, the app stays in Restoring and shows
        // neither the guest nor the authenticated shell.
        viewModelScope.launch { sessionRepository.restoreSession() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
