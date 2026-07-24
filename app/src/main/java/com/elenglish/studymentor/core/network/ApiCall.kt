package com.elenglish.studymentor.core.network

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Header the backend uses to return its correlation id. */
const val HEADER_REQUEST_ID = "X-Request-Id"

/**
 * Maps one Retrofit call onto [ApiResult].
 *
 * Failure mapping is centralised here so every repository reports errors the
 * same way and the `requestId` is never dropped: it is read from the error
 * envelope first, then from the `X-Request-Id` header, then from success `meta`.
 *
 * Nothing from the response body is logged or attached to the error message —
 * bodies can contain access tokens, refresh tokens and tutor prompts.
 */
suspend fun <T : Any, R> safeApiCall(
    json: Json,
    block: suspend () -> Response<ApiEnvelope<T>>,
    transform: (T) -> R,
): ApiResult<R> = try {
    val response = block()
    val requestId = response.headers()[HEADER_REQUEST_ID]

    if (response.isSuccessful) {
        val body = response.body()
        if (body == null) {
            ApiResult.Failure(ApiError.Unexpected(requestId = requestId))
        } else {
            ApiResult.Success(
                value = transform(body.data),
                requestId = body.meta?.requestId ?: requestId,
            )
        }
    } else {
        ApiResult.Failure(
            parseErrorBody(json, response.code(), response.errorBody()?.string(), requestId),
        )
    }
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network(cause = e))
} catch (e: HttpException) {
    ApiResult.Failure(ApiError.Unexpected(cause = e))
} catch (e: kotlinx.serialization.SerializationException) {
    ApiResult.Failure(ApiError.Unexpected(cause = e))
}

/**
 * Variant for endpoints whose HTTP status carries meaning beyond success.
 *
 * `POST /learning-events` answers `201` for a newly accepted event and `200`
 * when replaying an already-accepted one, so the status must reach the caller.
 */
suspend fun <T : Any, R> safeApiCallWithStatus(
    json: Json,
    block: suspend () -> Response<ApiEnvelope<T>>,
    transform: (httpStatus: Int, body: T) -> R,
): ApiResult<R> = try {
    val response = block()
    val requestId = response.headers()[HEADER_REQUEST_ID]

    if (response.isSuccessful) {
        val body = response.body()
        if (body == null) {
            ApiResult.Failure(ApiError.Unexpected(requestId = requestId))
        } else {
            ApiResult.Success(
                value = transform(response.code(), body.data),
                requestId = body.meta?.requestId ?: requestId,
            )
        }
    } else {
        ApiResult.Failure(
            parseErrorBody(json, response.code(), response.errorBody()?.string(), requestId),
        )
    }
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network(cause = e))
} catch (e: kotlinx.serialization.SerializationException) {
    ApiResult.Failure(ApiError.Unexpected(cause = e))
}

/**
 * Variant for endpoints that answer `204 No Content` (logout, logout-all).
 */
suspend fun safeEmptyApiCall(
    json: Json,
    block: suspend () -> Response<Unit>,
): ApiResult<Unit> = try {
    val response = block()
    val requestId = response.headers()[HEADER_REQUEST_ID]

    if (response.isSuccessful) {
        ApiResult.Success(Unit, requestId)
    } else {
        ApiResult.Failure(
            parseErrorBody(json, response.code(), response.errorBody()?.string(), requestId),
        )
    }
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network(cause = e))
} catch (e: kotlinx.serialization.SerializationException) {
    ApiResult.Failure(ApiError.Unexpected(cause = e))
}

/**
 * Parses a canonical `{ error }` envelope. A body that does not match the
 * contract degrades to [ApiError.Unexpected] rather than surfacing raw text.
 */
internal fun parseErrorBody(
    json: Json,
    httpStatus: Int,
    rawBody: String?,
    headerRequestId: String?,
): ApiError {
    if (rawBody.isNullOrBlank()) {
        return ApiError.Unexpected(requestId = headerRequestId)
    }
    return try {
        val envelope = json.decodeFromString(ApiErrorEnvelope.serializer(), rawBody)
        ApiError.Backend(
            httpStatus = httpStatus,
            code = envelope.error.code,
            message = envelope.error.message,
            requestId = envelope.error.requestId ?: headerRequestId,
        )
    } catch (e: kotlinx.serialization.SerializationException) {
        ApiError.Unexpected(requestId = headerRequestId, cause = e)
    }
}

/** Canonical error codes this client reacts to. */
object ApiErrorCodes {
    const val VALIDATION_INVALID_REQUEST = "validation.invalid_request"
    const val VALIDATION_INVALID_FIELD = "validation.invalid_field"
    const val AUTH_INVALID_CREDENTIALS = "auth.invalid_credentials"
    const val AUTH_EMAIL_ALREADY_REGISTERED = "auth.email_already_registered"
    const val AUTH_SESSION_EXPIRED = "auth.session_expired"
    const val AUTH_SESSION_REVOKED = "auth.session_revoked"
    const val AUTH_REFRESH_TOKEN_INVALID = "auth.refresh_token_invalid"
    const val AUTH_REFRESH_TOKEN_REUSED = "auth.refresh_token_reused"
    const val CONFLICT_REVISION_MISMATCH = "conflict.revision_mismatch"
    const val LEARNING_UNKNOWN_LESSON = "learning.unknown_lesson"
    const val LEARNING_INVALID_EVENT = "learning.invalid_event"
    const val LEARNING_RESOURCE_NOT_FOUND = "learning.resource_not_found"
    const val LEARNING_IDEMPOTENCY_KEY_REUSED = "learning.idempotency_key_reused"
    const val RATE_LIMIT_EXCEEDED = "rate_limit.exceeded"
    const val SERVER_INTERNAL = "server.internal"

    /** Codes that mean the stored session can never succeed again. */
    val UNRECOVERABLE_SESSION_CODES = setOf(
        AUTH_SESSION_REVOKED,
        AUTH_REFRESH_TOKEN_INVALID,
        AUTH_REFRESH_TOKEN_REUSED,
    )
}
