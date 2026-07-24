package com.elenglish.studymentor.data.tutor

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCallWithStatus
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.remote.TutorApi
import com.elenglish.studymentor.data.remote.dto.TutorResponseDto
import com.elenglish.studymentor.data.remote.dto.TutorResponseRequestDto
import com.elenglish.studymentor.domain.model.PendingTutorMessage
import com.elenglish.studymentor.domain.model.TutorAnswer
import com.elenglish.studymentor.domain.model.TutorAnswerStatus
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AI tutor.
 *
 * Conversation history is deliberately **not** stored here or anywhere else on
 * the device: the contract exposes no conversation-history resource, so keeping
 * one locally would invent a record the backend does not have — and it would be
 * a record of what a learner asked.
 */
@Singleton
class TutorRepository @Inject constructor(
    private val tutorApi: TutorApi,
    private val uuidGenerator: UuidGenerator,
    private val json: Json,
) {

    /**
     * Captures a question for sending.
     *
     * The idempotency key is fixed here so a retry replays the stored answer
     * rather than paying for a second provider call.
     */
    fun prepareMessage(lessonId: String, message: String): PendingTutorMessage {
        val trimmed = message.trim()
        require(trimmed.isNotEmpty()) { "a tutor message must not be blank" }
        require(trimmed.length <= TutorResponseRequestDto.MAX_MESSAGE_LENGTH) {
            "a tutor message must be at most ${TutorResponseRequestDto.MAX_MESSAGE_LENGTH} characters"
        }
        return PendingTutorMessage(
            idempotencyKey = uuidGenerator.newUuidV7(),
            lessonId = lessonId,
            message = trimmed,
        )
    }

    /** Sends [pending], or resends it unchanged after an unknown outcome. */
    suspend fun ask(pending: PendingTutorMessage): ApiResult<TutorAnswer> =
        safeApiCallWithStatus(
            json = json,
            block = {
                tutorApi.generateResponse(
                    idempotencyKey = pending.idempotencyKey,
                    body = TutorResponseRequestDto(
                        lessonId = pending.lessonId,
                        message = pending.message,
                    ),
                )
            },
            transform = { httpStatus, dto -> dto.toDomain(wasReplay = httpStatus == HTTP_OK) },
        )

    private companion object {
        const val HTTP_OK = 200
    }
}

private fun TutorResponseDto.toDomain(wasReplay: Boolean) = TutorAnswer(
    responseId = responseId,
    lessonId = lessonId,
    answer = answer,
    createdAt = createdAt,
    status = TutorAnswerStatus.fromWire(status),
    wasReplay = wasReplay,
)
