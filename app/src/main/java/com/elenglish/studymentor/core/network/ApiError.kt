package com.elenglish.studymentor.core.network

/**
 * Transport-neutral failure model shared by every repository.
 *
 * [requestId] is carried through so a user-visible error can be correlated with
 * a backend log line without exposing response bodies.
 */
sealed interface ApiError {

    val requestId: String?

    /** The device could not reach the backend at all. */
    data class Network(
        override val requestId: String? = null,
        val cause: Throwable? = null,
    ) : ApiError

    /** The backend returned a canonical `{ error }` envelope. */
    data class Backend(
        val httpStatus: Int,
        val code: String,
        val message: String,
        override val requestId: String? = null,
    ) : ApiError

    /** A response was received but could not be mapped to the contract. */
    data class Unexpected(
        override val requestId: String? = null,
        val cause: Throwable? = null,
    ) : ApiError
}

/** Result of a single contract call. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val requestId: String? = null) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}
