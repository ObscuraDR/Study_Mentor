package com.elenglish.studymentor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.theme.StudyMentorTheme

/** Visual weight of an action. */
enum class ButtonVariant { Primary, Secondary, Text }

/**
 * The single button primitive.
 *
 * States: enabled, disabled, loading. A loading button is disabled, keeps its
 * footprint so the layout does not jump, and announces its busy state to
 * accessibility services via `stateDescription`.
 *
 * The minimum height comes from the shared touch-target token, so no caller can
 * accidentally produce a target smaller than the accessibility floor.
 *
 * @param loadingDescription announced while [loading]; defaults to a generic
 *  "Loading" so a caller cannot silently ship an unlabelled busy state.
 */
@Composable
fun StudyMentorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    loadingDescription: String = stringResource(R.string.state_loading),
) {
    val minHeight = if (variant == ButtonVariant.Primary) {
        StudyMentorTheme.touchTargets.primaryButton
    } else {
        StudyMentorTheme.touchTargets.button
    }

    val buttonModifier = modifier
        .defaultMinSize(minHeight = minHeight)
        .semantics {
            if (loading) stateDescription = loadingDescription
        }

    val content: @Composable () -> Unit = { ButtonContent(text, loading, leadingIcon) }

    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !loading,
            shape = MaterialTheme.shapes.medium,
        ) { content() }

        ButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !loading,
            shape = MaterialTheme.shapes.medium,
        ) { content() }

        ButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !loading,
            shape = MaterialTheme.shapes.medium,
        ) { content() }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: ImageVector?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
    ) {
        if (loading) {
            CircularProgressIndicator(
                // The button already announces its busy state; the spinner itself
                // would otherwise be read as a second, redundant node.
                modifier = Modifier
                    .size(INDICATOR_SIZE_DP.dp)
                    .clearAndSetSemantics { },
                strokeWidth = INDICATOR_STROKE_DP.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                // Decorative: the button's text already carries the meaning.
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE_DP.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Icon-only action. [contentDescription] is required, not optional, because an
 * icon button has no visible label to fall back on.
 */
@Composable
fun StudyMentorIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier
            .size(StudyMentorTheme.touchTargets.icon)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

private const val INDICATOR_SIZE_DP = 18
private const val INDICATOR_STROKE_DP = 2
private const val ICON_SIZE_DP = 18
