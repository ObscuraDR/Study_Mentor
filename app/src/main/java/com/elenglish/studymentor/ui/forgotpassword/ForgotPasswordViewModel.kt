package com.elenglish.studymentor.ui.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val failure: String? = null,
) {
    val canSubmit: Boolean get() = !submitting && email.isNotBlank()
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, failure = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.submitting) return

        val emailError = when {
            state.email.isBlank() -> "Enter your email address"
            !state.email.contains('@') -> "Enter a valid email address"
            else -> null
        }
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }

        _uiState.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            val result = sessionRepository.requestPasswordReset(email = state.email.trim())
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(submitting = false, submitted = true) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        submitting = false,
                        failure = "Something went wrong. Please try again.",
                    )
                }
            }
        }
    }
}
