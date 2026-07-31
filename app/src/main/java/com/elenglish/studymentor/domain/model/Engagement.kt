package com.elenglish.studymentor.domain.model

/** One mission goal: a count towards a target, and whether it is already met. */
data class EngagementGoal(
    val progress: Int,
    val target: Int,
    val completed: Boolean,
)

data class DailyEngagementMission(
    val startsAt: String,
    val endsAt: String,
    val templateKey: String,
    val objectives: List<EngagementGoal>,
    val lesson: EngagementGoal,
    val quiz: EngagementGoal,
    val reviews: EngagementGoal,
    /** True when any daily learning path is complete. */
    val completed: Boolean,
)

data class WeeklyEngagementMission(
    val startsAt: String,
    val endsAt: String,
    val templateKey: String,
    val objectives: List<EngagementGoal>,
    val actions: EngagementGoal,
    val days: EngagementGoal,
    val completed: Boolean,
)

data class EngagementMissions(
    val ruleVersion: String,
    val daily: DailyEngagementMission,
    val weekly: WeeklyEngagementMission,
)

/**
 * Backend-authoritative engagement projection: level/XP, a timezone-aware
 * streak, private achievements and daily/weekly missions.
 *
 * Every field is copied verbatim from `GET /me/engagement`. The device
 * computes no XP, no level, no streak day count, no achievement criteria and
 * no mission progress — this is a read model over the backend's own
 * immutable award ledger and entitlement rows.
 */
data class EngagementProjection(
    val totalXp: Int,
    val level: Int,
    val currentLevelXp: Int,
    val nextLevelThreshold: Int,
    val completionPercentage: Double,
    val levelCurveVersion: String,
    val timezone: String,
    val streak: Int,
    /** Stable achievement keys already unlocked server-side, e.g. "first_lesson". */
    val achievements: List<String>,
    val missions: EngagementMissions,
)
