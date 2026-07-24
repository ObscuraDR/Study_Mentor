package com.elenglish.studymentor.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.catalog.bodyRes
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorCard
import com.elenglish.studymentor.ui.components.StudyMentorListItem
import com.elenglish.studymentor.ui.theme.MotionSpeed
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

/** Test tags for the Home dashboard. */
object HomeTestTags {
    const val SCREEN = "home_screen"
    const val PROGRESS_SECTION = "home_progress_section"
    const val COMPLETION_BAR = "home_completion_bar"
    const val CONTINUE_LEARNING_SECTION = "home_continue_learning_section"
    const val CONTINUE_LEARNING_ACTION = "home_continue_learning_action"
    const val FLASHCARDS_DUE_SECTION = "home_flashcards_due_section"
    const val FLASHCARDS_DUE_ACTION = "home_flashcards_due_action"
    const val NOTHING_ELSE_SECTION = "home_nothing_else_section"
    const val GREETING = "home_greeting"
}

/**
 * The Home tab.
 *
 * Three independent backend reads, each rendered as soon as it settles. A
 * failure in one never hides a section that already loaded — see
 * [HomeViewModel] for why flashcards-due is the one exception, coupled to
 * continue-learning by the contract itself rather than by choice here.
 *
 * Visually, "Continue learning" carries more weight (elevation, a larger
 * heading, an icon) than "Flashcards to review" — a primary versus secondary
 * action, not a difference in how trustworthy either figure is.
 */
@Composable
fun HomeScreen(
    onContinueLearning: (lessonId: String) -> Unit,
    onReviewFlashcards: (deckId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        onRetryProgress = viewModel::retryProgress,
        onRetryContinueLearning = viewModel::retryContinueLearning,
        onRetryFlashcardsDue = viewModel::retryFlashcardsDue,
        onContinueLearning = onContinueLearning,
        onReviewFlashcards = onReviewFlashcards,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    onRetryProgress: () -> Unit,
    onRetryContinueLearning: () -> Unit,
    onRetryFlashcardsDue: () -> Unit,
    onContinueLearning: (String) -> Unit,
    onReviewFlashcards: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether to greet a learner who has not completed anything yet, so the
    // screen reads as a natural "get started" rather than "keep going" — a
    // presentation choice only; the figures underneath are the same real
    // (possibly zero) backend values either way.
    val isNewLearner = (state.progress as? ProgressSectionState.Content)
        ?.progress
        ?.let { it.totalXp == 0 && it.completedLessons == 0 }
        ?: false

    // Both optional cards are settled and neither has anything to offer: the
    // screen would otherwise end after the progress card with a lot of empty
    // space, which reads as unfinished rather than as an honest "nothing yet".
    val nothingElseToShow = state.continueLearning is ContinueLearningState.Unavailable &&
        state.flashcardsDue is FlashcardsDueState.Unavailable

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(HomeTestTags.SCREEN)
            .verticalScroll(rememberScrollState())
            .padding(StudyMentorTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.lg),
    ) {
        GreetingHeader(isNewLearner = isNewLearner)
        ProgressSection(state = state.progress, onRetry = onRetryProgress)
        ContinueLearningSection(
            state = state.continueLearning,
            onContinue = onContinueLearning,
            onRetry = onRetryContinueLearning,
        )
        FlashcardsDueSection(
            state = state.flashcardsDue,
            onReview = onReviewFlashcards,
            onRetry = onRetryFlashcardsDue,
        )
        if (nothingElseToShow) NothingElseSection()
    }
}

