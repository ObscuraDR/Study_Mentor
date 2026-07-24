package com.elenglish.studymentor.di

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Security regression guard.
 *
 * `BODY` and `HEADERS` logging would write access tokens, refresh tokens and
 * `Authorization` headers into logcat. Neither level is acceptable in any build.
 */
class NetworkLoggingPolicyTest {

    @Test
    fun `never logs request or response bodies or headers`() {
        val level = NetworkModule.provideLoggingInterceptor().level

        assertNotEquals(HttpLoggingInterceptor.Level.BODY, level)
        assertNotEquals(HttpLoggingInterceptor.Level.HEADERS, level)
    }
}
