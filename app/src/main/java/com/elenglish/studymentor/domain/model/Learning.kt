package com.elenglish.studymentor.domain.model

/**
 * A completion the user has requested but the backend has not yet accepted.
 *
 * The key and [occurredAt] are captured **once**, when the user acts, and reused
 * verbatim on every retry. The backend hashes the client-owned request fields to
 * decide whether a repeated key is the same submission: regenerating either
 * value would produce a different payload and be rejected as
 * `learning.idempotency_key_reused`.
 *
 * This lives in memory for the current session only. It is deliberately **not**
 * a persisted offline queue — the backend exposes no synchronisation contract
 * with cursor, conflict and retry semantics, so promising durable offline
 * completion would be a promise the platform cannot keep.
 */
data class PendingCompletion(
    val idempotencyKey: String,
    val lessonId: String,
    val occurredAt: String,
    val durationSeconds: Int,
)

/** A learning event as accepted and returned by the backend. */
data class LearningEvent(
    val id: String,
    val lessonId: String,
    val occurredAt: String,
    /** Awarded by the backend. The client never calculates or predicts this. */
    val xpEarned: Int,
    val durationSeconds: Int,
    val acceptedAt: String,
)

/**
 * The server's progress projection. Every field is displayed exactly as
 * received; none is derived, accumulated or adjusted on the device.
 */
data class ProgressProjection(
    val completedLessons: Int,
    val totalLessons: Int,
    val completedTopics: Int,
    val totalTopics: Int,
    val completedSubjects: Int,
    val totalSubjects: Int,
    val totalXp: Int,
    val learningTimeSeconds: Int,
    val completionPercentage: Double,
)

/** Result of submitting a completion: the accepted event and fresh progress. */
data class CompletionResult(
    val event: LearningEvent,
    val progress: ProgressProjection,
    /** True when the backend replayed an already-accepted submission (HTTP 200). */
    val wasReplay: Boolean,
)

/**
 * One lesson the backend confirms as completed, derived from accepted
 * immutable learning events. Unlike [PendingCompletion] or any session-only
 * memory, this is authoritative and survives an app restart.
 */
data class LessonCompletion(
    val lessonId: String,
    val completedAt: String,
)
