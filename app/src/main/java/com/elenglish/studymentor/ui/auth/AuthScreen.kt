package com.elenglish.studymentor.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorTextField
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

object AuthTestTags {
    const val SCREEN = "auth_screen"
    const val DISPLAY_NAME = "auth_display_name"
    const val EMAIL = "auth_email"
    const val PASSWORD = "auth_password"
    const val SUBMIT = "auth_submit"
    const val MODE_TOGGLE = "auth_mode_toggle"
    const val FAILURE = "auth_failure"
}

/**
 * Sign-in and registration.
 *
 * On success this screen does not navigate itself: the session state changes and
 * the root graph switches to the authenticated shell. That keeps authorisation a
 * consequence of backend state rather than of a UI event.
 */
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreenContent(
        state = state,
        onModeChange = viewModel::setMode,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
internal fun AuthScreenContent(
    state: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRegister = state.mode == AuthMode.Register

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(AuthTestTags.SCREEN)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(
                horizontal = StudyMentorTheme.spacing.lg,
                vertical = StudyMentorTheme.spacing.xl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                if (isRegister) R.string.auth_register_subtitle else R.string.auth_sign_in_subtitle,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = StudyMentorTheme.spacing.md),
        )

        if (isRegister) {
            StudyMentorTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                label = stringResource(R.string.auth_field_name),
                errorText = state.displayNameError,
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AuthTestTags.DISPLAY_NAME),
            )
        }

        StudyMentorTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_field_email),
            errorText = state.emailError,
            enabled = !state.submitting,
            keyboardType = KeyboardType.Email,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthTestTags.EMAIL),
        )

        StudyMentorTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.auth_field_password),
            errorText = state.passwordError,
            helperText = if (isRegister) stringResource(R.string.auth_password_hint) else null,
            enabled = !state.submitting,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthTestTags.PASSWORD),
        )

        if (state.failure != null) {
            AuthFailureMessage(state.failure)
        }

        StudyMentorButton(
            text = stringResource(
                if (isRegister) R.string.auth_action_register else R.string.auth_action_sign_in,
            ),
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthTestTags.SUBMIT),
        )

        StudyMentorButton(
            text = stringResource(
                if (isRegister) R.string.auth_switch_to_sign_in else R.string.auth_switch_to_register,
            ),
            onClick = {
                onModeChange(if (isRegister) AuthMode.SignIn else AuthMode.Register)
            },
            variant = ButtonVariant.Text,
            enabled = !state.submitting,
            modifier = Modifier.testTag(AuthTestTags.MODE_TOGGLE),
        )
    }
}

@Composable
private fun AuthFailureMessage(failure: AuthFailure) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AuthTestTags.FAILURE)
            // Announced as soon as it appears, so the reason for a failed submit
            // is not visual-only.
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Text(
            text = failure.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (failure.requestId != null) {
            Text(
                text = stringResource(R.string.error_reference, failure.requestId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, name = "Sign in")
@Composable
private fun AuthScreenSignInPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        AuthScreenContent(
            state = AuthUiState(email = "learner@example.com"),
            onModeChange = {}, onDisplayNameChange = {}, onEmailChange = {},
            onPasswordChange = {}, onSubmit = {},
        )
    }
}

@Preview(showBackground = true, name = "Register with error")
@Composable
private fun AuthScreenRegisterErrorPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) {
        AuthScreenContent(
            state = AuthUiState(
                mode = AuthMode.Register,
                displayName = "Mai",
                email = "mai@example.com",
                failure = AuthFailure(
                    message = "That email is already registered.",
                    requestId = "0191f3a0-7d5c-7b3a-9f11-000000000001",
                ),
            ),
            onModeChange = {}, onDisplayNameChange = {}, onEmailChange = {},
            onPasswordChange = {}, onSubmit = {},
        )
    }
}
