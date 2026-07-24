package com.elenglish.studymentor.domain.model

/** A flashcard deck listed for a lesson. */
data class FlashcardDeck(
    val id: String,
    val lessonId: String,
    val name: String,
    val description: String?,
    val cardCount: Int,
    val displayOrder: Int,
)

/** One card. There is no correctness or box field here — those are review state. */
data class Flashcard(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val hint: String?,
    val displayOrder: Int,
)

/** The learner's review outcome for a card. */
enum class ReviewOutcome(val wireValue: String) {
    Known("known"),
    Forgot("forgot"),
}

/**
 * The server's review state for one card. Every value here is derived by the
 * backend from the review event stream; the client displays them and computes
 * nothing.
 */
data class FlashcardReviewState(
    val cardId: String,
    /** Leitner box 1..5. 1 is new, 5 is fully learned. */
    val box: Int,
    val dueAt: String,
    val lastReviewedAt: String?,
    val totalReviews: Int,
    val knownReviews: Int,
    val algorithmVersion: String,
) {
    /** Presentation only: box 1 → 0%, box 5 → 100%. Not a client calculation of mastery. */
    val memoryStrengthPercent: Int get() = (box - 1) * 25
}

/** A card paired with its review state, as returned by the queue. */
data class FlashcardQueueEntry(
    val card: Flashcard,
    val state: FlashcardReviewState,
)

/**
 * A review awaiting submission.
 *
 * The key and [reviewedAt] are captured once, when the learner answers, and
 * resent unchanged on retry — the backend hashes the request to recognise an
 * equivalent retry, so regenerating either would look like a different review.
 */
data class PendingFlashcardReview(
    val idempotencyKey: String,
    val cardId: String,
    val outcome: ReviewOutcome,
    val reviewedAt: String,
)

/** Result of submitting a review: the recalculated state. */
data class FlashcardReviewResult(
    val reviewId: String,
    val state: FlashcardReviewState,
    /** True when the backend replayed an already-accepted review (HTTP 200). */
    val wasReplay: Boolean,
)
