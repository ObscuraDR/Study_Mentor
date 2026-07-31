package com.elenglish.studymentor.domain.model

/**
 * Read-only history of an incorrect answer. The correct answer and all counts
 * are supplied by the backend; the client does not rebuild attempt history.
 */
data class WrongAnswer(
    val questionId: String,
    val quizId: String,
    val quizTitle: String,
    val lessonId: String,
    val prompt: String,
    val selectedOptionId: String,
    val selectedOptionText: String,
    val correctOptionId: String,
    val correctOptionText: String,
    val lastAnsweredAt: String,
    val wrongCount: Int,
)

data class WrongAnswerPage(
    val items: List<WrongAnswer>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val hasNext: Boolean,
)
