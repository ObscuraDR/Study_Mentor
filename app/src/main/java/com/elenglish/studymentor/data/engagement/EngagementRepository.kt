package com.elenglish.studymentor.data.engagement

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.data.remote.EngagementApi
import com.elenglish.studymentor.data.remote.dto.DailyEngagementMissionDto
import com.elenglish.studymentor.data.remote.dto.EngagementGoalDto
import com.elenglish.studymentor.data.remote.dto.EngagementMissionsDto
import com.elenglish.studymentor.data.remote.dto.EngagementProjectionDto
import com.elenglish.studymentor.data.remote.dto.WeeklyEngagementMissionDto
import com.elenglish.studymentor.domain.model.DailyEngagementMission
import com.elenglish.studymentor.domain.model.EngagementGoal
import com.elenglish.studymentor.domain.model.EngagementMissions
import com.elenglish.studymentor.domain.model.EngagementProjection
import com.elenglish.studymentor.domain.model.WeeklyEngagementMission
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the backend's engagement projection.
 *
 * This repository has no write methods. Level, XP, streak, achievements and
 * mission progress are derived entirely server-side from the existing
 * lesson/quiz/flashcard evidence; the client only ever displays them.
 */
@Singleton
class EngagementRepository @Inject constructor(
    private val engagementApi: EngagementApi,
    private val json: Json,
) {
    suspend fun getEngagement(): ApiResult<EngagementProjection> = safeApiCall(
        json = json,
        block = { engagementApi.getEngagement() },
        transform = EngagementProjectionDto::toDomain,
    )
}

private fun EngagementProjectionDto.toDomain() = EngagementProjection(
    totalXp = totalXp,
    level = level,
    currentLevelXp = currentLevelXp,
    nextLevelThreshold = nextLevelThreshold,
    completionPercentage = completionPercentage,
    levelCurveVersion = levelCurveVersion,
    timezone = timezone,
    streak = streak,
    achievements = achievements,
    missions = missions.toDomain(),
)

private fun EngagementMissionsDto.toDomain() = EngagementMissions(
    ruleVersion = ruleVersion,
    daily = daily.toDomain(),
    weekly = weekly.toDomain(),
)

private fun DailyEngagementMissionDto.toDomain() = DailyEngagementMission(
    startsAt = startsAt,
    endsAt = endsAt,
    templateKey = templateKey,
    objectives = objectives.map(EngagementGoalDto::toDomain),
    lesson = lesson.toDomain(),
    quiz = quiz.toDomain(),
    reviews = reviews.toDomain(),
    completed = completed,
)

private fun WeeklyEngagementMissionDto.toDomain() = WeeklyEngagementMission(
    startsAt = startsAt,
    endsAt = endsAt,
    templateKey = templateKey,
    objectives = objectives.map(EngagementGoalDto::toDomain),
    actions = actions.toDomain(),
    days = days.toDomain(),
    completed = completed,
)

private fun EngagementGoalDto.toDomain() = EngagementGoal(
    progress = progress,
    target = target,
    completed = completed,
)
