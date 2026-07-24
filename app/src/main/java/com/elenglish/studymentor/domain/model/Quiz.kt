package com.elenglish.studymentor.domain.model

/** A quiz as listed for a lesson. */
data class QuizSummary(
    val id: String,
    val lessonId: String,
    val title: String,
    val description: String?,
    val questionCount: Int,
    val displayOrder: Int,
)

data class QuizOption(
    val id: String,
    val text: String,
    val displayOrder: Int,
)

/**
 * One question.
 *
 * There is no `isCorrect` here and there never will be: the contract does not
 * expose an answer key to the client, so scoring cannot be faked on the device.
 */
data class QuizQuestion(
    val id: String,
    val prompt: String,
    val displayOrder: Int,
    val options: List<QuizOption>,
)

data class Quiz(
    val id: String,
    val lessonId: String,
    val title: String,
    val description: String?,
    val questionCount: Int,
    val questions: List<QuizQuestion>,
)

/**
 * An attempt the learner has submitted or is retrying.
 *
 * As with lesson completion, the key and the answer list are captured once and
 * resent verbatim: the backend hashes the request to recognise an equivalent
 * retry, so answer order must stay stable.
 */
data class PendingQuizAttempt(
    val idempotencyKey: String,
    val quizId: String,
    /** Ordered by question display order; never reshuffled between attempts. */
    val answers: List<QuizAnswer>,
)

data class QuizAnswer(
    val questionId: String,
    val selectedOptionId: String,
)

/** The server's verdict for one question. */
data class QuizQuestionResult(
    val questionId: String,
    val selectedOptionId: String,
    val correct: Boolean,
    val correctOptionId: String,
)

/**
 * The server's scored result. Every number here is computed by the backend;
 * the app displays them and derives nothing.
 */
data class QuizAttemptResult(
    val attemptId: String,
    val quizId: String,
    val submittedAt: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercentage: Double,
    val questionResults: List<QuizQuestionResult>,
    /** True when the backend replayed an already-accepted attempt (HTTP 200). */
    val wasReplay: Boolean,
)
