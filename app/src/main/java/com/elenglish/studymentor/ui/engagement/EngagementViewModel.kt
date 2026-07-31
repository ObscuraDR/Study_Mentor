package com.elenglish.studymentor.ui.engagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.engagement.EngagementRepository
import com.elenglish.studymentor.domain.model.EngagementProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EngagementUiState {
    data object Loading : EngagementUiState
    data class Content(val engagement: EngagementProjection) : EngagementUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : EngagementUiState
}

/**
 * Reads `GET /me/engagement`.
 *
 * Level, XP, streak, achievements and mission progress are all backend
 * projections over the server's own immutable award ledger and entitlement
 * rows. This ViewModel has no write path and no local cache: a stale or
 * device-derived figure here would misreport standing the same way a
 * client-calculated XP total would.
 */
@HiltViewModel
class EngagementViewModel @Inject constructor(
    private val engagementRepository: EngagementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EngagementUiState>(EngagementUiState.Loading)
    val uiState: StateFlow<EngagementUiState> = _uiState.asStateFlow()
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    init {
        load()
    }

    fun load() {
        _uiState.value = EngagementUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = engagementRepository.getEngagement()) {
                is ApiResult.Success -> EngagementUiState.Content(result.value)
                is ApiResult.Failure -> EngagementUiState.Failed(
                    kind = result.error.toKind(),
                    requestId = result.error.requestId,
                )
            }
        }
    }
}
