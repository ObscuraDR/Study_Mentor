package com.elenglish.studymentor.domain.model

data class StreakRecoveryEligibility(
    val eligible: Boolean,
    val reasonCode: String?,
    val missedLocalDate: String?,
    val policyVersion: String,
    val streak: Int,
)

data class PendingStreakRecoveryClaim(val idempotencyKey: String)

data class StreakRecoveryClaim(
    val missedLocalDate: String,
    val policyVersion: String,
    val acceptedAt: String,
    val wasReplay: Boolean,
)
