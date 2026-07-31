package com.elenglish.studymentor.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.data.catalog.CatalogRepository
import com.elenglish.studymentor.data.learning.CompletedLessonsRegistry
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.domain.model.CatalogData
import com.elenglish.studymentor.domain.model.CompletionResult
import com.elenglish.studymentor.domain.model.PendingCompletion
import com.elenglish.studymentor.domain.model.DataOrigin
import com.elenglish.studymentor.domain.model.Difficulty
import com.elenglish.studymentor.domain.model.Lesson
import com.elenglish.studymentor.domain.model.Subject
import com.elenglish.studymentor.domain.model.Topic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a catalog read failed, in terms the UI can render. */
enum class CatalogErrorKind {
    Network,
    NotFound,
    Unauthorized,
    Generic,
}

/**
 * State of one catalog list screen.
 *
 * [Empty] is deliberately distinct from [Failed]: an empty list is a valid
 * backend answer, not an error, and must not be shown as one.
 */
sealed interface CatalogUiState<out T> {
    data object Loading : CatalogUiState<Nothing>

    data class Content<T>(
        val items: List<T>,
        val origin: DataOrigin,
        val cachedAtEpochMillis: Long?,
    ) : CatalogUiState<T>

    data class Empty(val origin: DataOrigin) : CatalogUiState<Nothing>

    data class Failed(
        val kind: CatalogErrorKind,
        val requestId: String?,
    ) : CatalogUiState<Nothing>
}

@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogUiState<Subject>>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState<Subject>> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    init {
        load()
    }

    fun load() = loadList(_uiState) { repository.getSubjects() }
}

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val repository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val subjectId: String = checkNotNull(savedStateHandle[ARG_SUBJECT_ID]) {
        "subjectId is required to list topics"
    }

    private val _uiState = MutableStateFlow<CatalogUiState<Topic>>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState<Topic>> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    private val _subjectName = MutableStateFlow<String?>(null)
    val subjectName: StateFlow<String?> = _subjectName.asStateFlow()

    /** Java-friendly LiveData for subject name. */
    val subjectNameLiveData by lazy { _subjectName.asLiveData() }

    init {
        load()
    }

    fun load() {
        loadList(_uiState) { repository.getTopics(subjectId) }
        viewModelScope.launch {
            val result = repository.getSubject(subjectId)
            if (result is ApiResult.Success) _subjectName.value = result.value.value.name
        }
    }

    companion object {
        const val ARG_SUBJECT_ID = "subjectId"
    }
}

