package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for quizzes.
 *
 * Note what [QuizQuestionDto] and [QuizAnswerOptionDto] do **not** carry: there
 * is no correct-answer flag anywhere in the quiz read model. The client is
 * structurally unable to score an attempt, which is exactly the point —
 * correctness only exists in the server's [QuizAttemptResultDto].
 */

@Serializable
data class QuizSummaryDto(
    val id: String,
    val lessonId: String,
    val title: String,
    val description: String? = null,
    val questionCount: Int,
    val displayOrder: Int,
    val active: Boolean,
)

@Serializable
data class QuizAnswerOptionDto(
    val id: String,
    val text: String,
    val displayOrder: Int,
)

@Serializable
data class QuizQuestionDto(
    val id: String,
    val prompt: String,
    val type: String,
    val displayOrder: Int,
    val options: List<QuizAnswerOptionDto>,
)

@Serializable
data class QuizDetailDto(
    val id: String,
    val lessonId: String,
    val title: String,
    val description: String? = null,
    val questionCount: Int,
    val displayOrder: Int,
    val active: Boolean,
    val questions: List<QuizQuestionDto>,
)

@Serializable
data class QuizAttemptAnswerRequestDto(
    val questionId: String,
    val selectedOptionId: String,
)

/** The whole submission: a quiz reference and the learner's selections. */
@Serializable
data class QuizAttemptRequestDto(
    val quizId: String,
    val answers: List<QuizAttemptAnswerRequestDto>,
)

@Serializable
data class QuizQuestionResultDto(
    val questionId: String,
    val selectedOptionId: String,
    val correct: Boolean,
    val correctOptionId: String,
)

@Serializable
data class QuizAttemptResultDto(
    val attemptId: String,
    val quizId: String,
    val submittedAt: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    /** Server-computed. The client never derives a score. */
    val scorePercentage: Double,
    val questionResults: List<QuizQuestionResultDto>,
)
