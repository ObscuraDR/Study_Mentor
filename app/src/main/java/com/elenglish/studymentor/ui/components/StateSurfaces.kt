package com.elenglish.studymentor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.theme.StudyMentorTheme

/** Shared test tags for the state surfaces. */
object StateSurfaceTestTags {
    const val LOADING = "state_loading"
    const val EMPTY = "state_empty"
    const val ERROR = "state_error"
}

/**
 * Full-surface busy state.
 *
 * The indicator carries a content description so TalkBack announces that work is
 * in progress rather than reporting an empty screen.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.state_loading),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(StateSurfaceTestTags.LOADING)
            .padding(StudyMentorTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(INDICATOR_SIZE_DP.dp)
                .semantics { contentDescription = message },
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = StudyMentorTheme.spacing.md),
        )
    }
}

/**
 * "Nothing here" state. Distinct from [ErrorState]: an empty result is a valid
 * backend answer, not a failure, and must never be presented as one.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateScaffold(
        testTag = StateSurfaceTestTags.EMPTY,
        title = title,
        description = description,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        actionText = actionText,
        onAction = onAction,
        modifier = modifier,
    )
}

/**
 * Failure state with an optional retry.
 *
 * [requestId] is the backend correlation ID from the OpenAPI error envelope. It
 * is shown verbatim so a user can quote it in a support request; it is not a
 * secret and contains no token or payload.
 */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    requestId: String? = null,
    icon: ImageVector? = null,
    // Defaulted so retry wording stays consistent across screens; the button
    // appears only when [onRetry] is supplied.
    retryText: String = stringResource(R.string.action_retry),
    onRetry: (() -> Unit)? = null,
) {
    StateScaffold(
        testTag = StateSurfaceTestTags.ERROR,
        title = title,
        description = description,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.error,
        actionText = retryText,
        onAction = onRetry,
        modifier = modifier,
        footer = requestId?.let { id ->
            {
                Text(
                    text = stringResource(R.string.error_reference, id),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

@Composable
private fun StateScaffold(
    testTag: String,
    title: String,
    description: String?,
    icon: ImageVector?,
    iconTint: androidx.compose.ui.graphics.Color,
    actionText: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag)
            // Announced when the surface replaces previous content, so a screen
            // reader user learns the outcome without re-exploring the screen.
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(StudyMentorTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(STATE_ICON_SIZE_DP.dp)
                    .padding(bottom = StudyMentorTheme.spacing.sm),
                tint = iconTint,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = StudyMentorTheme.spacing.sm),
            )
        }
        if (actionText != null && onAction != null) {
            StudyMentorButton(
                text = actionText,
                onClick = onAction,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.padding(top = StudyMentorTheme.spacing.lg),
            )
        }
        if (footer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = StudyMentorTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                footer()
            }
        }
    }
}

private const val INDICATOR_SIZE_DP = 40
private const val STATE_ICON_SIZE_DP = 48
