package com.elenglish.studymentor.ui.flashcards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.flashcard.FlashcardRepository
import com.elenglish.studymentor.domain.model.FlashcardDeck
import com.elenglish.studymentor.domain.model.FlashcardQueueEntry
import com.elenglish.studymentor.domain.model.PendingFlashcardReview
import com.elenglish.studymentor.domain.model.ReviewOutcome
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.leavesOutcomeUnknown
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decks available for a lesson. */
sealed interface FlashcardDeckListUiState {
    data object Loading : FlashcardDeckListUiState
    data class Content(val decks: List<FlashcardDeck>) : FlashcardDeckListUiState
    data object Empty : FlashcardDeckListUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : FlashcardDeckListUiState
}

@HiltViewModel
class FlashcardDeckListViewModel @Inject constructor(
    private val repository: FlashcardRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle[ARG_LESSON_ID]) {
        "lessonId is required to list flashcard decks"
    }

    private val _uiState = MutableStateFlow<FlashcardDeckListUiState>(FlashcardDeckListUiState.Loading)
    val uiState: StateFlow<FlashcardDeckListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = FlashcardDeckListUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.listDecks(lessonId)) {
                is ApiResult.Success -> if (result.value.isEmpty()) {
                    FlashcardDeckListUiState.Empty
                } else {
                    FlashcardDeckListUiState.Content(result.value)
                }
                is ApiResult.Failure -> FlashcardDeckListUiState.Failed(
                    kind = result.error.toKind(),
                    requestId = result.error.requestId,
                )
            }
        }
    }

    companion object {
        const val ARG_LESSON_ID = "lessonId"
    }
}

/** How a single review submission is doing. */
sealed interface ReviewSubmission {
    data object Idle : ReviewSubmission
    data object Submitting : ReviewSubmission
    data class Failed(
        val kind: CatalogErrorKind,
        val requestId: String?,
        val canRetry: Boolean,
    ) : ReviewSubmission
}

/** State of the review session. */
sealed interface FlashcardReviewUiState {
    data object Loading : FlashcardReviewUiState

    /** No cards are due — the session is done or was empty. */
    data class Done(val reviewed: Int, val known: Int) : FlashcardReviewUiState

    data class Reviewing(
        val queue: List<FlashcardQueueEntry>,
        val index: Int,
        val revealed: Boolean,
        val reviewed: Int,
        val known: Int,
        val submission: ReviewSubmission = ReviewSubmission.Idle,
    ) : FlashcardReviewUiState {
        val current: FlashcardQueueEntry get() = queue[index]
        val remaining: Int get() = queue.size - index
    }

    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : FlashcardReviewUiState
}

@HiltViewModel
class FlashcardReviewViewModel @Inject constructor(
    private val repository: FlashcardRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle[ARG_DECK_ID]) {
        "deckId is required to review a deck"
    }

    private val _uiState = MutableStateFlow<FlashcardReviewUiState>(FlashcardReviewUiState.Loading)
    val uiState: StateFlow<FlashcardReviewUiState> = _uiState.asStateFlow()

    /** The review awaiting acceptance, held so a retry resends it unchanged. */
    private var pendingReview: PendingFlashcardReview? = null

    init {
        load()
    }

    fun load() {
        _uiState.value = FlashcardReviewUiState.Loading
        pendingReview = null
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getQueue(deckId)) {
                is ApiResult.Success -> if (result.value.isEmpty()) {
                    FlashcardReviewUiState.Done(reviewed = 0, known = 0)
                } else {
                    FlashcardReviewUiState.Reviewing(
                        queue = result.value,
                        index = 0,
                        revealed = false,
                        reviewed = 0,
                        known = 0,
                    )
                }
                is ApiResult.Failure -> FlashcardReviewUiState.Failed(
                    kind = result.error.toKind(),
                    requestId = result.error.requestId,
                )
            }
        }
    }

    /** Flip the current card to show its back. */
    fun reveal() {
        _uiState.update { state ->
            if (state is FlashcardReviewUiState.Reviewing) state.copy(revealed = true) else state
        }
    }

    /**
     * Records the outcome for the current card and advances.
     *
     * A first attempt prepares a new pending review; a retry after an unknown
     * outcome resends the existing one so the server replays its result.
     */
    fun answer(outcome: ReviewOutcome) {
        val state = _uiState.value as? FlashcardReviewUiState.Reviewing ?: return
        if (state.submission is ReviewSubmission.Submitting) return

        val pending = pendingReview
            ?: repository.prepareReview(state.current.card.id, outcome).also { pendingReview = it }

        update { it.copy(submission = ReviewSubmission.Submitting) }

        viewModelScope.launch {
            when (val result = repository.submitReview(pending)) {
                is ApiResult.Success -> {
                    pendingReview = null
                    advance(known = pending.outcome == ReviewOutcome.Known)
                }
                is ApiResult.Failure -> {
                    val outcomeUnknown = result.error.leavesOutcomeUnknown()
                    if (!outcomeUnknown) pendingReview = null
                    update {
                        it.copy(
                            submission = ReviewSubmission.Failed(
                                kind = result.error.toKind(),
                                requestId = result.error.requestId,
                                canRetry = outcomeUnknown,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun advance(known: Boolean) {
        _uiState.update { state ->
            if (state !is FlashcardReviewUiState.Reviewing) return@update state
            val reviewed = state.reviewed + 1
            val knownCount = state.known + if (known) 1 else 0
            val nextIndex = state.index + 1
            if (nextIndex >= state.queue.size) {
                FlashcardReviewUiState.Done(reviewed = reviewed, known = knownCount)
            } else {
                state.copy(
                    index = nextIndex,
                    revealed = false,
                    reviewed = reviewed,
                    known = knownCount,
                    submission = ReviewSubmission.Idle,
                )
            }
        }
    }

    private fun update(transform: (FlashcardReviewUiState.Reviewing) -> FlashcardReviewUiState.Reviewing) {
        _uiState.update { state ->
            if (state is FlashcardReviewUiState.Reviewing) transform(state) else state
        }
    }

    companion object {
        const val ARG_DECK_ID = "deckId"
    }
}
