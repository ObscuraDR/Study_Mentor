package com.elenglish.studymentor.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Content(val progress: ProgressProjection) : ProgressUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : ProgressUiState
}

/**
 * Reads `GET /me/progress`.
 *
 * There is deliberately no local cache and no local accumulation: progress is a
 * backend projection, and a stale or device-derived figure would misreport the
 * learner's actual standing.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProgressUiState>(ProgressUiState.Loading)
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = ProgressUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = learningRepository.getProgress()) {
                is ApiResult.Success -> ProgressUiState.Content(result.value)
                is ApiResult.Failure -> ProgressUiState.Failed(
                    kind = result.error.toKind(),
                    requestId = result.error.requestId,
                )
            }
        }
    }
}
