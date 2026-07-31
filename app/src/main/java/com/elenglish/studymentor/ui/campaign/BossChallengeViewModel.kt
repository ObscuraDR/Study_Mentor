package com.elenglish.studymentor.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.fullproduct.FullProductRepository
import com.elenglish.studymentor.domain.model.BossAnswer
import com.elenglish.studymentor.domain.model.BossAttemptResult
import com.elenglish.studymentor.domain.model.BossChallenge
import com.elenglish.studymentor.domain.model.PendingBossAttempt
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.leavesOutcomeUnknown
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BossSubmissionState {
    data object Idle : BossSubmissionState
    data object Submitting : BossSubmissionState
    data class Success(val result: BossAttemptResult) : BossSubmissionState
    data class Failed(
        val kind: CatalogErrorKind,
        val requestId: String?,
        val retryable: Boolean,
    ) : BossSubmissionState
}

sealed interface BossChallengeUiState {
    data object Loading : BossChallengeUiState
    data class Content(
        val challenge: BossChallenge,
        val submission: BossSubmissionState = BossSubmissionState.Idle,
    ) : BossChallengeUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : BossChallengeUiState
}

@HiltViewModel
class BossChallengeViewModel @Inject constructor(
    private val repository: FullProductRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<BossChallengeUiState>(BossChallengeUiState.Loading)
    val uiState = _uiState.asStateFlow()
    val uiStateLiveData by lazy { _uiState.asLiveData() }
    private var pending: PendingBossAttempt? = null

    init {
        load()
    }

    fun load() {
        pending = null
        _uiState.value = BossChallengeUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getActiveBossChallenge()) {
                is ApiResult.Success -> BossChallengeUiState.Content(result.value)
                is ApiResult.Failure -> BossChallengeUiState.Failed(
                    result.error.toKind(), result.error.requestId,
                )
            }
        }
    }

    fun submit(answers: List<BossAnswer>) {
        val content = _uiState.value as? BossChallengeUiState.Content ?: return
        if (content.submission is BossSubmissionState.Submitting) return
        val request = pending ?: repository.prepareBossAttempt(
            content.challenge.id, answers,
        ).also { pending = it }
        _uiState.value = content.copy(submission = BossSubmissionState.Submitting)
        viewModelScope.launch {
            when (val result = repository.submitBossAttempt(request)) {
                is ApiResult.Success -> {
                    pending = null
                    _uiState.value = content.copy(
                        submission = BossSubmissionState.Success(result.value),
                    )
                }
                is ApiResult.Failure -> {
                    val retryable = result.error.leavesOutcomeUnknown()
                    if (!retryable) pending = null
                    _uiState.value = content.copy(
                        submission = BossSubmissionState.Failed(
                            result.error.toKind(), result.error.requestId, retryable,
                        ),
                    )
                }
            }
        }
    }

    fun retry() {
        if (pending != null) submit(emptyList())
    }
}
