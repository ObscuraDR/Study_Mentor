package com.elenglish.studymentor.ui.engagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.engagement.StreakRecoveryRepository
import com.elenglish.studymentor.domain.model.PendingStreakRecoveryClaim
import com.elenglish.studymentor.domain.model.StreakRecoveryClaim
import com.elenglish.studymentor.domain.model.StreakRecoveryEligibility
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.leavesOutcomeUnknown
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecoveryClaimState {
    data object Idle : RecoveryClaimState
    data object Submitting : RecoveryClaimState
    data class Success(val result: StreakRecoveryClaim) : RecoveryClaimState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?, val retryable: Boolean) : RecoveryClaimState
}

sealed interface StreakRecoveryUiState {
    data object Loading : StreakRecoveryUiState
    data class Content(val eligibility: StreakRecoveryEligibility, val claim: RecoveryClaimState = RecoveryClaimState.Idle) : StreakRecoveryUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : StreakRecoveryUiState
}

@HiltViewModel
class StreakRecoveryViewModel @Inject constructor(
    private val repository: StreakRecoveryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StreakRecoveryUiState>(StreakRecoveryUiState.Loading)
    val uiState: StateFlow<StreakRecoveryUiState> = _uiState.asStateFlow()
    val uiStateLiveData by lazy { _uiState.asLiveData() }
    private var pendingClaim: PendingStreakRecoveryClaim? = null

    init { load() }

    fun load() {
        _uiState.value = StreakRecoveryUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getEligibility()) {
                is ApiResult.Success -> StreakRecoveryUiState.Content(result.value)
                is ApiResult.Failure -> StreakRecoveryUiState.Failed(result.error.toKind(), result.error.requestId)
            }
        }
    }

    fun claim(onSuccess: () -> Unit = {}) {
        val content = _uiState.value as? StreakRecoveryUiState.Content ?: return
        if (!content.eligibility.eligible || content.claim is RecoveryClaimState.Submitting) return
        val pending = pendingClaim ?: repository.prepareClaim().also { pendingClaim = it }
        _uiState.value = content.copy(claim = RecoveryClaimState.Submitting)
        viewModelScope.launch {
            when (val result = repository.claim(pending)) {
                is ApiResult.Success -> {
                    pendingClaim = null
                    _uiState.value = StreakRecoveryUiState.Content(content.eligibility, RecoveryClaimState.Success(result.value))
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    val retryable = result.error.leavesOutcomeUnknown()
                    if (!retryable) pendingClaim = null
                    _uiState.value = StreakRecoveryUiState.Content(
                        content.eligibility,
                        RecoveryClaimState.Failed(result.error.toKind(), result.error.requestId, retryable),
                    )
                }
            }
        }
    }

    fun retryClaim(onSuccess: () -> Unit = {}) {
        if (pendingClaim != null) claim(onSuccess)
    }
}
