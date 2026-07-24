package com.elenglish.studymentor.data.flashcard

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.network.safeApiCallWithStatus
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.learning.rfc3339Utc
import com.elenglish.studymentor.data.remote.FlashcardApi
import com.elenglish.studymentor.data.remote.dto.FlashcardDeckDto
import com.elenglish.studymentor.data.remote.dto.FlashcardDto
import com.elenglish.studymentor.data.remote.dto.FlashcardQueueEntryDto
import com.elenglish.studymentor.data.remote.dto.FlashcardReviewRequestDto
import com.elenglish.studymentor.data.remote.dto.FlashcardReviewResultDto
import com.elenglish.studymentor.data.remote.dto.FlashcardReviewStateDto
import com.elenglish.studymentor.domain.model.Flashcard
import com.elenglish.studymentor.domain.model.FlashcardDeck
import com.elenglish.studymentor.domain.model.FlashcardQueueEntry
import com.elenglish.studymentor.domain.model.FlashcardReviewResult
import com.elenglish.studymentor.domain.model.FlashcardReviewState
import com.elenglish.studymentor.domain.model.PendingFlashcardReview
import com.elenglish.studymentor.domain.model.ReviewOutcome
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flashcard decks, the review queue, and review submission.
 *
 * No scheduling happens here. The box and due date always come back from the
 * server; the client submits only the outcome and when it happened. There is no
 * cache of review state — a stale box could mis-schedule a card.
 */
@Singleton
class FlashcardRepository @Inject constructor(
    private val flashcardApi: FlashcardApi,
    private val uuidGenerator: UuidGenerator,
    private val timeSource: TimeSource,
    private val json: Json,
) {

    suspend fun listDecks(lessonId: String): ApiResult<List<FlashcardDeck>> = safeApiCall(
        json = json,
        block = { flashcardApi.listDecks(lessonId) },
        transform = { dtos -> dtos.map(FlashcardDeckDto::toDomain) },
    )

    suspend fun getQueue(deckId: String, dueOnly: Boolean = true): ApiResult<List<FlashcardQueueEntry>> =
        safeApiCall(
            json = json,
            block = { flashcardApi.getQueue(deckId, dueOnly) },
            transform = { dtos -> dtos.map(FlashcardQueueEntryDto::toDomain) },
        )

    /**
     * Captures a review for submission.
     *
     * The idempotency key and timestamp are fixed here so a retry replays the
     * server's original result rather than recording a second review.
     */
    fun prepareReview(cardId: String, outcome: ReviewOutcome): PendingFlashcardReview =
        PendingFlashcardReview(
            idempotencyKey = uuidGenerator.newUuidV7(),
            cardId = cardId,
            outcome = outcome,
            reviewedAt = rfc3339Utc(timeSource.nowEpochMillis()),
        )

    /** Submits [pending], or resubmits it unchanged after an unknown outcome. */
    suspend fun submitReview(pending: PendingFlashcardReview): ApiResult<FlashcardReviewResult> =
        safeApiCallWithStatus(
            json = json,
            block = {
                flashcardApi.submitReview(
                    idempotencyKey = pending.idempotencyKey,
                    body = FlashcardReviewRequestDto(
                        cardId = pending.cardId,
                        outcome = pending.outcome.wireValue,
                        reviewedAt = pending.reviewedAt,
                    ),
                )
            },
            transform = { httpStatus, dto -> dto.toDomain(wasReplay = httpStatus == HTTP_OK) },
        )

    suspend fun resetDeck(deckId: String): ApiResult<Int> = safeApiCall(
        json = json,
        block = { flashcardApi.resetDeck(deckId, uuidGenerator.newUuidV7()) },
        transform = { it.cardsReset },
    )

    private companion object {
        const val HTTP_OK = 200
    }
}

private fun FlashcardDeckDto.toDomain() = FlashcardDeck(
    id = id,
    lessonId = lessonId,
    name = name,
    description = description,
    cardCount = cardCount,
    displayOrder = displayOrder,
)

private fun FlashcardDto.toDomain() = Flashcard(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    hint = hint,
    displayOrder = displayOrder,
)

private fun FlashcardReviewStateDto.toDomain() = FlashcardReviewState(
    cardId = cardId,
    box = box,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    totalReviews = totalReviews,
    knownReviews = knownReviews,
    algorithmVersion = algorithmVersion,
)

private fun FlashcardQueueEntryDto.toDomain() = FlashcardQueueEntry(
    card = card.toDomain(),
    state = state.toDomain(),
)

private fun FlashcardReviewResultDto.toDomain(wasReplay: Boolean) = FlashcardReviewResult(
    reviewId = reviewId,
    state = state.toDomain(),
    wasReplay = wasReplay,
)