@Composable
private fun GreetingHeader(isNewLearner: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(HomeTestTags.GREETING),
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(
                if (isNewLearner) R.string.home_greeting_new else R.string.home_greeting_returning,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                if (isNewLearner) R.string.home_subtitle_new else R.string.home_subtitle_returning,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The backend's own progress projection, given the most visual weight on the
 * screen: a headline XP figure, an animated completion bar, and compact
 * secondary stats — the same [ProgressProjection] fields the Progress tab
 * shows, laid out for a quick glance rather than a full read.
 */
@Composable
private fun ProgressSection(state: ProgressSectionState, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.PROGRESS_SECTION),
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
            SectionHeading(
                textRes = R.string.progress_title,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
            )
            when (state) {
                ProgressSectionState.Loading -> SectionLoadingRow()
                is ProgressSectionState.Content -> HeroProgressBody(state.progress)
                is ProgressSectionState.Failed -> SectionErrorRow(
                    kind = state.kind,
                    requestId = state.requestId,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun HeroProgressBody(progress: ProgressProjection) {
    Column(verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm)) {
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

        // A unit conversion of the backend's own completionPercentage into the
        // 0f..1f fraction the indicator API expects — not a recalculation of
        // progress. The animation duration honours reduced-motion: it collapses
        // to zero via StudyMentorTheme.motion, so the bar simply appears at its
        // final value instead of animating.
        val targetFraction = (progress.completionPercentage / PERCENT_SCALE)
            .toFloat()
            .coerceIn(0f, 1f)
        val animatedFraction by animateFloatAsState(
            targetValue = targetFraction,
            animationSpec = tween(durationMillis = StudyMentorTheme.motion.duration(MotionSpeed.Normal)),
            label = "home_completion_fraction",
        )
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(COMPLETION_BAR_HEIGHT_DP.dp)
                .clip(MaterialTheme.shapes.small)
                .testTag(HomeTestTags.COMPLETION_BAR),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = stringResource(R.string.progress_percentage, progress.completionPercentage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            CompactStat(
                label = stringResource(R.string.progress_lessons),
                value = stringResource(
                    R.string.progress_ratio,
                    progress.completedLessons,
                    progress.totalLessons,
                ),
                modifier = Modifier.weight(1f),
            )
            CompactStat(
                label = stringResource(R.string.progress_topics),
                value = stringResource(
                    R.string.progress_ratio,
                    progress.completedTopics,
                    progress.totalTopics,
                ),
                modifier = Modifier.weight(1f),
            )
            CompactStat(
                label = stringResource(R.string.progress_subjects),
                value = stringResource(
                    R.string.progress_ratio,
                    progress.completedSubjects,
                    progress.totalSubjects,
                ),
                modifier = Modifier.weight(1f),
            )
            CompactStat(
                label = stringResource(R.string.progress_study_time),
                value = stringResource(R.string.progress_minutes, progress.learningTimeSeconds / SECONDS_PER_MINUTE),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs / 2),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** The first lesson in backend order — see [ContinueLearningState] for why. */
@Composable
private fun ContinueLearningSection(
    state: ContinueLearningState,
    onContinue: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ContinueLearningState.Loading -> PrimaryActionCard {
            SectionHeading(R.string.home_continue_learning_heading, Icons.AutoMirrored.Filled.MenuBook)
            SectionLoadingRow()
        }

        is ContinueLearningState.Available -> PrimaryActionCard {
            SectionHeading(R.string.home_continue_learning_heading, Icons.AutoMirrored.Filled.MenuBook)
            StudyMentorListItem(
                headline = state.lessonTitle,
                leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = { onContinue(state.lessonId) },
                modifier = Modifier.testTag(HomeTestTags.CONTINUE_LEARNING_ACTION),
            )
        }

        is ContinueLearningState.Failed -> PrimaryActionCard {
            SectionHeading(R.string.home_continue_learning_heading, Icons.AutoMirrored.Filled.MenuBook)
            SectionErrorRow(kind = state.kind, requestId = state.requestId, onRetry = onRetry)
        }

        // An empty catalog is a valid backend answer, not an error: no card,
        // no retry action, nothing invented in its place.
        ContinueLearningState.Unavailable -> Unit
    }
}

/** Cards due in the anchor lesson's deck — see [FlashcardsDueState] for scope. */
@Composable
private fun FlashcardsDueSection(
    state: FlashcardsDueState,
    onReview: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is FlashcardsDueState.Available -> StudyMentorCard(
            modifier = Modifier.testTag(HomeTestTags.FLASHCARDS_DUE_SECTION),
        ) {
            SectionHeading(R.string.home_flashcards_due_heading, Icons.Filled.Style)
            StudyMentorListItem(
                headline = state.deckName,
                supportingText = pluralStringResource(
                    R.plurals.flashcards_card_count,
                    state.dueCount,
                    state.dueCount,
                ),
                leadingIcon = Icons.Filled.Style,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = { onReview(state.deckId) },
                modifier = Modifier.testTag(HomeTestTags.FLASHCARDS_DUE_ACTION),
            )
        }

        is FlashcardsDueState.Failed -> StudyMentorCard(
            modifier = Modifier.testTag(HomeTestTags.FLASHCARDS_DUE_SECTION),
        ) {
            SectionHeading(R.string.home_flashcards_due_heading, Icons.Filled.Style)
            SectionErrorRow(kind = state.kind, requestId = state.requestId, onRetry = onRetry)
        }

        // Still resolving the anchor lesson, or genuinely nothing due right
        // now — either way there is nothing honest to show in its place.
        FlashcardsDueState.Loading, FlashcardsDueState.Unavailable -> Unit
    }
}

/**
 * Shown only when neither optional card has anything to offer, so the screen
 * never just trails off into blank space after the progress card.
 */
@Composable
private fun NothingElseSection() {
    StudyMentorCard(modifier = Modifier.testTag(HomeTestTags.NOTHING_ELSE_SECTION)) {
        Text(
            text = stringResource(R.string.home_nothing_yet_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_nothing_yet_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A card given extra visual weight (higher elevation, a plain [Card] rather
 * than the standard [StudyMentorCard]) to read as the primary action on the
 * screen. Colors stay on the same `surface`/`onSurface` roles every other
 * card uses — only elevation and typography carry the emphasis, so no new,
 * unaudited color pairing is introduced.
 */
@Composable
private fun PrimaryActionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.CONTINUE_LEARNING_SECTION),
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
            content = content,
        )
    }
}

@Composable
private fun SectionHeading(textRes: Int, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the heading text beside it already carries the meaning.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SECTION_ICON_SIZE_DP.dp),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Compact busy row for one section of the dashboard, not a full-surface takeover. */
@Composable
private fun SectionLoadingRow(modifier: Modifier = Modifier) {
    val message = stringResource(R.string.state_loading)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(SECTION_INDICATOR_SIZE_DP.dp)
                .semantics { contentDescription = message },
            strokeWidth = SECTION_INDICATOR_STROKE_DP.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Compact failure row for one section of the dashboard, with the same
 * `requestId` correlation and retry pattern as every full-screen error state.
 */
@Composable
private fun SectionErrorRow(
    kind: CatalogErrorKind,
    requestId: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(kind.bodyRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (requestId != null) {
            Text(
                text = stringResource(R.string.error_reference, requestId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyMentorButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
            variant = ButtonVariant.Secondary,
        )
    }
}

private const val SECTION_INDICATOR_SIZE_DP = 20
private const val SECTION_INDICATOR_STROKE_DP = 2
private const val SECTION_ICON_SIZE_DP = 22
private const val COMPLETION_BAR_HEIGHT_DP = 8
private const val PERCENT_SCALE = 100.0
private const val SECONDS_PER_MINUTE = 60

@Preview(showBackground = true, name = "Home — with data")
@Composable
private fun HomeScreenPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        HomeScreenContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(
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
                continueLearning = ContinueLearningState.Available("lesson-1", "Present simple"),
                flashcardsDue = FlashcardsDueState.Available("deck-1", "Greeting basics", 3),
            ),
            onRetryProgress = {},
            onRetryContinueLearning = {},
            onRetryFlashcardsDue = {},
            onContinueLearning = {},
            onReviewFlashcards = {},
        )
    }
}

@Preview(showBackground = true, name = "Home — new learner")
@Composable
private fun HomeScreenNewLearnerPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        HomeScreenContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(
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
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
            onRetryProgress = {},
            onRetryContinueLearning = {},
            onRetryFlashcardsDue = {},
            onContinueLearning = {},
            onReviewFlashcards = {},
        )
    }
}

@Preview(showBackground = true, name = "Home — dark")
@Composable
private fun HomeScreenDarkPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) {
        HomeScreenContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(
                    ProgressProjection(
                        completedLessons = 2,
                        totalLessons = 3,
                        completedTopics = 1,
                        totalTopics = 2,
                        completedSubjects = 0,
                        totalSubjects = 1,
                        totalXp = 40,
                        learningTimeSeconds = 1500,
                        completionPercentage = 66.67,
                    ),
                ),
                continueLearning = ContinueLearningState.Available("lesson-2", "Asking for directions"),
                flashcardsDue = FlashcardsDueState.Available("deck-2", "Travel phrases", 5),
            ),
            onRetryProgress = {},
            onRetryContinueLearning = {},
            onRetryFlashcardsDue = {},
            onContinueLearning = {},
            onReviewFlashcards = {},
        )
    }
}
