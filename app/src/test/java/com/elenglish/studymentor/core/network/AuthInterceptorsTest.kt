package com.elenglish.studymentor.core.network

import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end header, refresh and retry behaviour, exercised against a real
 * MockWebServer through the real OkHttp stack.
 */
class AuthInterceptorsTest {

    private lateinit var harness: ApiTestHarness

    @Before
    fun setUp() {
        harness = ApiTestHarness(refreshToken = "refresh-1")
    }

    @After
    fun tearDown() {
        harness.shutdown()
    }

    @Test
    fun `every request carries the android platform header`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.profile()))

        harness.accountApi.getProfile()

        val request = harness.server.takeRequest()
        assertEquals("android", request.getHeader("X-Client-Platform"))
    }

    @Test
    fun `protected requests carry the bearer access token`() = runTest {
        harness.accessTokenHolder.set("access-1")
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.profile()))

        harness.accountApi.getProfile()

        assertEquals("Bearer access-1", harness.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `login never carries an authorization header`() = runTest {
        // A stale token from a previous session must not leak into a sign-in.
        harness.accessTokenHolder.set("stale-token")
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.session()))

        harness.sessionRepository.login("mai@example.com", "correct-horse")

        assertNull(harness.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `refresh never carries an authorization header`() = runTest {
        harness.accessTokenHolder.set("expired-token")
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.refreshedSession()),
        )

        harness.sessionRepository.refreshAccessToken()

        assertNull(harness.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a 401 triggers one refresh and one retry with the new token`() = runTest {
        harness.accessTokenHolder.set("access-1")
        // 1: protected call rejected. 2: refresh succeeds. 3: retry succeeds.
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.refreshedSession("access-2")),
        )
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.profile()))

        val response = harness.accountApi.getProfile()

        assertTrue(response.isSuccessful)
        assertEquals(3, harness.server.requestCount)

        val first = harness.server.takeRequest()
        val refresh = harness.server.takeRequest()
        val retry = harness.server.takeRequest()

        assertEquals("Bearer access-1", first.getHeader("Authorization"))
        assertTrue(refresh.path!!.endsWith("auth/refresh"))
        assertEquals("Bearer access-2", retry.getHeader("Authorization"))
    }

    @Test
    fun `a second 401 after a retry gives up instead of looping`() = runTest {
        harness.accessTokenHolder.set("access-1")
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.refreshedSession("access-2")),
        )
        // The retry is rejected too.
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )

        val response = harness.accountApi.getProfile()

        assertEquals(401, response.code())
        // Original + refresh + one retry, and nothing more.
        assertEquals(3, harness.server.requestCount)
    }

    @Test
    fun `a failing refresh is never itself refreshed`() = runTest {
        harness.accessTokenHolder.set("access-1")
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_REFRESH_TOKEN_INVALID)),
        )

        val outcome = harness.sessionRepository.refreshAccessToken()

        // Exactly one call: the refresh itself. No recursion.
        assertEquals(1, harness.server.requestCount)
        assertTrue(outcome is com.elenglish.studymentor.data.session.RefreshOutcome.SessionEnded)
    }

    @Test
    fun `an unrecoverable refresh failure clears the stored refresh token`() = runTest {
        harness.accessTokenHolder.set("access-1")
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_REFRESH_TOKEN_REUSED)),
        )

        harness.sessionRepository.refreshAccessToken()

        assertNull(harness.refreshTokenStorage.read())
        assertNull(harness.accessTokenHolder.get())
    }

    @Test
    fun `a transient refresh failure keeps the session so it can be retried later`() = runTest {
        harness.accessTokenHolder.set("access-1")
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )

        val outcome = harness.sessionRepository.refreshAccessToken()

        assertTrue(outcome is com.elenglish.studymentor.data.session.RefreshOutcome.Transient)
        // A 5xx says nothing about the token's validity — keep it.
        assertEquals("refresh-1", harness.refreshTokenStorage.read())
    }

    @Test
    fun `concurrent refreshes spend the rotated token only once`() = runTest {
        harness.accessTokenHolder.set("access-1")
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.refreshedSession("access-2")),
        )

        val outcomes = listOf(
            async { harness.sessionRepository.refreshAccessToken("access-1") },
            async { harness.sessionRepository.refreshAccessToken("access-1") },
            async { harness.sessionRepository.refreshAccessToken("access-1") },
        ).awaitAll()

        // One network refresh only; the losers adopt the winner's token. A second
        // call would replay a rotated token and the backend would revoke the
        // whole session family as `auth.refresh_token_reused`.
        assertEquals(1, harness.server.requestCount)
        outcomes.forEach { outcome ->
            assertEquals(
                com.elenglish.studymentor.data.session.RefreshOutcome.Success("access-2"),
                outcome,
            )
        }
        assertEquals(1, harness.refreshTokenStorage.writeCount)
    }
}
