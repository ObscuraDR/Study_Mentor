package com.elenglish.studymentor.data.campaign

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.data.remote.CampaignApi
import com.elenglish.studymentor.data.remote.dto.CampaignLessonNodeDto
import com.elenglish.studymentor.data.remote.dto.CampaignProjectionDto
import com.elenglish.studymentor.data.remote.dto.CampaignTopicNodeDto
import com.elenglish.studymentor.data.remote.dto.CampaignZoneDto
import com.elenglish.studymentor.domain.model.CampaignLessonNode
import com.elenglish.studymentor.domain.model.CampaignLessonState
import com.elenglish.studymentor.domain.model.CampaignProjection
import com.elenglish.studymentor.domain.model.CampaignTopicNode
import com.elenglish.studymentor.domain.model.CampaignZone
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampaignRepository @Inject constructor(
    private val api: CampaignApi,
    private val json: Json,
) {
    suspend fun getCampaign(): ApiResult<CampaignProjection> = safeApiCall(
        json = json,
        block = { api.getCampaign() },
        transform = CampaignProjectionDto::toDomain,
    )
}

private fun CampaignProjectionDto.toDomain() = CampaignProjection(
    campaignKey, campaignVersion, catalogVersion, accessPolicy, recommendedNodeId,
    completedLessons, totalLessons, completionPercentage, zones.map(CampaignZoneDto::toDomain),
)

private fun CampaignZoneDto.toDomain() = CampaignZone(
    subjectId, slug, name, displayOrder, state, completedLessons, totalLessons,
    completionPercentage, topics.map(CampaignTopicNodeDto::toDomain),
)

private fun CampaignTopicNodeDto.toDomain() = CampaignTopicNode(
    topicId, slug, name, displayOrder, completedLessons, totalLessons,
    completionPercentage, lessons.map(CampaignLessonNodeDto::toDomain),
)

private fun CampaignLessonNodeDto.toDomain() = CampaignLessonNode(
    lessonId, slug, title, displayOrder, completed, state.toCampaignLessonState(),
)

private fun String.toCampaignLessonState() = when (this) {
    "completed" -> CampaignLessonState.Completed
    "recommended" -> CampaignLessonState.Recommended
    "not_started" -> CampaignLessonState.NotStarted
    else -> CampaignLessonState.Unavailable
}
