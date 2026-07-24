package com.elenglish.studymentor.data.session

import com.elenglish.studymentor.data.remote.dto.AndroidRefreshRequestDto
import com.elenglish.studymentor.data.remote.dto.LoginRequestDto
import com.elenglish.studymentor.data.remote.dto.RefreshedSessionDto
import com.elenglish.studymentor.data.remote.dto.RegisterRequestDto
import com.elenglish.studymentor.data.remote.dto.SessionDto
import com.elenglish.studymentor.data.remote.dto.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Secrets must not be reachable through `toString()`.
 *
 * A data class prints every property by default, so a single log, crash report
 * or debug print of a DTO would expose a password or token. These tests fail if
 * that protection is ever removed.
 */
class TokenSecrecyTest {

    private val secret = "super-secret-value"

    @Test
    fun `credential requests never print their secrets`() {
        val register = RegisterRequestDto("Mai", "mai@example.com", secret).toString()
        val login = LoginRequestDto("mai@example.com", secret).toString()
        val refresh = AndroidRefreshRequestDto(secret).toString()

        listOf(register, login, refresh).forEach { rendered ->
            assertFalse("secret leaked in: $rendered", rendered.contains(secret))
        }
        // Email is personal data too and is not printed.
        assertFalse(register.contains("mai@example.com"))
        assertFalse(login.contains("mai@example.com"))
    }

    @Test
    fun `session responses never print their tokens`() {
        val session = SessionDto(
            user = UserDto("id", "Mai", "mai@example.com", "2026-07-20T08:00:00Z"),
            accessToken = secret,
            accessTokenExpiresAt = "2026-07-20T09:00:00Z",
            refreshToken = secret,
        ).toString()

        val refreshed = RefreshedSessionDto(
            accessToken = secret,
            accessTokenExpiresAt = "2026-07-20T09:00:00Z",
            refreshToken = secret,
        ).toString()

        assertFalse("token leaked in: $session", session.contains(secret))
        assertFalse("token leaked in: $refreshed", refreshed.contains(secret))
    }

    @Test
    fun `the access token is held in memory only and can be cleared`() {
        val holder = AccessTokenHolder()

        assertNull(holder.get())

        holder.set("access-1")
        assertEquals("access-1", holder.get())

        holder.clear()
        assertNull(holder.get())
    }

    @Test
    fun `a blank access token is rejected`() {
        val holder = AccessTokenHolder()

        val error = runCatching { holder.set("  ") }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertNull(holder.get())
    }
}
