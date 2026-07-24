package com.elenglish.studymentor.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.ui.catalog.bodyRes
import com.elenglish.studymentor.ui.catalog.titleRes
import com.elenglish.studymentor.ui.components.ErrorState
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.components.StudyMentorCard
import com.elenglish.studymentor.ui.theme.MotionSpeed
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

/** Test tags for the Progress tab. */
object ProgressTestTags {
    const val SCREEN = "progress_screen"
    const val HEADER = "progress_header"
    const val SUMMARY_CARD = "progress_summary_card"
    const val COMPLETION_BAR = "progress_completion_bar"
    const val STATISTICS_SECTION = "progress_statistics_section"
}

/**
 * The learner's progress.
 *
 * Every figure is the backend's own [ProgressProjection], rendered exactly as
 * received. The device computes no XP, no completion percentage, no streak,
 * level or rank — the visual polish here is presentation only, layered over
 * the same single `GET /me/progress` read the screen has always made.
 */
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreenContent(state = state, onRetry = viewModel::load, modifier = modifier)
}

@Composable
internal fun ProgressScreenContent(
    state: ProgressUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ProgressUiState.Loading -> LoadingState(modifier = modifier)

        is ProgressUiState.Failed -> ErrorState(
            title = stringResource(state.kind.titleRes()),
            description = stringResource(state.kind.bodyRes()),
            requestId = state.requestId,
            onRetry = onRetry,
            modifier = modifier,
        )

        is ProgressUiState.Content -> {
            // Presentation only: the same real (possibly zero) backend figures
            // render either way. This only changes the supporting line under
            // the heading, the same pattern already used on Home for a
            // learner with nothing completed yet.
            val isNewLearner = state.progress.totalXp == 0 && state.progress.completedLessons == 0

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .testTag(ProgressTestTags.SCREEN)
                    .verticalScroll(rememberScrollState())
                    .padding(StudyMentorTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.lg),
            ) {
                ProgressHeader(isNewLearner = isNewLearner)
                CompletionSummaryCard(progress = state.progress)
                LearningStatisticsSection(progress = state.progress)
            }
        }
    }
}

@Composable
private fun ProgressHeader(isNewLearner: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(ProgressTestTags.HEADER),
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.progress_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                if (isNewLearner) R.string.progress_subtitle_new else R.string.progress_subtitle_returning,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The primary card: total XP and the completion bar together, given the most
 * visual weight on the screen — elevated, larger type, first after the
 * header. Colors stay on the same `surface`/`onSurface` roles every other
 * card already uses, so no new, unaudited pairing is introduced.
 */
@Composable
private fun CompletionSummaryCard(progress: ProgressProjection, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProgressTestTags.SUMMARY_CARD),
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
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
            ) {
                Text(
                    text = progress.totalXp.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.progress_total_xp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = StudyMentorTheme.spacing.xs),
                )
            }

            Text(
                text = stringResource(R.string.progress_completion_bar_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A unit conversion of the backend's own completionPercentage into
            // the 0f..1f fraction the indicator API expects — not a
            // recalculation of progress. The animation duration honours
            // reduced-motion via StudyMentorTheme.motion, collapsing to zero
            // so the bar simply appears at its final value instead of
            // animating.
            val targetFraction = (progress.completionPercentage / PERCENT_SCALE)
                .toFloat()
                .coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = StudyMentorTheme.motion.duration(MotionSpeed.Normal)),
                label = "progress_completion_fraction",
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(COMPLETION_BAR_HEIGHT_DP.dp)
                    .clip(MaterialTheme.shapes.small)
                    .testTag(ProgressTestTags.COMPLETION_BAR),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            // The exact figure is always shown as text alongside the bar, so
            // the state is never communicated by the bar's fill alone.
            Text(
                text = stringResource(R.string.progress_percentage, progress.completionPercentage),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Secondary statistics, given deliberately less visual weight than
 * [CompletionSummaryCard]: a plain [StudyMentorCard] rather than an elevated
 * one, laid out two-per-row rather than four-across so labels stay readable
 * at large font scales.
 */
@Composable
private fun LearningStatisticsSection(progress: ProgressProjection, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.progress_statistics_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        StudyMentorCard(modifier = Modifier.testTag(ProgressTestTags.STATISTICS_SECTION)) {
            Column(verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                ) {
                    StatItem(
                        icon = Icons.Filled.School,
                        label = stringResource(R.string.progress_lessons),
                        value = stringResource(
                            R.string.progress_ratio,
                            progress.completedLessons,
                            progress.totalLessons,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        icon = Icons.Filled.Topic,
                        label = stringResource(R.string.progress_topics),
                        value = stringResource(
                            R.string.progress_ratio,
                            progress.completedTopics,
                            progress.totalTopics,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                ) {
                    StatItem(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        label = stringResource(R.string.progress_subjects),
                        value = stringResource(
                            R.string.progress_ratio,
                            progress.completedSubjects,
                            progress.totalSubjects,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        icon = Icons.Filled.Schedule,
                        label = stringResource(R.string.progress_study_time),
                        value = stringResource(
                            R.string.progress_minutes,
                            progress.learningTimeSeconds / SECONDS_PER_MINUTE,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the value and label text beside it already carry
            // the meaning; a screen reader would otherwise hear the icon
            // name twice for no added information.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(STAT_ICON_SIZE_DP.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private const val STAT_ICON_SIZE_DP = 22
private const val COMPLETION_BAR_HEIGHT_DP = 8
private const val PERCENT_SCALE = 100.0
private const val SECONDS_PER_MINUTE = 60

@Preview(showBackground = true, name = "Progress — with data")
@Composable
private fun ProgressScreenPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        ProgressScreenContent(
            state = ProgressUiState.Content(
                ProgressProjection(
                    completedLessons = 2,
                    totalLessons = 3,
                    completedTopics = 1,
                    totalTopics = 2,
                    completedSubjects = 0,
                    totalSubjects = 1,
                    totalXp = 30,
                    learningTimeSeconds = 900,
                    completionPercentage = 66.67,
                ),
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Progress — new learner")
@Composable
private fun ProgressScreenNewLearnerPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        ProgressScreenContent(
            state = ProgressUiState.Content(
                ProgressProjection(
                    completedLessons = 0,
                    totalLessons = 3,
                    completedTopics = 0,
                    totalTopics = 2,
                    completedSubjects = 0,
                    totalSubjects = 1,
                    totalXp = 0,
                    learningTimeSeconds = 0,
                    completionPercentage = 0.0,
                ),
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Progress — dark")
@Composable
private fun ProgressScreenDarkPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) {
        ProgressScreenContent(
            state = ProgressUiState.Content(
                ProgressProjection(
                    completedLessons = 1,
                    totalLessons = 3,
                    completedTopics = 0,
                    totalTopics = 2,
                    completedSubjects = 0,
                    totalSubjects = 1,
                    totalXp = 10,
                    learningTimeSeconds = 600,
                    completionPercentage = 33.33,
                ),
            ),
            onRetry = {},
        )
    }
}
