package com.elenglish.studymentor.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.AppLocale
import com.elenglish.studymentor.domain.model.EducationLevel
import com.elenglish.studymentor.domain.model.SharedSettings
import com.elenglish.studymentor.domain.model.UserProfile
import com.elenglish.studymentor.ui.reminders.ReminderSection
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.ErrorState
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorCard
import com.elenglish.studymentor.ui.components.StudyMentorDialog
import com.elenglish.studymentor.ui.components.StudyMentorFilterChip
import com.elenglish.studymentor.ui.components.StudyMentorTextField
import com.elenglish.studymentor.ui.theme.LocalFeedbackColors
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

object ProfileTestTags {
    const val SCREEN = "profile_screen"
    const val DISPLAY_NAME = "profile_display_name"
    const val SAVE_PROFILE = "profile_save"
    const val DAILY_GOAL = "settings_daily_goal"
    const val SAVE_SETTINGS = "settings_save"
    const val MESSAGE = "profile_message"
    const val SIGN_OUT = "profile_sign_out"
    const val SIGN_OUT_ALL = "profile_sign_out_all"
}

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreenContent(
        state = state,
        onReload = viewModel::load,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onEducationLevelChange = viewModel::onEducationLevelChange,
        onLocaleChange = viewModel::onLocaleChange,
        onDailyGoalChange = viewModel::onDailyGoalChange,
        onSaveProfile = viewModel::saveProfile,
        onSaveSettings = viewModel::saveSettings,
        onSignOut = viewModel::signOut,
        onSignOutEverywhere = viewModel::signOutEverywhere,
        modifier = modifier,
    )
}

