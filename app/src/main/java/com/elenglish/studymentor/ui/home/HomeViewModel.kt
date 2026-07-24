package com.elenglish.studymentor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.catalog.CatalogRepository
import com.elenglish.studymentor.data.flashcard.FlashcardRepository
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.domain.model.Lesson
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.toKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The backend progress projection, read exactly as the Progress tab reads it. */
sealed interface ProgressSectionState {
    data object Loading : ProgressSectionState
    data class Content(val progress: ProgressProjection) : ProgressSectionState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : ProgressSectionState
}

/**
 * An honest "what to study next" prompt.
 *
 * Surfaces the first lesson, in backend order, that the backend's own
 * completion read model does not already show as completed (see
 * `LEARNING-COMPLETION-STATE-01-COMPLETION-REPORT.md`). The ordering itself is
 * still exactly the catalog's own backend order — nothing here re-sorts or
 * ranks lessons, it only skips ones already done.
 */
sealed interface ContinueLearningState {
    data object Loading : ContinueLearningState
    data class Available(val lessonId: String, val lessonTitle: String) : ContinueLearningState

    /** The catalog has no subjects, topics or lessons yet. Not an error. */
    data object Unavailable : ContinueLearningState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : ContinueLearningState
}

/**
 * Cards due for the [ContinueLearningState.Available] lesson's deck, if any.
 *
 * There is no endpoint for "all of my due cards across every lesson" — a queue
 * read requires a `deckId`, and a deck listing requires a `lessonId`
 * (`GET /flashcard-decks?lessonId=`). This section is therefore scoped to the
 * same lesson [ContinueLearningState] resolved, not a global count, and it
 * only becomes available once that resolution succeeds.
 */
sealed interface FlashcardsDueState {
    data object Loading : FlashcardsDueState
    data class Available(val deckId: String, val deckName: String, val dueCount: Int) : FlashcardsDueState

    /** No anchor lesson yet, no deck for it, or its queue is empty. Not an error. */
    data object Unavailable : FlashcardsDueState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : FlashcardsDueState
}

/**
 * The Home tab's state: three independently loading, independently failing
 * sections. One section failing never hides another that already succeeded.
 */
data class HomeUiState(
    val progress: ProgressSectionState = ProgressSectionState.Loading,
    val continueLearning: ContinueLearningState = ContinueLearningState.Loading,
    val flashcardsDue: FlashcardsDueState = FlashcardsDueState.Loading,
)

