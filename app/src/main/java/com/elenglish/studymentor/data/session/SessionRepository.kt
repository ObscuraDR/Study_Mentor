package com.elenglish.studymentor.data.session

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.network.safeEmptyApiCall
import com.elenglish.studymentor.core.session.SessionScopedStore
import com.elenglish.studymentor.core.session.SessionStateHolder
import com.elenglish.studymentor.data.remote.AuthApi
import com.elenglish.studymentor.data.remote.dto.AndroidRefreshRequestDto
import com.elenglish.studymentor.data.remote.dto.LoginRequestDto
import com.elenglish.studymentor.data.remote.dto.RegisterRequestDto
import com.elenglish.studymentor.data.remote.dto.SessionDto
import com.elenglish.studymentor.domain.model.AuthUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Outcome of a refresh attempt. */
sealed interface RefreshOutcome {
    data class Success(val accessToken: String) : RefreshOutcome

    /** The session is gone for good; the caller must sign the user out. */
    data object SessionEnded : RefreshOutcome

    /** Transient failure (offline, 5xx). The existing session may still be valid. */
    data class Transient(val error: ApiError) : RefreshOutcome
}

/**
 * Owns the device session: sign-in, sign-out, startup restoration and refresh.
 *
 * `AuthApi` is injected through a [Provider] because the OkHttp authenticator
 * that performs refresh is itself part of the Retrofit client — taking the API
 * lazily breaks that dependency cycle.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val authApiProvider: Provider<AuthApi>,
    private val accessTokenHolder: AccessTokenHolder,
    private val refreshTokenStorage: RefreshTokenStorage,
    private val sessionStateHolder: SessionStateHolder,
    private val json: Json,
    private val sessionScopedStores: Set<@JvmSuppressWildcards SessionScopedStore>,
) {

    private val authApi: AuthApi get() = authApiProvider.get()

    /**
     * Serialises refresh attempts. Without it, several 401s arriving together
     * would each spend the stored refresh token, and rotation would make all but
     * the first fail — the backend treats a replayed token as
     * `auth.refresh_token_reused` and kills the whole session family.
     */
    private val refreshMutex = Mutex()

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
    ): ApiResult<AuthUser> = openSession {
        authApi.register(RegisterRequestDto(displayName, email, password))
    }

    suspend fun login(email: String, password: String): ApiResult<AuthUser> = openSession {
        authApi.login(LoginRequestDto(email, password))
    }

    /**
     * Runs a credential exchange and, only on success, adopts the returned
     * session. The authenticated state is derived from the user the backend
     * returned — never assumed by the client.
     */
    private suspend fun openSession(
        call: suspend () -> retrofit2.Response<com.elenglish.studymentor.core.network.ApiEnvelope<SessionDto>>,
    ): ApiResult<AuthUser> {
        val result = safeApiCall(json = json, block = call, transform = { it })

        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val session = result.value
                storeSession(session.accessToken, session.refreshToken)
                val user = AuthUser(
                    id = session.user.id,
                    displayName = session.user.displayName,
                    email = session.user.email,
                    createdAt = session.user.createdAt,
                )
                sessionStateHolder.markAuthenticated(user.id)
                ApiResult.Success(user, result.requestId)
            }
        }
    }

    /**
     * Restores a session at startup.
     *
     * A stored refresh token is only evidence that a session *may* exist. The
     * backend is asked to confirm it; the client never assumes authorisation.
     */
    suspend fun restoreSession(): RefreshOutcome {
        if (refreshTokenStorage.read() == null) {
            endSessionLocally()
            return RefreshOutcome.SessionEnded
        }

        return when (val outcome = refreshAccessToken()) {
            is RefreshOutcome.Success -> {
                when (val me = currentUser()) {
                    is ApiResult.Success -> {
                        sessionStateHolder.markAuthenticated(me.value.id)
                        outcome
                    }
                    is ApiResult.Failure -> {
                        endSessionLocally()
                        RefreshOutcome.SessionEnded
                    }
                }
            }
            is RefreshOutcome.Transient -> {
                // The backend could not be reached, so the session is
                // unconfirmed rather than ended. Resolve to unauthenticated so
                // the app leaves its startup state instead of showing a spinner
                // for ever; the refresh token is deliberately kept, so a later
                // launch with connectivity can still restore the session.
                sessionStateHolder.markNotAuthenticated()
                outcome
            }

            RefreshOutcome.SessionEnded -> outcome
        }
    }

    suspend fun currentUser(): ApiResult<AuthUser> = safeApiCall(
        json = json,
        block = { authApi.me() },
        transform = { dto ->
            AuthUser(
                id = dto.id,
                displayName = dto.displayName,
                email = dto.email,
                createdAt = dto.createdAt,
            )
        },
    )

    /**
     * Exchanges the stored refresh token for a new access token, at most once at
     * a time.
     *
     * If another caller refreshed while this one waited for the lock, the fresh
     * access token is returned instead of spending the rotated refresh token a
     * second time.
     */
    suspend fun refreshAccessToken(tokenSeenBeforeRefresh: String? = null): RefreshOutcome =
        refreshMutex.withLock {
            val currentAccessToken = accessTokenHolder.get()
            if (tokenSeenBeforeRefresh != null &&
                currentAccessToken != null &&
                currentAccessToken != tokenSeenBeforeRefresh
            ) {
                return@withLock RefreshOutcome.Success(currentAccessToken)
            }

            val storedRefreshToken = refreshTokenStorage.read()
                ?: run {
                    endSessionLocally()
                    return@withLock RefreshOutcome.SessionEnded
                }

            val result = safeApiCall(
                json = json,
                block = { authApi.refresh(AndroidRefreshRequestDto(storedRefreshToken)) },
                transform = { it },
            )

            when (result) {
                is ApiResult.Success -> {
                    storeSession(result.value.accessToken, result.value.refreshToken)
                    RefreshOutcome.Success(result.value.accessToken)
                }
                is ApiResult.Failure -> when (val error = result.error) {
                    is ApiError.Backend -> if (error.isUnrecoverableSessionError()) {
                        endSessionLocally()
                        RefreshOutcome.SessionEnded
                    } else {
                        RefreshOutcome.Transient(error)
                    }
                    else -> RefreshOutcome.Transient(error)
                }
            }
        }

    /** Revokes this device's session family, then clears local state regardless. */
    suspend fun logout(): ApiResult<Unit> =
        safeEmptyApiCall(json) { authApi.logout() }.also { endSessionLocally() }

    /** Revokes every session family for the user, then clears local state. */
    suspend fun logoutAll(): ApiResult<Unit> =
        safeEmptyApiCall(json) { authApi.logoutAll() }.also { endSessionLocally() }

    /**
     * Drops all local session state.
     *
     * Called on sign-out and whenever the backend says the session is finished.
     * Clearing local tokens must never depend on the network call succeeding —
     * otherwise an offline sign-out would leave a usable refresh token behind.
     */
    suspend fun endSessionLocally() {
        accessTokenHolder.clear()
        refreshTokenStorage.clear()
        // Wipe every user-scoped local store, so a cached catalog can never be
        // shown to whoever signs in next on this device.
        sessionScopedStores.forEach { it.clearForSignOut() }
        sessionStateHolder.markNotAuthenticated()
    }

    private fun storeSession(accessToken: String, refreshToken: String?) {
        accessTokenHolder.set(accessToken)
        // The contract marks the refresh token optional; the backend returns it
        // for android platform requests. Absence means "keep the current one".
        if (refreshToken != null) refreshTokenStorage.write(refreshToken)
    }
}

private fun ApiError.Backend.isUnrecoverableSessionError(): Boolean =
    code in ApiErrorCodes.UNRECOVERABLE_SESSION_CODES ||
        // A refresh call rejected as expired cannot be retried into success.
        (code == ApiErrorCodes.AUTH_SESSION_EXPIRED && httpStatus == HTTP_UNAUTHORIZED)

private const val HTTP_UNAUTHORIZED = 401
