package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CampaignLessonNodeDto(
    val lessonId: String,
    val slug: String,
    val title: String,
    val displayOrder: Int,
    val completed: Boolean,
    val state: String,
)

@Serializable
data class CampaignTopicNodeDto(
    val topicId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val completedLessons: Int,
    val totalLessons: Int,
    val completionPercentage: Double,
    val lessons: List<CampaignLessonNodeDto>,
)

@Serializable
data class CampaignZoneDto(
    val subjectId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val state: String,
    val completedLessons: Int,
    val totalLessons: Int,
    val completionPercentage: Double,
    val topics: List<CampaignTopicNodeDto>,
)

@Serializable
data class CampaignProjectionDto(
    val campaignKey: String,
    val campaignVersion: String,
    val catalogVersion: String,
    val accessPolicy: String,
    val recommendedNodeId: String? = null,
    val completedLessons: Int,
    val totalLessons: Int,
    val completionPercentage: Double,
    val zones: List<CampaignZoneDto>,
)
