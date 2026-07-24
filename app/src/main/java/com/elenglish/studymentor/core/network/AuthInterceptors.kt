package com.elenglish.studymentor.core.network

import com.elenglish.studymentor.data.session.AccessTokenHolder
import com.elenglish.studymentor.data.session.RefreshOutcome
import com.elenglish.studymentor.data.session.SessionRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Header the backend requires in order to select the Android transport rules. */
private const val HEADER_CLIENT_PLATFORM = "X-Client-Platform"
private const val CLIENT_PLATFORM_ANDROID = "android"
private const val HEADER_AUTHORIZATION = "Authorization"

/**
 * Endpoints that must never receive an `Authorization` header or be retried
 * after a refresh.
 *
 * `auth/refresh` above all: letting it into the retry loop would make a failed
 * refresh trigger another refresh, and a rotated-token replay is what the
 * backend reports as `auth.refresh_token_reused` — which revokes the whole
 * session family.
 */
internal val UNAUTHENTICATED_PATHS = setOf(
    "auth/register",
    "auth/login",
    "auth/refresh",
)

/** Every path under `auth/` is excluded from the refresh-and-retry loop. */
internal fun Request.isAuthEndpoint(): Boolean =
    url.encodedPath.substringAfter("/api/v1/", url.encodedPath).startsWith("auth/")

internal fun Request.isUnauthenticatedEndpoint(): Boolean {
    val relativePath = url.encodedPath.substringAfter("/api/v1/", url.encodedPath)
    return UNAUTHENTICATED_PATHS.any { relativePath.startsWith(it) }
}

/** Adds the mandatory platform header to every request. */
@Singleton
class ClientPlatformInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_CLIENT_PLATFORM, CLIENT_PLATFORM_ANDROID)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Attaches the in-memory access token to protected requests.
 *
 * Register, login and refresh are skipped: they establish a session rather than
 * consume one, and sending a stale token with them would be pointless noise.
 */
@Singleton
class AccessTokenInterceptor @Inject constructor(
    private val accessTokenHolder: AccessTokenHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isUnauthenticatedEndpoint()) return chain.proceed(request)

        val token = accessTokenHolder.get() ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header(HEADER_AUTHORIZATION, "Bearer $token")
                .build(),
        )
    }
}

/**
 * Refreshes the access token once when a protected request comes back `401`.
 *
 * Guarantees:
 * - Requests under `auth/` are never retried, so refresh cannot recurse.
 * - Exactly **one** retry per original request; a second `401` gives up and the
 *   failure surfaces to the caller.
 * - Concurrent 401s share a single refresh, coordinated by
 *   [SessionRepository.refreshAccessToken]; the token seen before the attempt is
 *   passed in so a caller that lost the race adopts the winner's token instead
 *   of spending the rotated refresh token again.
 *
 * [SessionRepository] is injected through a [Provider] because it depends on the
 * Retrofit client that this authenticator is part of.
 */
@Singleton
class RefreshTokenAuthenticator @Inject constructor(
    private val sessionRepositoryProvider: Provider<SessionRepository>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request

        if (request.isAuthEndpoint()) return null
        if (response.priorResponseCount() >= MAX_RETRIES) return null

        val tokenUsedForFailedCall = request.header(HEADER_AUTHORIZATION)
            ?.removePrefix("Bearer ")
            ?.takeIf { it.isNotBlank() }

        // OkHttp's authenticator contract is blocking; the refresh itself is a
        // suspending call, and the coordinator serialises concurrent callers.
        val outcome = runBlocking {
            sessionRepositoryProvider.get().refreshAccessToken(tokenUsedForFailedCall)
        }

        return when (outcome) {
            is RefreshOutcome.Success -> request.newBuilder()
                .header(HEADER_AUTHORIZATION, "Bearer ${outcome.accessToken}")
                .build()

            // Session is finished, or the network is down: do not retry. The
            // repository has already cleared local state for SessionEnded.
            RefreshOutcome.SessionEnded, is RefreshOutcome.Transient -> null
        }
    }

    /** How many times OkHttp has already replayed this request. */
    private fun Response.priorResponseCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_RETRIES = 1
    }
}
