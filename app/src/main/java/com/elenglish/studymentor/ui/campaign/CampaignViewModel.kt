package com.elenglish.studymentor.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.campaign.CampaignRepository
import com.elenglish.studymentor.domain.model.CampaignProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CampaignUiState {
    data object Loading : CampaignUiState
    data class Empty(val requestId: String? = null) : CampaignUiState
    data class Content(val campaign: CampaignProjection, val origin: CampaignDataOrigin = CampaignDataOrigin.Live) : CampaignUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : CampaignUiState
}

enum class CampaignDataOrigin { Live, Cached }

@HiltViewModel
class CampaignViewModel @Inject constructor(
    private val repository: CampaignRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CampaignUiState>(CampaignUiState.Loading)
    val uiState: StateFlow<CampaignUiState> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    init { load() }

    fun load() {
        _uiState.value = CampaignUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getCampaign()) {
                is ApiResult.Success -> if (result.value.zones.isEmpty()) CampaignUiState.Empty(result.requestId) else CampaignUiState.Content(result.value)
                is ApiResult.Failure -> CampaignUiState.Failed(result.error.toKind(), result.error.requestId)
            }
        }
    }
}
