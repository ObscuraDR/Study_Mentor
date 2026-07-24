package com.elenglish.studymentor.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The reminder fires at a local wall-clock time, so the delay is computed
 * against the device's zone rather than a fixed offset.
 */
class ReminderSchedulingTest {

    private val london = ZoneId.of("Europe/London")
    private val hoChiMinh = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `a later time today is scheduled for today`() {
        val now = ZonedDateTime.of(2026, 7, 23, 9, 0, 0, 0, london)

        val delay = initialDelayUntil(LocalTime.of(20, 0), london, now)

        assertEquals(Duration.ofHours(11), delay)
    }

    @Test
    fun `a time already past today is scheduled for tomorrow`() {
        val now = ZonedDateTime.of(2026, 7, 23, 21, 0, 0, 0, london)

        val delay = initialDelayUntil(LocalTime.of(20, 0), london, now)

        assertEquals(Duration.ofHours(23), delay)
    }

    @Test
    fun `the delay is never negative`() {
        val now = ZonedDateTime.of(2026, 7, 23, 20, 0, 0, 0, london)

        val delay = initialDelayUntil(LocalTime.of(20, 0), london, now)

        // Exactly on the boundary counts as passed, so it waits a full day.
        assertTrue(!delay.isNegative)
        assertEquals(Duration.ofHours(24), delay)
    }

    @Test
    fun `the same wall-clock time in another zone yields its own local delay`() {
        val londonNow = ZonedDateTime.of(2026, 7, 23, 9, 0, 0, 0, london)
        val saigonNow = ZonedDateTime.of(2026, 7, 23, 9, 0, 0, 0, hoChiMinh)

        val londonDelay = initialDelayUntil(LocalTime.of(20, 0), london, londonNow)
        val saigonDelay = initialDelayUntil(LocalTime.of(20, 0), hoChiMinh, saigonNow)

        // 20:00 means 20:00 where the learner is, not a fixed UTC instant.
        assertEquals(londonDelay, saigonDelay)
    }

    @Test
    fun `a spring-forward day still lands on the chosen local time`() {
        // On 2026-03-29 London jumps 01:00 to 02:00. A 09:00 reminder set at
        // 23:00 the night before must still be 09:00 local, which is 9 hours
        // later rather than 10.
        val now = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, london)

        val delay = initialDelayUntil(LocalTime.of(9, 0), london, now)

        val fireTime = now.plus(delay).withZoneSameInstant(london)
        assertEquals(9, fireTime.hour)
        assertEquals(0, fireTime.minute)
    }

    @Test
    fun `minutes are honoured, not rounded to the hour`() {
        val now = ZonedDateTime.of(2026, 7, 23, 9, 0, 0, 0, london)

        val delay = initialDelayUntil(LocalTime.of(9, 30), london, now)

        assertEquals(Duration.ofMinutes(30), delay)
    }
}
