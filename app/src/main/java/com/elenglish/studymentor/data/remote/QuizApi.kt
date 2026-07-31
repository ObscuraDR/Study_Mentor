package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.QuizAttemptRequestDto
import com.elenglish.studymentor.data.remote.dto.QuizAttemptResultDto
import com.elenglish.studymentor.data.remote.dto.QuizDetailDto
import com.elenglish.studymentor.data.remote.dto.QuizSummaryDto
import com.elenglish.studymentor.data.remote.dto.WrongAnswerPageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface QuizApi {

    @GET("me/wrong-answers")
    suspend fun getWrongAnswers(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("lessonId") lessonId: String? = null,
        @Query("quizId") quizId: String? = null,
    ): Response<ApiEnvelope<WrongAnswerPageDto>>

    /** `lessonId` is a required query parameter. */
    @GET("quizzes")
    suspend fun listQuizzes(
        @Query("lessonId") lessonId: String,
    ): Response<ApiEnvelope<List<QuizSummaryDto>>>

    @GET("quizzes/{quizId}")
    suspend fun getQuiz(@Path("quizId") quizId: String): Response<ApiEnvelope<QuizDetailDto>>

    /**
     * Submits one immutable attempt and receives the server-scored result.
     *
     * The request carries only `quizId` and the selected option IDs. Score,
     * correctness, XP and timestamps are server-owned and forbidden in the
     * request. `201` is a new attempt; `200` replays an equivalent retry;
     * `409` means the key was reused with different data.
     */
    @POST("quiz-attempts")
    suspend fun submitAttempt(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: QuizAttemptRequestDto,
    ): Response<ApiEnvelope<QuizAttemptResultDto>>
}
