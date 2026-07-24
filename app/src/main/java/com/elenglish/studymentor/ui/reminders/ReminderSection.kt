package com.elenglish.studymentor.ui.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elenglish.studymentor.R
import com.elenglish.studymentor.notifications.StudyReminderWorker
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorCard
import com.elenglish.studymentor.ui.components.StudyMentorDialog
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import java.util.Locale

object ReminderTestTags {
    const val SECTION = "reminder_section"
    const val SWITCH = "reminder_switch"
    const val TIME = "reminder_time"
    const val PERMISSION_NOTE = "reminder_permission_note"
}

/**
 * Daily reminder settings.
 *
 * The notification permission is requested at the moment the user turns
 * reminders on, so the system prompt arrives with its reason on screen rather
 * than as an unexplained dialog at launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSection(
    modifier: Modifier = Modifier,
    viewModel: ReminderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showTimePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    // Permission can be changed in system settings while the app is backgrounded,
    // so it is re-checked every time the screen resumes.
    DisposableLifecycleEffect(lifecycleOwner) {
        viewModel.onPermissionStateChanged(StudyReminderWorker.hasPostPermission(context))
    }

    LaunchedEffect(state.awaitingPermission) {
        if (!state.awaitingPermission) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Below Android 13 there is no runtime permission; the app-level
            // notification toggle decides.
            viewModel.onPermissionResult(StudyReminderWorker.hasPostPermission(context))
        }
    }

    StudyMentorCard(modifier = modifier.testTag(ReminderTestTags.SECTION)) {
        Text(
            text = stringResource(R.string.reminder_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reminder_daily_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.reminder_daily_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.preference.enabled,
                onCheckedChange = viewModel::onEnabledChange,
                modifier = Modifier
                    .testTag(ReminderTestTags.SWITCH)
                    .semantics {
                        stateDescription = if (state.preference.enabled) "On" else "Off"
                    },
            )
        }

        StudyMentorButton(
            text = stringResource(
                R.string.reminder_time_at,
                formatTime(state.preference.hour, state.preference.minute),
            ),
            onClick = { showTimePicker = true },
            variant = ButtonVariant.Secondary,
            enabled = state.preference.enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReminderTestTags.TIME),
        )

        if (!state.permissionGranted) {
            Text(
                text = stringResource(R.string.reminder_permission_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ReminderTestTags.PERMISSION_NOTE),
            )
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.preference.hour,
            initialMinute = state.preference.minute,
            is24Hour = true,
        )
        StudyMentorDialog(
            title = stringResource(R.string.reminder_pick_time),
            text = "",
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                showTimePicker = false
                viewModel.onTimeChange(pickerState.hour, pickerState.minute)
            },
            onDismissRequest = { showTimePicker = false },
            dismissText = stringResource(R.string.action_cancel),
            content = { TimePicker(state = pickerState) },
        )
    }
}

/** Runs [onResume] now and on every subsequent resume. */
@Composable
private fun DisposableLifecycleEffect(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onResume: () -> Unit,
) {
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

internal fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", hour, minute)