@HiltViewModel
class LessonsViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val learningRepository: LearningRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val topicId: String = checkNotNull(savedStateHandle[ARG_TOPIC_ID]) {
        "topicId is required to list lessons"
    }

    private val _rawState = MutableStateFlow<CatalogUiState<Lesson>>(CatalogUiState.Loading)
    private val _searchQuery = MutableStateFlow("")
    private val _difficultyFilter = MutableStateFlow<Difficulty?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val difficultyFilter: StateFlow<Difficulty?> = _difficultyFilter.asStateFlow()

    /**
     * Filtered view of the raw lesson list. Filtering is purely local — the
     * full list is fetched once and the query/filter narrow it in memory.
     */
    val uiState: StateFlow<CatalogUiState<Lesson>> = combine(
        _rawState, _searchQuery, _difficultyFilter,
    ) { raw, query, difficulty ->
        if (raw !is CatalogUiState.Content) return@combine raw
        val filtered = raw.items.filter { lesson ->
            (query.isBlank() || lesson.title.contains(query, ignoreCase = true)) &&
                (difficulty == null || lesson.difficulty == difficulty)
        }
        if (filtered.isEmpty()) CatalogUiState.Empty(raw.origin)
        else raw.copy(items = filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CatalogUiState.Loading,
    )

    private val _topicName = MutableStateFlow<String?>(null)
    val topicName: StateFlow<String?> = _topicName.asStateFlow()

    private val _completedLessonIds = MutableStateFlow<Set<String>>(emptySet())
    val completedLessonIds: StateFlow<Set<String>> = _completedLessonIds.asStateFlow()

    init {
        load()
    }

    fun load() {
        loadList(_rawState) { repository.getLessons(topicId) }
        viewModelScope.launch {
            val result = repository.getTopic(topicId)
            if (result is ApiResult.Success) _topicName.value = result.value.value.name
        }
        viewModelScope.launch {
            val result = learningRepository.getLessonCompletions()
            if (result is ApiResult.Success) {
                _completedLessonIds.value = result.value.map { it.lessonId }.toSet()
            }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onDifficultyFilterChange(difficulty: Difficulty?) { _difficultyFilter.value = difficulty }

    companion object {
        const val ARG_TOPIC_ID = "topicId"
    }
}

/** How the completion action is currently doing. */
sealed interface CompletionState {
    data object Idle : CompletionState

    /**
     * The backend already accepted a completion for this lesson earlier in this
     * session, so the action is not offered again.
     */
    data object AlreadyCompletedThisSession : CompletionState

    /**
     * The backend's own completion read model shows this lesson as already
     * completed, from any point in the past — including a prior app session
     * or install on this account. Unlike [AlreadyCompletedThisSession], this
     * is not session memory; it is confirmed fresh from the server on load.
     */
    data class CompletedPreviously(val completedAt: String) : CompletionState

    data object Submitting : CompletionState

    /** Accepted by the backend. [result] holds the server's own figures. */
    data class Accepted(val result: CompletionResult) : CompletionState

    /**
     * Submission failed. [canRetry] is true only for a network failure, where
     * resending the identical payload is safe.
     */
    data class Failed(
        val kind: CatalogErrorKind,
        val requestId: String?,
        val canRetry: Boolean,
    ) : CompletionState
}

/** State of the lesson detail screen. */
sealed interface LessonDetailUiState {
    data object Loading : LessonDetailUiState
    data class Content(
        val lesson: Lesson,
        val origin: DataOrigin,
        val completion: CompletionState = CompletionState.Idle,
    ) : LessonDetailUiState
    data class Failed(val kind: CatalogErrorKind, val requestId: String?) : LessonDetailUiState
}

@HiltViewModel
class LessonDetailViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val learningRepository: LearningRepository,
    private val completedLessons: CompletedLessonsRegistry,
    private val timeSource: TimeSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle[ARG_LESSON_ID]) {
        "lessonId is required to show a lesson"
    }

    private val _uiState = MutableStateFlow<LessonDetailUiState>(LessonDetailUiState.Loading)
    val uiState: StateFlow<LessonDetailUiState> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    /**
     * The completion awaiting acceptance, held for the lifetime of this screen.
     *
     * Retries reuse this object unchanged so the idempotency key and timestamp
     * stay identical. It is not persisted: there is no offline synchronisation
     * contract, so a completion that never reaches the backend is simply not
     * recorded, rather than silently queued and claimed later.
     */
    private var pendingCompletion: PendingCompletion? = null

    /**
     * When the lesson content first appeared, used to report how long the
     * learner actually spent on it. It is a real measurement of time on screen,
     * not an estimate — reporting the lesson's advertised length would claim
     * study time that may never have happened.
     */
    private var lessonOpenedAtMillis: Long? = null

    init {
        load()
    }

    fun load() {
        _uiState.value = LessonDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getLesson(lessonId)) {
                is ApiResult.Success -> LessonDetailUiState.Content(
                    lesson = result.value.value,
                    origin = result.value.origin,
                    completion = resolveInitialCompletion(),
                ).also {
                    if (lessonOpenedAtMillis == null) {
                        lessonOpenedAtMillis = timeSource.nowEpochMillis()
                    }
                }
                is ApiResult.Failure -> LessonDetailUiState.Failed(
                    kind = result.error.toKind(),
                    requestId = result.error.requestId,
                )
            }
        }
    }

    /**
     * What to show for completion before the learner acts.
     *
     * The session registry is checked first: it needs no network call and
     * covers the common "just completed it, still in this session" case. Only
     * when that is silent does this ask the backend's own completion read
     * model, which is what makes a completed lesson stay honestly labelled
     * after an app restart. A failure to read it is not surfaced as an error
     * here — it leaves the lesson looking incomplete, which is recoverable
     * (resubmitting is harmless; the backend awards no XP twice), rather than
     * blocking the whole screen for a secondary read.
     */
    private suspend fun resolveInitialCompletion(): CompletionState {
        if (completedLessons.wasCompletedThisSession(lessonId)) {
            return CompletionState.AlreadyCompletedThisSession
        }
        val result = learningRepository.getLessonCompletions()
        val completions = (result as? ApiResult.Success)?.value ?: return CompletionState.Idle
        val match = completions.firstOrNull { it.lessonId == lessonId } ?: return CompletionState.Idle
        return CompletionState.CompletedPreviously(match.completedAt)
    }

    /**
     * Marks the lesson complete.
     *
     * A first attempt prepares a new pending completion; a retry after a network
     * failure resends the existing one untouched.
     */
    fun markComplete(studiedSeconds: Int? = null) {
        val content = _uiState.value as? LessonDetailUiState.Content ?: return
        if (content.completion is CompletionState.Submitting) return
        if (content.completion is CompletionState.Accepted) return
        if (content.completion is CompletionState.AlreadyCompletedThisSession) return
        if (content.completion is CompletionState.CompletedPreviously) return

        val pending = pendingCompletion
            ?: learningRepository.prepareCompletion(
                lessonId = lessonId,
                durationSeconds = studiedSeconds ?: measuredSecondsOnScreen(),
            ).also { pendingCompletion = it }

        updateCompletion(CompletionState.Submitting)

        viewModelScope.launch {
            when (val result = learningRepository.submitCompletion(pending)) {
                is ApiResult.Success -> {
                    // The submission is settled; drop it so a later action
                    // cannot resend a key the backend has already consumed.
                    pendingCompletion = null
                    completedLessons.markCompleted(lessonId)
                    updateCompletion(CompletionState.Accepted(result.value))
                }

                is ApiResult.Failure -> {
                    val outcomeUnknown = result.error.leavesOutcomeUnknown()
                    // Keep the key whenever the server's decision is unknown, so
                    // a retry is recognised as the same submission. Discarding it
                    // would generate a new key and could record the completion a
                    // second time if the first attempt had in fact committed.
                    if (!outcomeUnknown) pendingCompletion = null
                    updateCompletion(
                        CompletionState.Failed(
                            kind = result.error.toKind(),
                            requestId = result.error.requestId,
                            canRetry = outcomeUnknown,
                        ),
                    )
                }
            }
        }
    }

    /** Seconds the lesson has actually been on screen, floored at zero. */
    private fun measuredSecondsOnScreen(): Int {
        val openedAt = lessonOpenedAtMillis ?: return 0
        val elapsedMillis = timeSource.nowEpochMillis() - openedAt
        return (elapsedMillis / MILLIS_PER_SECOND).coerceAtLeast(0).toInt()
    }

    private fun updateCompletion(state: CompletionState) {
        _uiState.update { current ->
            if (current is LessonDetailUiState.Content) current.copy(completion = state) else current
        }
    }

    companion object {
        const val ARG_LESSON_ID = "lessonId"
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

/** Shared list-loading behaviour: loading → content / empty / failure. */
private fun <T> ViewModel.loadList(
    state: MutableStateFlow<CatalogUiState<T>>,
    fetch: suspend () -> ApiResult<CatalogData<List<T>>>,
) {
    state.update { CatalogUiState.Loading }
    viewModelScope.launch {
        state.value = when (val result = fetch()) {
            is ApiResult.Success -> if (result.value.value.isEmpty()) {
                CatalogUiState.Empty(result.value.origin)
            } else {
                CatalogUiState.Content(
                    items = result.value.value,
                    origin = result.value.origin,
                    cachedAtEpochMillis = result.value.cachedAtEpochMillis,
                )
            }

            is ApiResult.Failure -> CatalogUiState.Failed(
                kind = result.error.toKind(),
                requestId = result.error.requestId,
            )
        }
    }
}

/**
 * True when the failure leaves it genuinely unclear whether the backend
 * accepted the submission.
 *
 * No response at all, or a server-side error, may or may not have committed the
 * event — a 5xx can be raised after the write. Retrying with the same
 * idempotency key is then both safe and necessary. A definitive 4xx rejection
 * (invalid lesson, reused key) is a decision, so the key can be discarded.
 */
internal fun ApiError.leavesOutcomeUnknown(): Boolean = when (this) {
    is ApiError.Network -> true
    is ApiError.Unexpected -> true
    is ApiError.Backend -> httpStatus >= HTTP_SERVER_ERROR || httpStatus == HTTP_TOO_MANY_REQUESTS
}

private const val HTTP_SERVER_ERROR = 500
private const val HTTP_TOO_MANY_REQUESTS = 429

internal fun ApiError.toKind(): CatalogErrorKind = when (this) {
    is ApiError.Network -> CatalogErrorKind.Network
    is ApiError.Unexpected -> CatalogErrorKind.Generic
    is ApiError.Backend -> when (code) {
        ApiErrorCodes.AUTH_SESSION_EXPIRED,
        ApiErrorCodes.AUTH_SESSION_REVOKED,
        -> CatalogErrorKind.Unauthorized

        "learning.resource_not_found",
        "learning.unknown_lesson",
        -> CatalogErrorKind.NotFound

        else -> CatalogErrorKind.Generic
    }
}
