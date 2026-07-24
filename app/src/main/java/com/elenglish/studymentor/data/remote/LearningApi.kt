package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.LearningEventRequestDto
import com.elenglish.studymentor.data.remote.dto.LearningEventSubmissionDto
import com.elenglish.studymentor.data.remote.dto.LessonCompletionDto
import com.elenglish.studymentor.data.remote.dto.ProgressProjectionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface LearningApi {

    /**
     * Appends one immutable completed-learning event.
     *
     * The server accepts an event at most once per `Idempotency-Key`:
     * - `201` — accepted for the first time.
     * - `200` — same key and equivalent payload; the original result is replayed.
     * - `409` — same key with different data (`learning.idempotency_key_reused`).
     *
     * A retry must therefore resend the **same key and the same payload**.
     */
    @POST("learning-events")
    suspend fun appendLearningEvent(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: LearningEventRequestDto,
    ): Response<ApiEnvelope<LearningEventSubmissionDto>>

    @GET("me/progress")
    suspend fun getProgress(): Response<ApiEnvelope<ProgressProjectionDto>>

    /**
     * The authenticated user's completed lessons, derived server-side from
     * accepted immutable learning events. Survives app restart and reinstall
     * on the same account, unlike any client-local completion memory.
     */
    @GET("me/lesson-completions")
    suspend fun getLessonCompletions(): Response<ApiEnvelope<List<LessonCompletionDto>>>
}
