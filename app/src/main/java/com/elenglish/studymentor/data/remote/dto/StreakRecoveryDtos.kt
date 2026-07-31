package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StreakRecoveryEligibilityDto(
    val eligible: Boolean,
    val reasonCode: String? = null,
    val missedLocalDate: String? = null,
    val policyVersion: String,
    val streak: Int,
)

@Serializable
data class StreakRecoveryClaimDto(
    val status: String,
    val missedLocalDate: String,
    val policyVersion: String,
    val acceptedAt: String,
)
