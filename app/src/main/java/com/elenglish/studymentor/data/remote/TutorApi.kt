package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.TutorResponseDto
import com.elenglish.studymentor.data.remote.dto.TutorResponseRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TutorApi {

    /**
     * Asks the tutor about one lesson.
     *
     * `201` is a new response and `200` replays a stored one for the same
     * idempotency key. A safety refusal is **not** an error: it arrives as a
     * successful response with `status = refused`.
     */
    @POST("ai/tutor-responses")
    suspend fun generateResponse(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: TutorResponseRequestDto,
    ): Response<ApiEnvelope<TutorResponseDto>>
}
