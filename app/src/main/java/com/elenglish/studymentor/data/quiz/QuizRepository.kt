package com.elenglish.studymentor.data.quiz

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.network.safeApiCallWithStatus
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.remote.QuizApi
import com.elenglish.studymentor.data.remote.dto.QuizAttemptAnswerRequestDto
import com.elenglish.studymentor.data.remote.dto.QuizAttemptRequestDto
import com.elenglish.studymentor.data.remote.dto.QuizAttemptResultDto
import com.elenglish.studymentor.data.remote.dto.QuizDetailDto
import com.elenglish.studymentor.data.remote.dto.QuizSummaryDto
import com.elenglish.studymentor.data.remote.dto.WrongAnswerDto
import com.elenglish.studymentor.data.remote.dto.WrongAnswerPageDto
import com.elenglish.studymentor.domain.model.PendingQuizAttempt
import com.elenglish.studymentor.domain.model.Quiz
import com.elenglish.studymentor.domain.model.QuizAnswer
import com.elenglish.studymentor.domain.model.QuizAttemptResult
import com.elenglish.studymentor.domain.model.QuizOption
import com.elenglish.studymentor.domain.model.QuizQuestion
import com.elenglish.studymentor.domain.model.QuizQuestionResult
import com.elenglish.studymentor.domain.model.QuizSummary
import com.elenglish.studymentor.domain.model.WrongAnswer
import com.elenglish.studymentor.domain.model.WrongAnswerPage
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quizzes: reading them, and submitting attempts for the server to score.
 *
 * There is no local scoring path anywhere in this class, and no cache: a quiz
 * answered against a stale copy could reference options the server no longer
 * recognises.
 */
@Singleton
class QuizRepository @Inject constructor(
    private val quizApi: QuizApi,
    private val uuidGenerator: UuidGenerator,
    private val json: Json,
) {

    suspend fun getWrongAnswers(
        page: Int = 1,
        pageSize: Int = 20,
        lessonId: String? = null,
        quizId: String? = null,
    ): ApiResult<WrongAnswerPage> {
        require(page > 0) { "page must be positive" }
        require(pageSize in 1..100) { "pageSize must be between 1 and 100" }
        return safeApiCall(
            json = json,
            block = { quizApi.getWrongAnswers(page, pageSize, lessonId, quizId) },
            transform = WrongAnswerPageDto::toDomain,
        )
    }

    suspend fun listQuizzes(lessonId: String): ApiResult<List<QuizSummary>> = safeApiCall(
        json = json,
        block = { quizApi.listQuizzes(lessonId) },
        transform = { dtos -> dtos.map(QuizSummaryDto::toDomain) },
    )

    suspend fun getQuiz(quizId: String): ApiResult<Quiz> = safeApiCall(
        json = json,
        block = { quizApi.getQuiz(quizId) },
        transform = QuizDetailDto::toDomain,
    )

    /**
     * Captures an attempt for submission.
     *
     * [answers] are ordered by the caller and stored as given; the backend
     * hashes the request body to recognise an equivalent retry, so reordering
     * them later would look like a different submission.
     */
    fun prepareAttempt(quizId: String, answers: List<QuizAnswer>): PendingQuizAttempt {
        require(answers.isNotEmpty()) { "an attempt must contain at least one answer" }
        return PendingQuizAttempt(
            idempotencyKey = uuidGenerator.newUuidV7(),
            quizId = quizId,
            answers = answers,
        )
    }

    /** Submits [pending], or resubmits it unchanged after an unknown outcome. */
    suspend fun submitAttempt(pending: PendingQuizAttempt): ApiResult<QuizAttemptResult> =
        safeApiCallWithStatus(
            json = json,
            block = {
                quizApi.submitAttempt(
                    idempotencyKey = pending.idempotencyKey,
                    body = QuizAttemptRequestDto(
                        quizId = pending.quizId,
                        answers = pending.answers.map {
                            QuizAttemptAnswerRequestDto(it.questionId, it.selectedOptionId)
                        },
                    ),
                )
            },
            transform = { httpStatus, dto -> dto.toDomain(wasReplay = httpStatus == HTTP_OK) },
        )

    private companion object {
        const val HTTP_OK = 200
    }
}

private fun WrongAnswerPageDto.toDomain() = WrongAnswerPage(
    items = items.map(WrongAnswerDto::toDomain),
    page = page,
    pageSize = pageSize,
    totalItems = totalItems,
    hasNext = hasNext,
)

private fun WrongAnswerDto.toDomain() = WrongAnswer(
    questionId, quizId, quizTitle, lessonId, prompt, selectedOptionId,
    selectedOptionText, correctOptionId, correctOptionText, lastAnsweredAt, wrongCount,
)

private fun QuizSummaryDto.toDomain() = QuizSummary(
    id = id,
    lessonId = lessonId,
    title = title,
    description = description,
    questionCount = questionCount,
    displayOrder = displayOrder,
)

private fun QuizDetailDto.toDomain() = Quiz(
    id = id,
    lessonId = lessonId,
    title = title,
    description = description,
    questionCount = questionCount,
    // Backend order is preserved exactly, as everywhere else in the catalog.
    questions = questions.map { question ->
        QuizQuestion(
            id = question.id,
            prompt = question.prompt,
            displayOrder = question.displayOrder,
            options = question.options.map { QuizOption(it.id, it.text, it.displayOrder) },
        )
    },
)

private fun QuizAttemptResultDto.toDomain(wasReplay: Boolean) = QuizAttemptResult(
    attemptId = attemptId,
    quizId = quizId,
    submittedAt = submittedAt,
    totalQuestions = totalQuestions,
    correctAnswers = correctAnswers,
    scorePercentage = scorePercentage,
    questionResults = questionResults.map {
        QuizQuestionResult(it.questionId, it.selectedOptionId, it.correct, it.correctOptionId)
    },
    wasReplay = wasReplay,
)
