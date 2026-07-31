package com.elenglish.studymentor.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.quiz.QuizRepository
import com.elenglish.studymentor.domain.model.PendingQuizAttempt
import com.elenglish.studymentor.domain.model.Quiz
import com.elenglish.studymentor.domain.model.QuizAnswer
import com.elenglish.studymentor.domain.model.QuizAttemptResult
import com.elenglish.studymentor.domain.model.QuizSummary
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

/** Quizzes available for one lesson. */
sealed interface QuizListUiState {
    data object Loading : QuizListUiState
    data class Content(val quizzes: List<QuizSummary>) : QuizListUiState
    data object Empty : QuizListUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : QuizListUiState
}

@HiltViewModel
class QuizListViewModel @Inject constructor(
    private val repository: QuizRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle[ARG_LESSON_ID]) {
        "lessonId is required to list quizzes"
    }

    private val _uiState = MutableStateFlow<QuizListUiState>(QuizListUiState.Loading)
    val uiState: StateFlow<QuizListUiState> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    init {
        load()
    }

    fun load() {
        _uiState.value = QuizListUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.listQuizzes(lessonId)) {
                is ApiResult.Success -> if (result.value.isEmpty()) {
                    QuizListUiState.Empty
                } else {
                    QuizListUiState.Content(result.value)
                }
                is ApiResult.Failure -> QuizListUiState.Failed(
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

/** How the submission is doing. */
sealed interface QuizSubmissionState {
    data object Idle : QuizSubmissionState
    data object Submitting : QuizSubmissionState
    data class Scored(val result: QuizAttemptResult) : QuizSubmissionState
    data class Failed(
        val kind: CatalogErrorKind,
        val requestId: String?,
        val canRetry: Boolean,
    ) : QuizSubmissionState
}

sealed interface QuizUiState {
    data object Loading : QuizUiState

    data class Content(
        val quiz: Quiz,
        /** questionId → selected optionId. */
        val selections: Map<String, String> = emptyMap(),
        val submission: QuizSubmissionState = QuizSubmissionState.Idle,
    ) : QuizUiState {
        val answeredCount: Int get() = selections.size

        /**
         * The contract requires an answer for every question, so submission is
         * blocked until the learner has answered them all.
         */
        val canSubmit: Boolean
            get() = quiz.questions.isNotEmpty() &&
                answeredCount == quiz.questions.size &&
                submission !is QuizSubmissionState.Submitting &&
                submission !is QuizSubmissionState.Scored
    }

    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : QuizUiState
}

@HiltViewModel
class QuizAttemptViewModel @Inject constructor(
    private val repository: QuizRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val quizId: String = checkNotNull(savedStateHandle[ARG_QUIZ_ID]) {
        "quizId is required to take a quiz"
    }

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    /** Held so a retry resends the identical key and answer list. */
    private var pendingAttempt: PendingQuizAttempt? = null

    init {
        load()
    }

    fun load() {
        _uiState.value = QuizUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getQuiz(quizId)) {
                is ApiResult.Success -> QuizUiState.Content(result.value)
                is ApiResult.Failure -> QuizUiState.Failed(
                    kind = result.error.toKind(),
                    requestId = result.error.requestId,
                )
            }
        }
    }

    /** Single-choice: selecting an option replaces any previous one. */
    fun selectOption(questionId: String, optionId: String) {
        _uiState.update { current ->
            if (current !is QuizUiState.Content) return@update current
            // Answers are locked once the server has scored the attempt.
            if (current.submission is QuizSubmissionState.Scored) return@update current
            if (current.submission is QuizSubmissionState.Submitting) return@update current
            current.copy(selections = current.selections + (questionId to optionId))
        }
    }

    fun submit() {
        val content = _uiState.value as? QuizUiState.Content ?: return
        if (!content.canSubmit && pendingAttempt == null) return

        val pending = pendingAttempt ?: repository.prepareAttempt(
            quizId = quizId,
            // Ordered by the question order the backend sent, so the payload is
            // stable across retries.
            answers = content.quiz.questions.mapNotNull { question ->
                content.selections[question.id]?.let { QuizAnswer(question.id, it) }
            },
        ).also { pendingAttempt = it }

        updateSubmission(QuizSubmissionState.Submitting)

        viewModelScope.launch {
            when (val result = repository.submitAttempt(pending)) {
                is ApiResult.Success -> {
                    pendingAttempt = null
                    updateSubmission(QuizSubmissionState.Scored(result.value))
                }
                is ApiResult.Failure -> {
                    val outcomeUnknown = result.error.leavesOutcomeUnknown()
                    // Same rule as lesson completion: keep the key whenever the
                    // server's decision is unknown, so a retry is recognised as
                    // the same attempt rather than creating a second one.
                    if (!outcomeUnknown) pendingAttempt = null
                    updateSubmission(
                        QuizSubmissionState.Failed(
                            kind = result.error.toKind(),
                            requestId = result.error.requestId,
                            canRetry = outcomeUnknown,
                        ),
                    )
                }
            }
        }
    }

    private fun updateSubmission(state: QuizSubmissionState) {
        _uiState.update { current ->
            if (current is QuizUiState.Content) current.copy(submission = state) else current
        }
    }

    companion object {
        const val ARG_QUIZ_ID = "quizId"
    }
}
