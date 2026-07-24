package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/** Wire types for the learning catalog endpoints of OpenAPI v1. */

@Serializable
data class SubjectDto(
    val id: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val active: Boolean,
)

@Serializable
data class TopicDto(
    val id: String,
    val subjectId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val active: Boolean,
)

@Serializable
data class LessonDto(
    val id: String,
    val topicId: String,
    val slug: String,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    val difficulty: String,
    val displayOrder: Int,
    val active: Boolean,
)
