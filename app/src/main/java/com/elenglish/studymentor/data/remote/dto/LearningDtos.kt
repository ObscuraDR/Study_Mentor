package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for learning events and progress.
 *
 * Note what the request does **not** contain: no XP, no score, no progress
 * total. The contract states plainly that XP is derived from the authoritative
 * lesson by the server and that clients must not calculate or claim it.
 */
@Serializable
data class LearningEventRequestDto(
    val lessonId: String,
    val occurredAt: String,
    val durationSeconds: Int,
    val eventType: String = EVENT_TYPE_LESSON_COMPLETED,
) {
    companion object {
        /** The only event type the contract currently accepts. */
        const val EVENT_TYPE_LESSON_COMPLETED = "lesson.completed"
    }
}

@Serializable
data class LearningEventDto(
    val id: String,
    val userId: String,
    val lessonId: String,
    val occurredAt: String,
    /** Server-derived. Displayed, never computed or predicted on the device. */
    val xpEarned: Int,
    val durationSeconds: Int,
    val eventType: String,
    val acceptedAt: String,
)

@Serializable
data class ProgressProjectionDto(
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

@Serializable
data class LearningEventSubmissionDto(
    val event: LearningEventDto,
    val progress: ProgressProjectionDto,
)

/**
 * One completed lesson, derived server-side from accepted immutable learning
 * events. [completedAt] is the earliest accepted `lesson.completed` event's
 * `occurredAt` for that lesson; completing it again never changes this value.
 */
@Serializable
data class LessonCompletionDto(
    val lessonId: String,
    val completedAt: String,
)
