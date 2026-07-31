package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.BossAttemptRequestDto
import com.elenglish.studymentor.data.remote.dto.BossAttemptResultDto
import com.elenglish.studymentor.data.remote.dto.BossChallengeDto
import com.elenglish.studymentor.data.remote.dto.EconomyProjectionDto
import com.elenglish.studymentor.data.remote.dto.PurchaseRequestDto
import com.elenglish.studymentor.data.remote.dto.PurchaseResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface FullProductApi {
    @GET("me/boss-challenge")
    suspend fun getActiveBossChallenge(): Response<ApiEnvelope<BossChallengeDto>>

    @POST("boss-attempts")
    suspend fun submitBossAttempt(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: BossAttemptRequestDto,
    ): Response<ApiEnvelope<BossAttemptResultDto>>

    @GET("me/economy")
    suspend fun getEconomy(): Response<ApiEnvelope<EconomyProjectionDto>>

    @POST("me/shop-purchases")
    suspend fun purchase(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: PurchaseRequestDto,
    ): Response<ApiEnvelope<PurchaseResultDto>>
}
