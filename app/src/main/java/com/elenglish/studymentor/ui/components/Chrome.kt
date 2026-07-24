package com.elenglish.studymentor.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.theme.StudyMentorTheme

/**
 * Standard top app bar.
 *
 * @param onNavigateBack when supplied, renders a labelled back affordance.
 *  [navigateBackDescription] is required alongside it so the icon is never
 *  shipped without an accessible name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMentorTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    navigateBackDescription: String = stringResource(R.string.action_navigate_up),
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                StudyMentorIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = navigateBackDescription,
                    onClick = onNavigateBack,
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** One entry in the bottom navigation bar. */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Bottom navigation chrome.
 *
 * Each item keeps its visible label — icon-only navigation is harder to scan and
 * removes the accessible name — and announces selection via `stateDescription`.
 */
@Composable
fun StudyMentorBottomBar(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                modifier = Modifier.semantics {
                    stateDescription = if (selected) "Selected" else "Not selected"
                },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = {
                    Text(text = item.label, style = MaterialTheme.typography.labelSmall)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Confirmation dialog with a required primary action and an optional dismiss. */
@Composable
fun StudyMentorDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    destructive: Boolean = false,
    /** Optional richer body, e.g. a picker. Replaces [text] when supplied. */
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            if (content != null) {
                content()
            } else {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            StudyMentorButton(
                text = confirmText,
                onClick = onConfirm,
                variant = if (destructive) ButtonVariant.Secondary else ButtonVariant.Text,
            )
        },
        dismissButton = dismissText?.let {
            {
                StudyMentorButton(
                    text = it,
                    onClick = onDismissRequest,
                    variant = ButtonVariant.Text,
                )
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** Snackbar host styled with the app's shape and color tokens. */
@Composable
fun StudyMentorSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/** Standard screen edge padding, so screens do not re-derive layout insets. */
val screenHorizontalPadding: androidx.compose.ui.unit.Dp
    @Composable get() = StudyMentorTheme.spacing.md
