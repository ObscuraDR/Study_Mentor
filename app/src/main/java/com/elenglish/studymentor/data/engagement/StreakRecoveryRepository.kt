package com.elenglish.studymentor.data.engagement

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.network.safeApiCallWithStatus
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.remote.StreakRecoveryApi
import com.elenglish.studymentor.data.remote.dto.StreakRecoveryClaimDto
import com.elenglish.studymentor.data.remote.dto.StreakRecoveryEligibilityDto
import com.elenglish.studymentor.domain.model.PendingStreakRecoveryClaim
import com.elenglish.studymentor.domain.model.StreakRecoveryClaim
import com.elenglish.studymentor.domain.model.StreakRecoveryEligibility
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreakRecoveryRepository @Inject constructor(
    private val api: StreakRecoveryApi,
    private val uuidGenerator: UuidGenerator,
    private val json: Json,
) {
    suspend fun getEligibility(): ApiResult<StreakRecoveryEligibility> = safeApiCall(
        json = json,
        block = { api.getEligibility() },
        transform = StreakRecoveryEligibilityDto::toDomain,
    )

    fun prepareClaim(): PendingStreakRecoveryClaim = PendingStreakRecoveryClaim(uuidGenerator.newUuidV7())

    suspend fun claim(pending: PendingStreakRecoveryClaim): ApiResult<StreakRecoveryClaim> = safeApiCallWithStatus(
        json = json,
        block = { api.claim(pending.idempotencyKey) },
        transform = { status, dto -> dto.toDomain(wasReplay = status == HTTP_OK) },
    )

    private companion object { const val HTTP_OK = 200 }
}

private fun StreakRecoveryEligibilityDto.toDomain() = StreakRecoveryEligibility(eligible, reasonCode, missedLocalDate, policyVersion, streak)
private fun StreakRecoveryClaimDto.toDomain(wasReplay: Boolean) = StreakRecoveryClaim(missedLocalDate, policyVersion, acceptedAt, wasReplay)
