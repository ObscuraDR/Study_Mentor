package com.elenglish.studymentor.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.elenglish.studymentor.core.session.SessionScopedStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels the daily study reminder.
 *
 * The reminder fires at a **local** wall-clock time. The initial delay is
 * computed against the device's current zone rather than a fixed UTC offset, so
 * "20:00" stays 20:00 after travelling or a daylight-saving change.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionScopedStore {

    fun schedule(time: LocalTime, zone: ZoneId = ZoneId.systemDefault()) {
        val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(
            repeatInterval = Duration.ofDays(1),
        )
            .setInitialDelay(initialDelayUntil(time, zone))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StudyReminderWorker.WORK_NAME,
            // Replace so a changed time takes effect instead of waiting out the
            // previously scheduled run.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(StudyReminderWorker.WORK_NAME)
    }

    /** Reminders are a signed-in feature, so signing out cancels them. */
    override suspend fun clearForSignOut() = cancel()
}

/**
 * Time until the next occurrence of [time] in [zone].
 *
 * If today's slot has already passed, the next one is tomorrow.
 */
internal fun initialDelayUntil(
    time: LocalTime,
    zone: ZoneId,
    now: ZonedDateTime = ZonedDateTime.now(zone),
): Duration {
    val todayAtTime = now.with(time)
    val next = if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
    return Duration.between(now, next)
}
