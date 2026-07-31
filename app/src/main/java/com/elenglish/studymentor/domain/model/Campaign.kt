package com.elenglish.studymentor.domain.model

enum class CampaignLessonState { NotStarted, Recommended, Completed, Unavailable }

data class CampaignLessonNode(
    val lessonId: String,
    val slug: String,
    val title: String,
    val displayOrder: Int,
    val completed: Boolean,
    val state: CampaignLessonState,
)

data class CampaignTopicNode(
    val topicId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val completedLessons: Int,
    val totalLessons: Int,
    val completionPercentage: Double,
    val lessons: List<CampaignLessonNode>,
)

data class CampaignZone(
    val subjectId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val state: String,
    val completedLessons: Int,
    val totalLessons: Int,
    val completionPercentage: Double,
    val topics: List<CampaignTopicNode>,
)

data class CampaignProjection(
    val campaignKey: String,
    val campaignVersion: String,
    val catalogVersion: String,
    val accessPolicy: String,
    val recommendedNodeId: String?,
    val completedLessons: Int,
    val totalLessons: Int,
    val completionPercentage: Double,
    val zones: List<CampaignZone>,
)
