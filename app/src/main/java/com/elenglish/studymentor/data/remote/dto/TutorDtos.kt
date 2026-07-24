package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for the AI tutor.
 *
 * `toString()` is redacted on both types. A learner's question and the tutor's
 * answer are personal content: a stray log line or crash report containing them
 * would leak what someone was struggling with.
 */
@Serializable
data class TutorResponseRequestDto(
    val lessonId: String,
    val message: String,
) {
    override fun toString(): String =
        "TutorResponseRequestDto(lessonId=$lessonId, message=<redacted ${message.length} chars>)"

    companion object {
        /** From `TutorResponseRequest.message.maxLength` in OpenAPI v1. */
        const val MAX_MESSAGE_LENGTH = 2000
    }
}

@Serializable
data class TutorResponseDto(
    val responseId: String,
    val lessonId: String,
    val answer: String,
    val createdAt: String,
    /** `completed`, `truncated` or `refused`. */
    val status: String,
) {
    override fun toString(): String =
        "TutorResponseDto(responseId=$responseId, status=$status, answer=<redacted>)"
}
