package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for `GET /me/engagement`.
 *
 * Every numeric and boolean field here is server-derived from the backend's
 * immutable `engagement_awards` ledger, `achievement_entitlements`, and
 * accepted learning/quiz/flashcard evidence. There is no client-writable
 * counterpart: this contract is read-only.
 */
@Serializable
data class EngagementGoalDto(
    val progress: Int,
    val target: Int,
    val completed: Boolean,
)

@Serializable
data class DailyEngagementMissionDto(
    val startsAt: String,
    val endsAt: String,
    val templateKey: String,
    val objectives: List<EngagementGoalDto>,
    val lesson: EngagementGoalDto,
    val quiz: EngagementGoalDto,
    val reviews: EngagementGoalDto,
    val completed: Boolean,
)

@Serializable
data class WeeklyEngagementMissionDto(
    val startsAt: String,
    val endsAt: String,
    val templateKey: String,
    val objectives: List<EngagementGoalDto>,
    val actions: EngagementGoalDto,
    val days: EngagementGoalDto,
    val completed: Boolean,
)

@Serializable
data class EngagementMissionsDto(
    val ruleVersion: String,
    val daily: DailyEngagementMissionDto,
    val weekly: WeeklyEngagementMissionDto,
)

@Serializable
data class EngagementProjectionDto(
    val totalXp: Int,
    val level: Int,
    val currentLevelXp: Int,
    val nextLevelThreshold: Int,
    val completionPercentage: Double,
    val levelCurveVersion: String,
    val timezone: String,
    val streak: Int,
    val achievements: List<String>,
    val missions: EngagementMissionsDto,
)
