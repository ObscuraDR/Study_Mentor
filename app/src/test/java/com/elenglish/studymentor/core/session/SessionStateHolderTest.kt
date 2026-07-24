package com.elenglish.studymentor.core.session

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionStateHolderTest {

    @Test
    fun `starts unknown so the client never assumes it is authorised`() = runTest {
        val holder = SessionStateHolder()
        assertEquals(SessionState.Unknown, holder.state.value)
    }

    @Test
    fun `emits transitions in order`() = runTest {
        val holder = SessionStateHolder()

        holder.state.test {
            assertEquals(SessionState.Unknown, awaitItem())

            holder.markNotAuthenticated()
            assertEquals(SessionState.NotAuthenticated, awaitItem())

            holder.markAuthenticated("0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f")
            assertEquals(
                SessionState.Authenticated("0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f"),
                awaitItem(),
            )

            holder.markNotAuthenticated()
            assertEquals(SessionState.NotAuthenticated, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejects a blank user id`() {
        val holder = SessionStateHolder()
        assertThrows(IllegalArgumentException::class.java) { holder.markAuthenticated("  ") }
        assertEquals(SessionState.Unknown, holder.state.value)
    }
}
