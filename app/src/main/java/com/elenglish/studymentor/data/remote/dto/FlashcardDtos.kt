package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for flashcards.
 *
 * Note what the review request does **not** carry: no box, no due date, no
 * mastery, no XP. Scheduling is server-owned (`leitner-5box-v1`), and the
 * client submits only what happened — the same discipline as learning events
 * and quiz attempts.
 */

@Serializable
data class FlashcardDeckDto(
    val id: String,
    val lessonId: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val cardCount: Int,
    val displayOrder: Int,
    val active: Boolean,
)

@Serializable
data class FlashcardDto(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val hint: String? = null,
    val displayOrder: Int,
    val active: Boolean,
)

@Serializable
data class FlashcardDeckDetailDto(
    val id: String,
    val lessonId: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val cardCount: Int,
    val displayOrder: Int,
    val active: Boolean,
    val cards: List<FlashcardDto>,
)

@Serializable
data class FlashcardReviewStateDto(
    val cardId: String,
    /** Server-derived Leitner box 1..5. Never calculated on the device. */
    val box: Int,
    val dueAt: String,
    val lastReviewedAt: String? = null,
    val totalReviews: Int,
    val knownReviews: Int,
    val algorithmVersion: String,
)

@Serializable
data class FlashcardQueueEntryDto(
    val card: FlashcardDto,
    val state: FlashcardReviewStateDto,
)

@Serializable
data class FlashcardReviewRequestDto(
    val cardId: String,
    /** `known` or `forgot`. */
    val outcome: String,
    val reviewedAt: String,
)

@Serializable
data class FlashcardReviewResultDto(
    val reviewId: String,
    val state: FlashcardReviewStateDto,
    val acceptedAt: String,
)

@Serializable
data class FlashcardDeckResetResultDto(
    val deckId: String,
    val cardsReset: Int,
    val resetAt: String,
)
