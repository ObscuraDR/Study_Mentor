package com.elenglish.studymentor.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which credential form is showing. */
enum class AuthMode { SignIn, Register }

/**
 * A failure to show the user.
 *
 * [requestId] is the backend correlation id, safe to display. The raw response
 * body is never carried here.
 */
data class AuthFailure(
    val message: String,
    val requestId: String? = null,
)

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val displayNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val submitting: Boolean = false,
    val failure: AuthFailure? = null,
) {
    /** Enabled only when the form could plausibly succeed. */
    val canSubmit: Boolean
        get() = !submitting &&
            email.isNotBlank() &&
            password.isNotBlank() &&
            (mode == AuthMode.SignIn || displayName.isNotBlank())
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                displayNameError = null,
                emailError = null,
                passwordError = null,
                failure = null,
            )
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, displayNameError = null, failure = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, failure = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, failure = null) }
    }

    /**
     * Submits the form.
     *
     * Local validation only checks what the contract states plainly (presence,
     * and the 8-character minimum for registration). Everything else — including
     * whether the credentials are correct — is the backend's decision.
     */
    fun submit() {
        val state = _uiState.value
        if (state.submitting) return

        val validated = state.validate()
        if (validated.hasFieldErrors) {
            _uiState.value = validated
            return
        }

        _uiState.value = validated.copy(submitting = true, failure = null)

        viewModelScope.launch {
            val result = when (state.mode) {
                AuthMode.SignIn -> sessionRepository.login(
                    email = state.email.trim(),
                    password = state.password,
                )
                AuthMode.Register -> sessionRepository.register(
                    displayName = state.displayName.trim(),
                    email = state.email.trim(),
                    password = state.password,
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    // Navigation is driven by SessionStateHolder, which the
                    // repository updated from the backend's response. Clear the
                    // password so it does not linger in UI state.
                    _uiState.update { it.copy(submitting = false, password = "") }
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(submitting = false, failure = result.error.toAuthFailure(state.mode))
                    }
                }
            }
        }
    }

    fun dismissFailure() {
        _uiState.update { it.copy(failure = null) }
    }
}

private val AuthUiState.hasFieldErrors: Boolean
    get() = displayNameError != null || emailError != null || passwordError != null

private fun AuthUiState.validate(): AuthUiState = copy(
    displayNameError = if (mode == AuthMode.Register && displayName.isBlank()) {
        "Enter your name"
    } else {
        null
    },
    emailError = when {
        email.isBlank() -> "Enter your email address"
        !email.contains('@') -> "Enter a valid email address"
        else -> null
    },
    passwordError = when {
        password.isBlank() -> "Enter your password"
        mode == AuthMode.Register && password.length < MIN_PASSWORD_LENGTH ->
            "Use at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    },
)

/** From `RegisterRequest.password.minLength` in OpenAPI v1. */
private const val MIN_PASSWORD_LENGTH = 8

/**
 * Maps a contract error code to user-facing wording.
 *
 * The backend's own message is not shown directly: it is written for developers
 * and may name internal fields.
 */
private fun ApiError.toAuthFailure(mode: AuthMode): AuthFailure = when (this) {
    is ApiError.Network -> AuthFailure(
        message = "No connection. Check your network and try again.",
    )

    is ApiError.Backend -> AuthFailure(
        message = when (code) {
            ApiErrorCodes.AUTH_INVALID_CREDENTIALS -> "That email and password do not match."
            ApiErrorCodes.AUTH_EMAIL_ALREADY_REGISTERED -> "That email is already registered."
            ApiErrorCodes.VALIDATION_INVALID_FIELD,
            ApiErrorCodes.VALIDATION_INVALID_REQUEST,
            -> if (mode == AuthMode.Register) {
                "Check your details and try again."
            } else {
                "Check your email and password and try again."
            }
            ApiErrorCodes.RATE_LIMIT_EXCEEDED -> "Too many attempts. Wait a moment and try again."
            else -> "Something went wrong. Please try again."
        },
        requestId = requestId,
    )

    is ApiError.Unexpected -> AuthFailure(
        message = "Something went wrong. Please try again.",
        requestId = requestId,
    )
}
