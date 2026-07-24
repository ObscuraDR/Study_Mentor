package com.elenglish.studymentor.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Runs the reminder worker for real and inspects what it posts.
 */
@RunWith(RobolectricTestRunner::class)
class StudyReminderWorkerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun grantNotificationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<StudyReminderWorker>(context).build().doWork()
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Test
    fun `posts a reminder notification on its own channel`() {
        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)

        val posted = shadowOf(notificationManager()).allNotifications
        assertEquals(1, posted.size)
        assertEquals(StudyReminderWorker.CHANNEL_ID, posted.single().channelId)
    }

    @Test
    fun `the notification carries the reminder wording and no learning data`() {
        runWorker()

        val extras = shadowOf(notificationManager()).allNotifications.single().extras
        val title = extras.getCharSequence("android.title").toString()
        val text = extras.getCharSequence("android.text").toString()

        assertEquals("Time to study", title)
        assertEquals("Continue where you left off.", text)

        // A local reminder must not state XP, streaks or progress: none of that
        // is the device's to claim, and it would be stale by definition.
        listOf("XP", "streak", "%", "level").forEach { forbidden ->
            assertTrue(
                "reminder must not mention $forbidden",
                !title.contains(forbidden, ignoreCase = true) &&
                    !text.contains(forbidden, ignoreCase = true),
            )
        }
    }

    @Test
    fun `the notification channel is created before posting`() {
        runWorker()

        val channel = notificationManager().getNotificationChannel(StudyReminderWorker.CHANNEL_ID)
        assertNotNull("the reminder channel must exist", channel)
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            channel.importance,
        )
    }

    @Test
    fun `without permission it posts nothing and does not fail`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val result = runWorker()

        // Posting without permission would throw, and retrying could never
        // succeed, so the worker succeeds quietly instead of failing forever.
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(shadowOf(notificationManager()).allNotifications.isEmpty())
    }

    @Test
    fun `running twice replaces the reminder rather than stacking copies`() {
        runWorker()
        runWorker()

        // A fixed notification id means yesterday's reminder is replaced, not
        // piled up alongside today's.
        assertEquals(1, shadowOf(notificationManager()).allNotifications.size)
    }
}
