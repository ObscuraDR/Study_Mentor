package com.elenglish.studymentor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elenglish.studymentor.ui.theme.StudyMentorTheme

/**
 * Standard content container.
 *
 * When [onClick] is supplied the whole card becomes one accessible button — the
 * Web token set specifies full-card touch coverage — instead of leaving a small
 * tappable region inside a large visual block.
 */
@Composable
fun StudyMentorCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = CardDefaults.cardElevation(
        defaultElevation = StudyMentorTheme.elevation.sm,
        pressedElevation = StudyMentorTheme.elevation.md,
        disabledElevation = StudyMentorTheme.elevation.none,
    )
    val innerPadding = StudyMentorTheme.spacing.md

    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
        ) {
            Column(
                modifier = Modifier.padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                content = content,
            )
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .semantics { role = Role.Button },
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
        ) {
            Column(
                modifier = Modifier.padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                content = content,
            )
        }
    }
}

/**
 * Single-line-primary list row with optional supporting text and leading/trailing
 * icons. Height is floored at the shared list-row touch target.
 */
@Composable
fun StudyMentorListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = StudyMentorTheme.touchTargets.listRow)
        .let { base ->
            if (onClick != null) {
                base
                    .semantics { role = Role.Button }
                    .clickable(enabled = enabled, onClick = onClick)
            } else {
                base
            }
        }
        .padding(
            horizontal = StudyMentorTheme.spacing.md,
            vertical = StudyMentorTheme.spacing.sm,
        )

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE_DP.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE_DP.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Non-interactive descriptive chip. */
@Composable
fun StudyMentorTag(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    AssistChip(
        onClick = { },
        enabled = false,
        modifier = modifier,
        label = { Text(text = text, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(CHIP_ICON_DP.dp)) }
        },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** Selectable chip. Selection state is announced through Material's own semantics. */
@Composable
fun StudyMentorFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = StudyMentorTheme.touchTargets.minimum),
        enabled = enabled,
        label = { Text(text = text, style = MaterialTheme.typography.labelMedium) },
        shape = MaterialTheme.shapes.small,
    )
}

private const val ICON_SIZE_DP = 24
private const val CHIP_ICON_DP = 16