/**
 * Home dashboard: a real, backend-derived summary built entirely from data
 * other screens already fetch — no new endpoint, no local calculation.
 *
 * Every figure shown is either the backend's own progress projection or the
 * backend's own catalog/flashcard ordering. Nothing here computes a streak,
 * level, rank, coin balance or recommendation: none of those exist in the
 * contract, and inventing one on the device would be exactly the fabrication
 * the rest of this app refuses to do.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
    private val catalogRepository: CatalogRepository,
    private val flashcardRepository: FlashcardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadProgress()
        loadContinueLearning()
    }

    fun retryProgress() = loadProgress()

    /** Also re-resolves flashcards-due, since it depends on the same lesson. */
    fun retryContinueLearning() = loadContinueLearning()

    /** Retries only the flashcard calls; the anchor lesson is already known. */
    fun retryFlashcardsDue() {
        val lessonId = (_uiState.value.continueLearning as? ContinueLearningState.Available)
            ?.lessonId
            ?: return
        loadFlashcardsDue(lessonId)
    }

    private fun loadProgress() {
        _uiState.update { it.copy(progress = ProgressSectionState.Loading) }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    progress = when (val result = learningRepository.getProgress()) {
                        is ApiResult.Success -> ProgressSectionState.Content(result.value)
                        is ApiResult.Failure -> ProgressSectionState.Failed(
                            kind = result.error.toKind(),
                            requestId = result.error.requestId,
                        )
                    },
                )
            }
        }
    }

    private fun loadContinueLearning() {
        _uiState.update {
            it.copy(
                continueLearning = ContinueLearningState.Loading,
                flashcardsDue = FlashcardsDueState.Loading,
            )
        }
        viewModelScope.launch {
            when (val result = resolveAnchorLesson()) {
                is ApiResult.Success -> {
                    val lesson = result.value
                    if (lesson == null) {
                        _uiState.update {
                            it.copy(
                                continueLearning = ContinueLearningState.Unavailable,
                                flashcardsDue = FlashcardsDueState.Unavailable,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                continueLearning = ContinueLearningState.Available(
                                    lessonId = lesson.id,
                                    lessonTitle = lesson.title,
                                ),
                            )
                        }
                        loadFlashcardsDue(lesson.id)
                    }
                }

                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        continueLearning = ContinueLearningState.Failed(
                            kind = result.error.toKind(),
                            requestId = result.error.requestId,
                        ),
                        // Nothing to retry independently here: there is no
                        // lesson to look a deck up for until this succeeds.
                        flashcardsDue = FlashcardsDueState.Unavailable,
                    )
                }
            }
        }
    }

    private fun loadFlashcardsDue(lessonId: String) {
        _uiState.update { it.copy(flashcardsDue = FlashcardsDueState.Loading) }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    flashcardsDue = when (val result = resolveFlashcardsDue(lessonId)) {
                        is ApiResult.Success -> {
                            val info = result.value
                            if (info == null) {
                                FlashcardsDueState.Unavailable
                            } else {
                                FlashcardsDueState.Available(
                                    deckId = info.deckId,
                                    deckName = info.deckName,
                                    dueCount = info.dueCount,
                                )
                            }
                        }

                        is ApiResult.Failure -> FlashcardsDueState.Failed(
                            kind = result.error.toKind(),
                            requestId = result.error.requestId,
                        )
                    },
                )
            }
        }
    }

    /**
     * The first lesson, in backend order, that is not already completed:
     * walking subjects, then each subject's topics, then each topic's
     * lessons. `null` (not a failure) when the catalog is genuinely empty or
     * every lesson in it is already completed — both are valid backend
     * answers, not errors.
     *
     * A completions-read failure fails open (treated as "nothing known
     * completed yet") rather than failing the whole section: this prompt is
     * an aid, not a correctness-critical figure, and the worse case is
     * resuggesting a finished lesson, which the lesson's own completion state
     * (`LessonDetailViewModel`) already resolves honestly on its own.
     */
    private suspend fun resolveAnchorLesson(): ApiResult<Lesson?> {
        val completedLessonIds = when (val result = learningRepository.getLessonCompletions()) {
            is ApiResult.Success -> result.value.map { it.lessonId }.toSet()
            is ApiResult.Failure -> emptySet()
        }

        val subjects = when (val result = catalogRepository.getSubjects()) {
            is ApiResult.Success -> result.value.value
            is ApiResult.Failure -> return result
        }

        for (subject in subjects) {
            val topics = when (val result = catalogRepository.getTopics(subject.id)) {
                is ApiResult.Success -> result.value.value
                is ApiResult.Failure -> return result
            }
            for (topic in topics) {
                val lessons = when (val result = catalogRepository.getLessons(topic.id)) {
                    is ApiResult.Success -> result.value.value
                    is ApiResult.Failure -> return result
                }
                val firstIncomplete = lessons.firstOrNull { it.id !in completedLessonIds }
                if (firstIncomplete != null) return ApiResult.Success(firstIncomplete)
            }
        }
        return ApiResult.Success(null)
    }

    /** The anchor lesson's first deck and its due count, if either exists. */
    private suspend fun resolveFlashcardsDue(lessonId: String): ApiResult<FlashcardsDueInfo?> {
        val decks = when (val result = flashcardRepository.listDecks(lessonId)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result
        }
        val deck = decks.firstOrNull() ?: return ApiResult.Success(null)

        val queue = when (val result = flashcardRepository.getQueue(deck.id, dueOnly = true)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result
        }
        if (queue.isEmpty()) return ApiResult.Success(null)

        return ApiResult.Success(
            FlashcardsDueInfo(deckId = deck.id, deckName = deck.name, dueCount = queue.size),
        )
    }

    private data class FlashcardsDueInfo(val deckId: String, val deckName: String, val dueCount: Int)
}
