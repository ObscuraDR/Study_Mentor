package com.elenglish.studymentor.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.quiz.QuizRepository
import com.elenglish.studymentor.domain.model.WrongAnswer
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WrongAnswerUiState {
    data object Loading : WrongAnswerUiState
    data object Empty : WrongAnswerUiState
    data class Content(
        val items: List<WrongAnswer>,
        val totalItems: Int,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
    ) : WrongAnswerUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : WrongAnswerUiState
}

@HiltViewModel
class WrongAnswerViewModel @Inject constructor(
    private val repository: QuizRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WrongAnswerUiState>(WrongAnswerUiState.Loading)
    val uiState = _uiState.asStateFlow()
    val uiStateLiveData by lazy { _uiState.asLiveData() }
    private var nextPage = 1

    init {
        load()
    }

    fun load() {
        nextPage = 1
        _uiState.value = WrongAnswerUiState.Loading
        loadPage(replace = true)
    }

    fun loadMore() {
        val current = _uiState.value as? WrongAnswerUiState.Content ?: return
        if (!current.canLoadMore || current.loadingMore) return
        _uiState.value = current.copy(loadingMore = true)
        loadPage(replace = false)
    }

    private fun loadPage(replace: Boolean) {
        viewModelScope.launch {
            when (val result = repository.getWrongAnswers(page = nextPage)) {
                is ApiResult.Success -> {
                    val previous = if (replace) emptyList()
                    else (_uiState.value as? WrongAnswerUiState.Content)?.items.orEmpty()
                    val items = previous + result.value.items
                    if (items.isEmpty()) {
                        _uiState.value = WrongAnswerUiState.Empty
                    } else {
                        _uiState.value = WrongAnswerUiState.Content(
                            items = items,
                            totalItems = result.value.totalItems,
                            canLoadMore = result.value.hasNext,
                        )
                    }
                    nextPage = result.value.page + 1
                }
                is ApiResult.Failure -> {
                    if (!replace && _uiState.value is WrongAnswerUiState.Content) {
                        val current = _uiState.value as WrongAnswerUiState.Content
                        _uiState.value = current.copy(loadingMore = false)
                    } else {
                        _uiState.value = WrongAnswerUiState.Failed(
                            result.error.toKind(),
                            result.error.requestId,
                        )
                    }
                }
            }
        }
    }
}
