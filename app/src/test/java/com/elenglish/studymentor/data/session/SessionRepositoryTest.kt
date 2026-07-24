package com.elenglish.studymentor.data.session

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.session.SessionState
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    private var harness: ApiTestHarness? = null

    private fun harness(refreshToken: String? = null): ApiTestHarness =
        ApiTestHarness(refreshToken).also { harness = it }

    @After
    fun tearDown() {
        harness?.shutdown()
    }

    @Test
    fun `login stores both tokens and derives the authenticated state from the response`() =
        runTest {
            val h = harness()
            h.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.session()))

            val result = h.sessionRepository.login("mai@example.com", "correct-horse")

            assertTrue(result is ApiResult.Success)
            assertEquals(Fixtures.USER_ID, (result as ApiResult.Success).value.id)
            assertEquals("access-1", h.accessTokenHolder.get())
            assertEquals("refresh-1", h.refreshTokenStorage.read())
            assertEquals(
                SessionState.Authenticated(Fixtures.USER_ID),
                h.sessionStateHolder.state.value,
            )
        }

    @Test
    fun `login retains the request id for support correlation`() = runTest {
        val h = harness()
        h.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.session()))

        val result = h.sessionRepository.login("mai@example.com", "correct-horse")

        assertEquals(Fixtures.REQUEST_ID, (result as ApiResult.Success).requestId)
    }

    @Test
    fun `failed login stores no token and leaves the client unauthenticated`() = runTest {
        val h = harness()
        h.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_INVALID_CREDENTIALS)),
        )

        val result = h.sessionRepository.login("mai@example.com", "wrong")

        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(ApiErrorCodes.AUTH_INVALID_CREDENTIALS, error.code)
        assertEquals(Fixtures.REQUEST_ID, error.requestId)
        assertNull(h.accessTokenHolder.get())
        assertNull(h.refreshTokenStorage.read())
        assertEquals(SessionState.Unknown, h.sessionStateHolder.state.value)
    }

    @Test
    fun `register opens a session exactly like login`() = runTest {
        val h = harness()
        h.server.enqueue(MockResponse().setResponseCode(201).setBody(Fixtures.session()))

        val result = h.sessionRepository.register("Mai", "mai@example.com", "correct-horse")

        assertTrue(result is ApiResult.Success)
        assertEquals("refresh-1", h.refreshTokenStorage.read())
    }

    @Test
    fun `startup restoration with no stored token ends the session without a network call`() =
        runTest {
            val h = harness(refreshToken = null)

            val outcome = h.sessionRepository.restoreSession()

            assertEquals(RefreshOutcome.SessionEnded, outcome)
            assertEquals(0, h.server.requestCount)
            assertEquals(SessionState.NotAuthenticated, h.sessionStateHolder.state.value)
        }

    @Test
    fun `startup restoration confirms the session with the backend before trusting it`() =
        runTest {
            val h = harness(refreshToken = "refresh-1")
            h.server.enqueue(
                MockResponse().setResponseCode(200).setBody(Fixtures.refreshedSession()),
            )
            h.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.user()))

            val outcome = h.sessionRepository.restoreSession()

            assertTrue(outcome is RefreshOutcome.Success)
            // Refresh, then /auth/me. Authorisation is never assumed from storage.
            assertEquals(2, h.server.requestCount)
            assertEquals(
                SessionState.Authenticated(Fixtures.USER_ID),
                h.sessionStateHolder.state.value,
            )
        }

    @Test
    fun `startup restoration with an invalid stored token signs the user out`() = runTest {
        val h = harness(refreshToken = "stale")
        h.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_REFRESH_TOKEN_INVALID)),
        )

        val outcome = h.sessionRepository.restoreSession()

        assertEquals(RefreshOutcome.SessionEnded, outcome)
        assertNull(h.refreshTokenStorage.read())
        assertEquals(SessionState.NotAuthenticated, h.sessionStateHolder.state.value)
    }

    @Test
    fun `startup restoration resolves even when the backend is unreachable`() = runTest {
        val h = harness(refreshToken = "refresh-1")
        h.goOffline()

        val outcome = h.sessionRepository.restoreSession()

        // The session is unconfirmed, not ended. The app must still leave its
        // startup state, or it shows a loading spinner for ever.
        assertTrue(outcome is RefreshOutcome.Transient)
        assertEquals(SessionState.NotAuthenticated, h.sessionStateHolder.state.value)
        // The token is kept so a later launch with connectivity can restore.
        assertEquals("refresh-1", h.refreshTokenStorage.read())
    }

    @Test
    fun `refresh rotation replaces the stored refresh token`() = runTest {
        val h = harness(refreshToken = "refresh-1")
        h.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.refreshedSession("access-2", "refresh-2")),
        )

        h.sessionRepository.refreshAccessToken()

        assertEquals("access-2", h.accessTokenHolder.get())
        assertEquals("refresh-2", h.refreshTokenStorage.read())
    }

    @Test
    fun `a refresh response without a new refresh token keeps the current one`() = runTest {
        val h = harness(refreshToken = "refresh-1")
        h.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.refreshedSession("access-2", refreshToken = null)),
        )

        h.sessionRepository.refreshAccessToken()

        assertEquals("access-2", h.accessTokenHolder.get())
        assertEquals("refresh-1", h.refreshTokenStorage.read())
    }

    @Test
    fun `logout clears local session state`() = runTest {
        val h = harness(refreshToken = "refresh-1")
        h.accessTokenHolder.set("access-1")
        h.server.enqueue(MockResponse().setResponseCode(204))

        h.sessionRepository.logout()

        assertNull(h.accessTokenHolder.get())
        assertNull(h.refreshTokenStorage.read())
        assertEquals(SessionState.NotAuthenticated, h.sessionStateHolder.state.value)
    }

    @Test
    fun `logout clears local tokens even when the network call fails`() = runTest {
        val h = harness(refreshToken = "refresh-1")
        h.accessTokenHolder.set("access-1")
        h.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )

        h.sessionRepository.logout()

        // Otherwise an offline sign-out would leave a usable refresh token on the
        // device while the user believes they signed out.
        assertNull(h.accessTokenHolder.get())
        assertNull(h.refreshTokenStorage.read())
        assertEquals(SessionState.NotAuthenticated, h.sessionStateHolder.state.value)
    }

    @Test
    fun `logout-all revokes every family and clears local state`() = runTest {
        val h = harness(refreshToken = "refresh-1")
        h.accessTokenHolder.set("access-1")
        h.server.enqueue(MockResponse().setResponseCode(204))

        h.sessionRepository.logoutAll()

        assertTrue(h.server.takeRequest().path!!.endsWith("auth/logout-all"))
        assertNull(h.refreshTokenStorage.read())
        assertEquals(SessionState.NotAuthenticated, h.sessionStateHolder.state.value)
    }
}
