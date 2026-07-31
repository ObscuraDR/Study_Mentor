package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.StreakRecoveryClaimDto
import com.elenglish.studymentor.data.remote.dto.StreakRecoveryEligibilityDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface StreakRecoveryApi {
    @GET("me/streak-recovery")
    suspend fun getEligibility(): Response<ApiEnvelope<StreakRecoveryEligibilityDto>>

    /** Empty-body claim; all recovery facts remain backend-owned. */
    @POST("me/streak-recoveries")
    suspend fun claim(@Header("Idempotency-Key") idempotencyKey: String): Response<ApiEnvelope<StreakRecoveryClaimDto>>
}
