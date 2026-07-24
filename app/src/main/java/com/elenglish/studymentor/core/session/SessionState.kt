package com.elenglish.studymentor.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authorization state of the current process.
 *
 * Phase 1 only models the state machine and always starts [Unknown] before
 * resolving to [NotAuthenticated]. Real token restoration is Phase 3 work; the
 * client must never assume it is authenticated without a backend-issued session.
 */
sealed interface SessionState {
    /** Startup restoration has not completed yet. */
    data object Unknown : SessionState

    /** No usable session exists; only guest destinations are reachable. */
    data object NotAuthenticated : SessionState

    /** A backend-issued session exists for [userId]. */
    data class Authenticated(val userId: String) : SessionState
}

/**
 * Single in-memory source of truth for [SessionState].
 *
 * Tokens are deliberately not stored here. Phase 3 introduces memory-only access
 * token storage and Keystore-backed refresh token storage behind this holder.
 */
@Singleton
class SessionStateHolder @Inject constructor() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun markNotAuthenticated() {
        _state.value = SessionState.NotAuthenticated
    }

    fun markAuthenticated(userId: String) {
        require(userId.isNotBlank()) { "userId must not be blank" }
        _state.value = SessionState.Authenticated(userId)
    }
}