@Composable
internal fun ProfileScreenContent(
    state: ProfileUiState,
    onReload: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onEducationLevelChange: (EducationLevel?) -> Unit,
    onLocaleChange: (AppLocale) -> Unit,
    onDailyGoalChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onSaveSettings: () -> Unit,
    onSignOut: () -> Unit,
    onSignOutEverywhere: () -> Unit,
    modifier: Modifier = Modifier,
    reminderContent: @Composable () -> Unit = { ReminderSection() },
) {
    when (val loadState = state.loadState) {
        ProfileLoadState.Loading -> LoadingState(modifier = modifier)

        is ProfileLoadState.Failed -> ErrorState(
            title = stringResource(loadState.kind.titleRes()),
            description = stringResource(loadState.kind.bodyRes()),
            requestId = loadState.requestId,
            onRetry = onReload,
            modifier = modifier,
        )

        ProfileLoadState.Loaded -> LoadedProfile(
            state = state,
            onReload = onReload,
            onDisplayNameChange = onDisplayNameChange,
            onEducationLevelChange = onEducationLevelChange,
            onLocaleChange = onLocaleChange,
            onDailyGoalChange = onDailyGoalChange,
            onSaveProfile = onSaveProfile,
            onSaveSettings = onSaveSettings,
            onSignOut = onSignOut,
            onSignOutEverywhere = onSignOutEverywhere,
            reminderContent = reminderContent,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadedProfile(
    state: ProfileUiState,
    onReload: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onEducationLevelChange: (EducationLevel?) -> Unit,
    onLocaleChange: (AppLocale) -> Unit,
    onDailyGoalChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onSaveSettings: () -> Unit,
    onSignOut: () -> Unit,
    onSignOutEverywhere: () -> Unit,
    reminderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingSignOut by remember { mutableStateOf<SignOutScope?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProfileTestTags.SCREEN)
            .verticalScroll(rememberScrollState())
            .padding(StudyMentorTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md),
    ) {
        if (state.message != null) {
            ProfileMessageBanner(state.message, onReload)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = StudyMentorTheme.elevation.md),
        ) {
            Column(
                modifier = Modifier.padding(StudyMentorTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )

                StudyMentorTextField(
                    value = state.displayNameInput,
                    onValueChange = onDisplayNameChange,
                    label = stringResource(R.string.profile_field_display_name),
                    enabled = !state.savingProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileTestTags.DISPLAY_NAME),
                )

                StudyMentorTextField(
                    value = state.profile?.email.orEmpty(),
                    onValueChange = { },
                    label = stringResource(R.string.profile_field_email),
                    helperText = stringResource(R.string.profile_email_readonly),
                    // Email is read-only in the contract.
                    enabled = false,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.profile_field_education_level),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = StudyMentorTheme.spacing.xs),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
                ) {
                    EducationLevel.entries.forEach { level ->
                        StudyMentorFilterChip(
                            text = stringResource(level.labelRes()),
                            selected = state.educationLevelInput == level,
                            // Tapping the selected chip clears the value, which the
                            // contract models as an explicit null.
                            onClick = {
                                onEducationLevelChange(
                                    if (state.educationLevelInput == level) null else level,
                                )
                            },
                            enabled = !state.savingProfile,
                        )
                    }
                }

                StudyMentorButton(
                    text = stringResource(R.string.profile_action_save),
                    onClick = onSaveProfile,
                    enabled = state.canSaveProfile,
                    loading = state.savingProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileTestTags.SAVE_PROFILE),
                )
            }
        }

        StudyMentorCard {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )

            Text(
                text = stringResource(R.string.settings_field_locale),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
            ) {
                AppLocale.entries.forEach { locale ->
                    StudyMentorFilterChip(
                        text = stringResource(locale.labelRes()),
                        selected = state.localeInput == locale,
                        onClick = { onLocaleChange(locale) },
                        enabled = !state.savingSettings,
                    )
                }
            }

            StudyMentorTextField(
                value = state.dailyGoalInput,
                onValueChange = onDailyGoalChange,
                label = stringResource(R.string.settings_field_daily_goal),
                errorText = if (state.settingsDirty) state.dailyGoalError else null,
                enabled = !state.savingSettings,
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTestTags.DAILY_GOAL),
            )

            StudyMentorButton(
                text = stringResource(R.string.profile_action_save),
                onClick = onSaveSettings,
                enabled = state.canSaveSettings,
                loading = state.savingSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTestTags.SAVE_SETTINGS),
            )
        }

        reminderContent()

        StudyMentorButton(
            text = stringResource(R.string.action_sign_out),
            onClick = { pendingSignOut = SignOutScope.ThisDevice },
            variant = ButtonVariant.Secondary,
            enabled = !state.signingOut,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileTestTags.SIGN_OUT),
        )
        StudyMentorButton(
            text = stringResource(R.string.action_sign_out_all),
            onClick = { pendingSignOut = SignOutScope.AllDevices },
            variant = ButtonVariant.Text,
            enabled = !state.signingOut,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileTestTags.SIGN_OUT_ALL),
        )
    }

    pendingSignOut?.let { scope ->
        StudyMentorDialog(
            title = stringResource(
                if (scope == SignOutScope.AllDevices) {
                    R.string.sign_out_all_confirm_title
                } else {
                    R.string.sign_out_confirm_title
                },
            ),
            text = stringResource(
                if (scope == SignOutScope.AllDevices) {
                    R.string.sign_out_all_confirm_body
                } else {
                    R.string.sign_out_confirm_body
                },
            ),
            confirmText = stringResource(R.string.action_sign_out),
            onConfirm = {
                pendingSignOut = null
                if (scope == SignOutScope.AllDevices) onSignOutEverywhere() else onSignOut()
            },
            onDismissRequest = { pendingSignOut = null },
            dismissText = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

private enum class SignOutScope { ThisDevice, AllDevices }

/**
 * A save/load outcome banner.
 *
 * An error (including a revision conflict) gets the error-container tint and
 * a warning icon so it reads as distinctly different from a plain success
 * confirmation — not just by its wording — while still reusing the same
 * `requestId`/reload affordances as before.
 */
@Composable
private fun ProfileMessageBanner(message: ProfileMessage, onReload: () -> Unit) {
    val containerColor: Color
    val contentColor: Color
    if (message.isError) {
        containerColor = MaterialTheme.colorScheme.errorContainer
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    } else {
        containerColor = LocalFeedbackColors.current.successContainer
        contentColor = LocalFeedbackColors.current.success
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProfileTestTags.MESSAGE)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(StudyMentorTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = if (message.isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(MESSAGE_ICON_SIZE_DP.dp),
                )
                Text(
                    text = stringResource(message.kind.bodyRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            if (message.requestId != null) {
                Text(
                    text = stringResource(R.string.error_reference, message.requestId),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
            if (message.requiresReload) {
                StudyMentorButton(
                    text = stringResource(R.string.action_reload),
                    onClick = onReload,
                    variant = ButtonVariant.Secondary,
                )
            }
        }
    }
}

private const val MESSAGE_ICON_SIZE_DP = 20

private fun EducationLevel.labelRes(): Int = when (this) {
    EducationLevel.Beginner -> R.string.profile_education_beginner
    EducationLevel.Intermediate -> R.string.profile_education_intermediate
    EducationLevel.Advanced -> R.string.profile_education_advanced
}

private fun AppLocale.labelRes(): Int = when (this) {
    AppLocale.English -> R.string.settings_locale_en
    AppLocale.Vietnamese -> R.string.settings_locale_vi
}

private fun ProfileMessageKind.titleRes(): Int = when (this) {
    ProfileMessageKind.NetworkUnavailable -> R.string.error_network_title
    else -> R.string.error_generic_title
}

private fun ProfileMessageKind.bodyRes(): Int = when (this) {
    ProfileMessageKind.ProfileSaved -> R.string.profile_saved
    ProfileMessageKind.SettingsSaved -> R.string.settings_saved
    ProfileMessageKind.NetworkUnavailable -> R.string.error_network_body
    ProfileMessageKind.RevisionConflict -> R.string.conflict_reload_required
    ProfileMessageKind.Generic -> R.string.error_generic_body
}

@Preview(showBackground = true, name = "Profile loaded")
@Composable
private fun ProfileScreenPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        ProfileScreenContent(
            state = ProfileUiState(
                loadState = ProfileLoadState.Loaded,
                profile = UserProfile(
                    id = "0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f",
                    displayName = "Mai",
                    email = "mai@example.com",
                    avatarKey = null,
                    educationLevel = EducationLevel.Intermediate,
                    updatedAt = "2026-07-20T08:00:00Z",
                    revision = "rev-1",
                ),
                settings = SharedSettings(
                    locale = AppLocale.English,
                    dailyGoalTargetXp = 50,
                    updatedAt = "2026-07-20T08:00:00Z",
                    revision = "rev-1",
                ),
                displayNameInput = "Mai",
                educationLevelInput = EducationLevel.Intermediate,
                localeInput = AppLocale.English,
                dailyGoalInput = "50",
            ),
            onReload = {}, onDisplayNameChange = {}, onEducationLevelChange = {},
            onLocaleChange = {}, onDailyGoalChange = {}, onSaveProfile = {},
            onSaveSettings = {}, onSignOut = {}, onSignOutEverywhere = {},
        )
    }
}
