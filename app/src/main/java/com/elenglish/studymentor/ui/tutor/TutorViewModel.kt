package com.elenglish.studymentor.ui.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.remote.dto.TutorResponseRequestDto
import com.elenglish.studymentor.data.tutor.TutorRepository
import com.elenglish.studymentor.domain.model.PendingTutorMessage
import com.elenglish.studymentor.domain.model.TutorTurn
import com.elenglish.studymentor.ui.catalog.leavesOutcomeUnknown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a tutor request failed, in terms the UI can explain. */
enum class TutorErrorKind {
    Network,
    RateLimited,
    ProviderUnavailable,
    ProviderTimeout,
    LessonNotFound,
    Unauthorized,
    Generic,
}

data class TutorFailure(
    val kind: TutorErrorKind,
    val requestId: String?,
    val canRetry: Boolean,
)

data class TutorUiState(
    /**
     * The conversation as shown on screen. It lives only in this ViewModel:
     * the contract has no conversation-history resource, so nothing is
     * persisted and the exchange is gone when the screen is.
     */
    val turns: List<TutorTurn> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val failure: TutorFailure? = null,
) {
    val draftLength: Int get() = draft.trim().length

    val draftTooLong: Boolean
        get() = draftLength > TutorResponseRequestDto.MAX_MESSAGE_LENGTH

    val canSend: Boolean
        get() = !sending && draftLength > 0 && !draftTooLong
}

@HiltViewModel
class TutorViewModel @Inject constructor(
    private val repository: TutorRepository,
    private val uuidGenerator: UuidGenerator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle[ARG_LESSON_ID]) {
        "lessonId is required to ask the tutor"
    }

    private val _uiState = MutableStateFlow(TutorUiState())
    val uiState: StateFlow<TutorUiState> = _uiState.asStateFlow()

    /** Held so a retry resends the identical question under the same key. */
    private var pendingMessage: PendingTutorMessage? = null

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value, failure = null) }
    }

    /**
     * Sends the current draft, or resends the pending question after a failure
     * whose outcome was unknown.
     */
    fun send() {
        val state = _uiState.value
        if (state.sending) return

        val pending = pendingMessage ?: run {
            if (!state.canSend) return
            repository.prepareMessage(lessonId, state.draft).also { prepared ->
                pendingMessage = prepared
                // The question joins the transcript immediately, and the input
                // clears, so the learner can see what they asked while waiting.
                _uiState.update {
                    it.copy(
                        turns = it.turns + TutorTurn.Question(
                            id = uuidGenerator.newUuidV7(),
                            text = prepared.message,
                        ),
                        draft = "",
                    )
                }
            }
        }

        _uiState.update { it.copy(sending = true, failure = null) }

        viewModelScope.launch {
            when (val result = repository.ask(pending)) {
                is ApiResult.Success -> {
                    pendingMessage = null
                    _uiState.update {
                        it.copy(
                            sending = false,
                            turns = it.turns + TutorTurn.Answer(
                                id = result.value.responseId,
                                answer = result.value,
                            ),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    val error = result.error
                    val retryable = error.leavesOutcomeUnknown()
                    // Keeping the key means a retry replays the stored answer
                    // instead of paying for a second provider call.
                    if (!retryable) pendingMessage = null
                    _uiState.update {
                        it.copy(
                            sending = false,
                            failure = TutorFailure(
                                kind = error.toTutorKind(),
                                requestId = error.requestId,
                                canRetry = retryable,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun dismissFailure() {
        _uiState.update { it.copy(failure = null) }
    }

    companion object {
        const val ARG_LESSON_ID = "lessonId"
    }
}

internal fun ApiError.toTutorKind(): TutorErrorKind = when (this) {
    is ApiError.Network -> TutorErrorKind.Network
    is ApiError.Unexpected -> TutorErrorKind.Generic
    is ApiError.Backend -> when (code) {
        ApiErrorCodes.RATE_LIMIT_EXCEEDED -> TutorErrorKind.RateLimited
        "ai.provider_error" -> TutorErrorKind.ProviderUnavailable
        "ai.timeout" -> TutorErrorKind.ProviderTimeout
        ApiErrorCodes.LEARNING_RESOURCE_NOT_FOUND,
        ApiErrorCodes.LEARNING_UNKNOWN_LESSON,
        -> TutorErrorKind.LessonNotFound
        ApiErrorCodes.AUTH_SESSION_EXPIRED,
        ApiErrorCodes.AUTH_SESSION_REVOKED,
        -> TutorErrorKind.Unauthorized
        else -> TutorErrorKind.Generic
    }
}
