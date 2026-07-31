package com.elenglish.studymentor.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.fullproduct.FullProductRepository
import com.elenglish.studymentor.domain.model.EconomyProjection
import com.elenglish.studymentor.domain.model.PendingPurchase
import com.elenglish.studymentor.domain.model.PurchaseResult
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.leavesOutcomeUnknown
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PurchaseState {
    data object Idle : PurchaseState
    data class Submitting(val itemId: String) : PurchaseState
    data class Success(val result: PurchaseResult) : PurchaseState
    data class Failed(
        val itemId: String,
        val kind: CatalogErrorKind,
        val requestId: String?,
        val retryable: Boolean,
    ) : PurchaseState
}

sealed interface EconomyUiState {
    data object Loading : EconomyUiState
    data class Content(
        val economy: EconomyProjection,
        val purchase: PurchaseState = PurchaseState.Idle,
    ) : EconomyUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : EconomyUiState
}

@HiltViewModel
class EconomyViewModel @Inject constructor(
    private val repository: FullProductRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<EconomyUiState>(EconomyUiState.Loading)
    val uiState = _uiState.asStateFlow()
    val uiStateLiveData by lazy { _uiState.asLiveData() }
    private var pending: PendingPurchase? = null

    init {
        load()
    }

    fun load() {
        pending = null
        _uiState.value = EconomyUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getEconomy()) {
                is ApiResult.Success -> EconomyUiState.Content(result.value)
                is ApiResult.Failure -> EconomyUiState.Failed(
                    result.error.toKind(), result.error.requestId,
                )
            }
        }
    }

    fun purchase(itemId: String) {
        val content = _uiState.value as? EconomyUiState.Content ?: return
        if (content.purchase is PurchaseState.Submitting) return
        val request = pending ?: repository.preparePurchase(itemId).also { pending = it }
        _uiState.value = content.copy(purchase = PurchaseState.Submitting(request.itemId))
        viewModelScope.launch {
            when (val result = repository.purchase(request)) {
                is ApiResult.Success -> {
                    pending = null
                    val refreshed = repository.getEconomy()
                    val economy = if (refreshed is ApiResult.Success) {
                        refreshed.value
                    } else {
                        content.economy
                    }
                    _uiState.value = EconomyUiState.Content(
                        economy, PurchaseState.Success(result.value),
                    )
                }
                is ApiResult.Failure -> {
                    val retryable = result.error.leavesOutcomeUnknown()
                    if (!retryable) pending = null
                    _uiState.value = content.copy(
                        purchase = PurchaseState.Failed(
                            request.itemId,
                            result.error.toKind(),
                            result.error.requestId,
                            retryable,
                        ),
                    )
                }
            }
        }
    }

    fun retryPurchase() {
        pending?.let { purchase(it.itemId) }
    }
}
