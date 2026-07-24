package com.elenglish.studymentor.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical OpenAPI v1 envelopes.
 *
 * Success: `{ "data": ..., "meta": { "requestId": "..." } }`
 * Error:   `{ "error": { "code", "message", "details?", "requestId" } }`
 *
 * Source of truth: `contracts/openapi/ai-study-mentor.v1.openapi.json`.
 */
@Serializable
data class ResponseMeta(
    @SerialName("requestId") val requestId: String? = null,
)

@Serializable
data class ApiEnvelope<T>(
    @SerialName("data") val data: T,
    @SerialName("meta") val meta: ResponseMeta? = null,
)

@Serializable
data class ApiErrorDetail(
    @SerialName("field") val field: String? = null,
    @SerialName("issue") val issue: String? = null,
)

@Serializable
data class ApiErrorBody(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("details") val details: List<ApiErrorDetail>? = null,
    @SerialName("requestId") val requestId: String? = null,
)

@Serializable
data class ApiErrorEnvelope(
    @SerialName("error") val error: ApiErrorBody,
)
