package com.elenglish.studymentor.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.main.MainActivity

/**
 * Posts the daily study reminder.
 *
 * The reminder is a purely local nudge. It carries no learning data — no XP, no
 * streak, no progress — because none of that is the device's to state.
 */
class StudyReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // The user can revoke notification permission at any time; posting
        // without it would throw and is not something to retry.
        if (!hasPostPermission(context)) return Result.success()

        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            PENDING_INTENT_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the post.
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "study_mentor_daily_reminder"
        const val CHANNEL_ID = "study_reminders"
        private const val NOTIFICATION_ID = 1001
        private const val PENDING_INTENT_REQUEST_CODE = 0

        fun hasPostPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Before Android 13 there is no runtime permission to hold.
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

        /** Channels exist on every supported version, since minSdk is 26. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.reminder_channel_description)
                },
            )
        }
    }
}
