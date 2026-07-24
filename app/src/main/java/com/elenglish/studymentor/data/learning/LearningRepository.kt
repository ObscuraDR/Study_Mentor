package com.elenglish.studymentor.data.learning

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.network.safeApiCallWithStatus
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.remote.LearningApi
import com.elenglish.studymentor.data.remote.dto.LearningEventRequestDto
import com.elenglish.studymentor.data.remote.dto.LearningEventSubmissionDto
import com.elenglish.studymentor.data.remote.dto.LessonCompletionDto
import com.elenglish.studymentor.data.remote.dto.ProgressProjectionDto
import com.elenglish.studymentor.domain.model.CompletionResult
import com.elenglish.studymentor.domain.model.LearningEvent
import com.elenglish.studymentor.domain.model.LessonCompletion
import com.elenglish.studymentor.domain.model.PendingCompletion
import com.elenglish.studymentor.domain.model.ProgressProjection
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lesson completion and the server's progress projection.
 *
 * The client submits completion *intent* only. It never sends, calculates or
 * predicts XP or any progress total — the backend derives XP from the
 * authoritative lesson, and every figure shown comes back from the server.
 */
@Singleton
class LearningRepository @Inject constructor(
    private val learningApi: LearningApi,
    private val uuidGenerator: UuidGenerator,
    private val timeSource: TimeSource,
    private val json: Json,
) {

    /**
     * Captures a completion the user just performed.
     *
     * The idempotency key and timestamp are fixed here, once. Every subsequent
     * attempt reuses this exact object, which is what makes a retry safe: the
     * backend recognises the same key with an equivalent payload and replays the
     * original result instead of awarding a second time.
     */
    fun prepareCompletion(lessonId: String, durationSeconds: Int): PendingCompletion {
        require(durationSeconds >= 0) { "durationSeconds must not be negative" }
        return PendingCompletion(
            idempotencyKey = uuidGenerator.newUuidV7(),
            lessonId = lessonId,
            occurredAt = rfc3339Utc(timeSource.nowEpochMillis()),
            durationSeconds = durationSeconds,
        )
    }

    /**
     * Submits [pending], or resubmits it unchanged after a failure.
     *
     * Callers must pass back the *same* [PendingCompletion] they were given.
     * Building a fresh one for a retry would generate a new key and timestamp,
     * and the backend would treat it as a second, separate completion.
     */
    suspend fun submitCompletion(pending: PendingCompletion): ApiResult<CompletionResult> =
        safeApiCallWithStatus(
            json = json,
            block = {
                learningApi.appendLearningEvent(
                    idempotencyKey = pending.idempotencyKey,
                    body = LearningEventRequestDto(
                        lessonId = pending.lessonId,
                        occurredAt = pending.occurredAt,
                        durationSeconds = pending.durationSeconds,
                    ),
                )
            },
            transform = { httpStatus, body ->
                body.toDomain(wasReplay = httpStatus == HTTP_OK)
            },
        )

    suspend fun getProgress(): ApiResult<ProgressProjection> = safeApiCall(
        json = json,
        block = { learningApi.getProgress() },
        transform = ProgressProjectionDto::toDomain,
    )

    /**
     * The user's completed lessons, straight from the backend's own read
     * model. Never guessed or reconstructed locally from partial memory.
     */
    suspend fun getLessonCompletions(): ApiResult<List<LessonCompletion>> = safeApiCall(
        json = json,
        block = { learningApi.getLessonCompletions() },
        transform = { list -> list.map { it.toDomain() } },
    )

    private companion object {
        const val HTTP_OK = 200
    }
}

/** RFC 3339 UTC, as every timestamp in the contract must be. Shared across repositories. */
fun rfc3339Utc(epochMillis: Long): String =
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

private fun LearningEventSubmissionDto.toDomain(wasReplay: Boolean) = CompletionResult(
    event = LearningEvent(
        id = event.id,
        lessonId = event.lessonId,
        occurredAt = event.occurredAt,
        xpEarned = event.xpEarned,
        durationSeconds = event.durationSeconds,
        acceptedAt = event.acceptedAt,
    ),
    progress = progress.toDomain(),
    wasReplay = wasReplay,
)

private fun LessonCompletionDto.toDomain() = LessonCompletion(
    lessonId = lessonId,
    completedAt = completedAt,
)

private fun ProgressProjectionDto.toDomain() = ProgressProjection(
    completedLessons = completedLessons,
    totalLessons = totalLessons,
    completedTopics = completedTopics,
    totalTopics = totalTopics,
    completedSubjects = completedSubjects,
    totalSubjects = totalSubjects,
    totalXp = totalXp,
    learningTimeSeconds = learningTimeSeconds,
    completionPercentage = completionPercentage,
)
