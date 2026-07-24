package com.elenglish.studymentor.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.DataOrigin
import com.elenglish.studymentor.domain.model.Difficulty
import com.elenglish.studymentor.domain.model.Lesson
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.domain.model.Subject
import com.elenglish.studymentor.domain.model.Topic
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.EmptyState
import com.elenglish.studymentor.ui.components.ErrorState
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorCard
import com.elenglish.studymentor.ui.theme.LocalFeedbackColors
import com.elenglish.studymentor.ui.components.StudyMentorListItem
import com.elenglish.studymentor.ui.components.StudyMentorTag
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

object CatalogTestTags {
    const val SUBJECTS_LIST = "catalog_subjects_list"
    const val TOPICS_LIST = "catalog_topics_list"
    const val LESSONS_LIST = "catalog_lessons_list"
    const val LESSON_DETAIL = "catalog_lesson_detail"
    const val CACHED_BANNER = "catalog_cached_banner"
    const val COMPLETION_ACTION = "lesson_mark_complete"
    const val COMPLETION_ACCEPTED = "lesson_completion_accepted"
    const val COMPLETION_FAILED = "lesson_completion_failed"
    const val PROGRESS_FIGURES = "progress_figures"
    const val OPEN_QUIZZES = "lesson_open_quizzes"
    const val OPEN_TUTOR = "lesson_open_tutor"
    const val OPEN_FLASHCARDS = "lesson_open_flashcards"
    const val CONTEXT_LINE = "catalog_context_line"
    const val LESSON_COMPLETED_MARK = "lesson_completed_mark"

    fun row(id: String) = "catalog_row_$id"
}

/**
 * Renders one catalog list state.
 *
 * Item order is exactly the order the repository returned, which is the order
 * the backend sent. Nothing here re-sorts or filters.
 *
 * [contextLine] is a static piece of supporting copy, not a heading — the top
 * app bar already carries the screen's accessible name (the backend's own
 * subject/topic title, or "Subjects"), so this never repeats it. It is shown
 * in every state via a `weight(1f)` content slot below it, so a header can
 * appear consistently without any risk of the state surfaces below it
 * (which fill their available space) overflowing an unbounded container.
 */
@Composable
internal fun <T> CatalogListScreen(
    state: CatalogUiState<T>,
    listTestTag: String,
    contextLine: String,
    emptyTitle: String,
    emptyDescription: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> String,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = contextLine,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CatalogTestTags.CONTEXT_LINE)
                .padding(
                    horizontal = StudyMentorTheme.spacing.md,
                    vertical = StudyMentorTheme.spacing.sm,
                ),
        )
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                CatalogUiState.Loading -> LoadingState()

                is CatalogUiState.Failed -> ErrorState(
                    title = stringResource(state.kind.titleRes()),
                    description = stringResource(state.kind.bodyRes()),
                    requestId = state.requestId,
                    onRetry = onRetry,
                )

                is CatalogUiState.Empty -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.origin == DataOrigin.Cached) CachedBanner(null)
                    EmptyState(title = emptyTitle, description = emptyDescription)
                }

                is CatalogUiState.Content -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.origin == DataOrigin.Cached) CachedBanner(state.cachedAtEpochMillis)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(listTestTag),
                        contentPadding = PaddingValues(StudyMentorTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                    ) {
                        items(items = state.items, key = key) { item -> itemContent(item) }
                    }
                }
            }
        }
    }
}

/**
 * Says plainly that the user is looking at a stored copy.
 *
 * Cached data is never presented as live: without this the user could not tell
 * that the catalog might have changed since it was saved.
 */
@Composable
private fun CachedBanner(cachedAtEpochMillis: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CatalogTestTags.CACHED_BANNER)
            .padding(
                horizontal = StudyMentorTheme.spacing.md,
                vertical = StudyMentorTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cachedCopyMessage(cachedAtEpochMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How old the saved copy is, in words.
 *
 * Saying only "saved copy" leaves the user unable to judge whether it is
 * minutes or weeks out of date. When the age is unknown the wording stays
 * vague rather than guessing.
 */
@Composable
internal fun cachedCopyMessage(cachedAtEpochMillis: Long?): String {
    if (cachedAtEpochMillis == null) return stringResource(R.string.catalog_showing_saved_copy)

    val ageMillis = (System.currentTimeMillis() - cachedAtEpochMillis).coerceAtLeast(0)
    val minutes = ageMillis / MILLIS_PER_MINUTE

    return when {
        minutes < 1 -> stringResource(R.string.cached_just_now)
        minutes < MINUTES_PER_HOUR -> stringResource(R.string.cached_minutes_ago, minutes)
        minutes < MINUTES_PER_DAY ->
            stringResource(R.string.cached_hours_ago, minutes / MINUTES_PER_HOUR)
        else -> stringResource(R.string.cached_days_ago, minutes / MINUTES_PER_DAY)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * 60L

@Composable
fun SubjectRow(subject: Subject, onClick: () -> Unit) {
    StudyMentorCard(modifier = Modifier.testTag(CatalogTestTags.row(subject.id))) {
        StudyMentorListItem(
            headline = subject.name,
            leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onClick,
        )
    }
}

@Composable
fun TopicRow(topic: Topic, onClick: () -> Unit) {
    StudyMentorCard(modifier = Modifier.testTag(CatalogTestTags.row(topic.id))) {
        StudyMentorListItem(
            headline = topic.name,
            leadingIcon = Icons.Filled.Topic,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onClick,
        )
    }
}

/**
 * A lesson row. Duration and difficulty are shown as the same [StudyMentorTag]
 * chips lesson detail already uses for them — one visual language for the
 * same two fields, not a new one invented for the list. Difficulty is
 * genuinely nullable in the contract (a value this client predates degrades
 * to `null`), so its tag is omitted rather than shown empty or guessed.
 *
 * [isCompleted] is real, backend-derived state from `GET /me/lesson-completions`
 * (`LEARNING-COMPLETION-STATE-01`) — not a guess or a local flag. It never
 * shows unless the caller explicitly has that confirmation, and it is not
 * communicated by color alone: the leading icon itself changes to a checkmark.
 */
@Composable
fun LessonRow(lesson: Lesson, isCompleted: Boolean = false, onClick: () -> Unit) {
    StudyMentorCard(
        onClick = onClick,
        modifier = Modifier.testTag(CatalogTestTags.row(lesson.id)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.completion_recorded),
                    tint = LocalFeedbackColors.current.success,
                    modifier = Modifier.testTag(CatalogTestTags.LESSON_COMPLETED_MARK),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
            ) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm)) {
                    StudyMentorTag(
                        text = stringResource(R.string.lesson_estimated_minutes, lesson.estimatedMinutes),
                        leadingIcon = Icons.Filled.Schedule,
                    )
                    lesson.difficulty?.let { difficulty ->
                        StudyMentorTag(text = stringResource(difficulty.labelRes()))
                    }
                }
                Text(
                    text = lesson.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lesson detail. Shows only what the backend returned — no derived progress. */
@Composable
internal fun LessonDetailContent(
    state: LessonDetailUiState,
    onRetry: () -> Unit,
    onMarkComplete: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenTutor: () -> Unit,
    onOpenFlashcards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LessonDetailUiState.Loading -> LoadingState(modifier = modifier)

        is LessonDetailUiState.Failed -> ErrorState(
            title = stringResource(state.kind.titleRes()),
            description = stringResource(state.kind.bodyRes()),
            requestId = state.requestId,
            onRetry = onRetry,
            modifier = modifier,
        )

        is LessonDetailUiState.Content -> Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(CatalogTestTags.LESSON_DETAIL)
                .verticalScroll(rememberScrollState())
                .padding(StudyMentorTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.lg),
        ) {
            if (state.origin == DataOrigin.Cached) CachedBanner(null)

            // The lesson's own summary, given the same elevated treatment as
            // Home's hero progress card — colours stay on the same
            // surface/onSurface roles every other card uses, only elevation
            // and type scale carry the extra weight.
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
                        text = state.lesson.title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
                    ) {
                        StudyMentorTag(
                            text = stringResource(
                                R.string.lesson_estimated_minutes,
                                state.lesson.estimatedMinutes,
                            ),
                            leadingIcon = Icons.Filled.Schedule,
                        )
                        state.lesson.difficulty?.let { difficulty ->
                            StudyMentorTag(text = stringResource(difficulty.labelRes()))
                        }
                    }
                    Text(
                        text = state.lesson.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Supporting actions, grouped tightly together and set apart from
            // both the lesson summary above and the primary completion
            // action below by the outer group's larger spacing.
            Column(verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm)) {
                StudyMentorButton(
                    text = stringResource(R.string.quiz_open_action),
                    onClick = onOpenQuizzes,
                    variant = ButtonVariant.Secondary,
                    leadingIcon = Icons.Filled.Quiz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CatalogTestTags.OPEN_QUIZZES),
                )

                StudyMentorButton(
                    text = stringResource(R.string.flashcards_open_action),
                    onClick = onOpenFlashcards,
                    variant = ButtonVariant.Secondary,
                    leadingIcon = Icons.Filled.Style,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CatalogTestTags.OPEN_FLASHCARDS),
                )

                StudyMentorButton(
                    text = stringResource(R.string.tutor_open_action),
                    onClick = onOpenTutor,
                    variant = ButtonVariant.Secondary,
                    leadingIcon = Icons.AutoMirrored.Filled.Chat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CatalogTestTags.OPEN_TUTOR),
                )
            }

            CompletionSection(state = state.completion, onMarkComplete = onMarkComplete)
        }
    }
}

/**
 * The completion action and its outcome.
 *
 * Everything shown after acceptance — XP and every progress figure — comes
 * straight from the backend's response. Nothing here is computed on the device.
 */
@Composable
private fun CompletionSection(
    state: CompletionState,
    onMarkComplete: () -> Unit,
) {
    when (state) {
        is CompletionState.Accepted -> StudyMentorCard(
            modifier = Modifier
                .testTag(CatalogTestTags.COMPLETION_ACCEPTED)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = LocalFeedbackColors.current.success,
                )
                Text(
                    text = stringResource(
                        if (state.result.wasReplay) {
                            R.string.completion_already_recorded
                        } else {
                            R.string.completion_recorded
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalFeedbackColors.current.success,
                )
            }
            Text(
                text = stringResource(R.string.completion_xp_awarded, state.result.event.xpEarned),
                style = MaterialTheme.typography.bodyLarge,
            )
            ProgressFigures(state.result.progress)
        }

        is CompletionState.Failed -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CatalogTestTags.COMPLETION_FAILED)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
        ) {
            Text(
                text = stringResource(state.kind.bodyRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (state.requestId != null) {
                Text(
                    text = stringResource(R.string.error_reference, state.requestId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.canRetry) {
                StudyMentorButton(
                    text = stringResource(R.string.action_retry),
                    onClick = onMarkComplete,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Both cases mean the same thing to the learner — this lesson needs no
        // action — whether the confirmation came from this session's own
        // memory or fresh from the backend's completion read model just now.
        CompletionState.AlreadyCompletedThisSession, is CompletionState.CompletedPreviously -> StudyMentorCard(
            modifier = Modifier.testTag(CatalogTestTags.COMPLETION_ACCEPTED),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = LocalFeedbackColors.current.success,
                )
                Text(
                    text = stringResource(R.string.completion_already_recorded),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalFeedbackColors.current.success,
                )
            }
        }

        CompletionState.Idle, CompletionState.Submitting -> StudyMentorButton(
            text = stringResource(R.string.completion_mark_complete),
            onClick = onMarkComplete,
            loading = state == CompletionState.Submitting,
            leadingIcon = if (state == CompletionState.Submitting) null else Icons.Filled.CheckCircle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CatalogTestTags.COMPLETION_ACTION),
        )
    }
}

/** Renders the server's progress projection verbatim. */
@Composable
internal fun ProgressFigures(progress: ProgressProjection) {
    Column(
        modifier = Modifier.testTag(CatalogTestTags.PROGRESS_FIGURES),
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        ProgressRow(
            stringResource(R.string.progress_total_xp),
            progress.totalXp.toString(),
        )
        ProgressRow(
            stringResource(R.string.progress_lessons),
            stringResource(
                R.string.progress_ratio,
                progress.completedLessons,
                progress.totalLessons,
            ),
        )
        ProgressRow(
            stringResource(R.string.progress_topics),
            stringResource(
                R.string.progress_ratio,
                progress.completedTopics,
                progress.totalTopics,
            ),
        )
        ProgressRow(
            stringResource(R.string.progress_subjects),
            stringResource(
                R.string.progress_ratio,
                progress.completedSubjects,
                progress.totalSubjects,
            ),
        )
        ProgressRow(
            stringResource(R.string.progress_completion),
            stringResource(R.string.progress_percentage, progress.completionPercentage),
        )
        ProgressRow(
            stringResource(R.string.progress_study_time),
            stringResource(R.string.progress_minutes, progress.learningTimeSeconds / 60),
        )
    }
}

@Composable
private fun ProgressRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.Beginner -> R.string.profile_education_beginner
    Difficulty.Intermediate -> R.string.profile_education_intermediate
    Difficulty.Advanced -> R.string.profile_education_advanced
}

internal fun CatalogErrorKind.titleRes(): Int = when (this) {
    CatalogErrorKind.Network -> R.string.error_network_title
    CatalogErrorKind.NotFound -> R.string.catalog_not_found_title
    CatalogErrorKind.Unauthorized -> R.string.catalog_unauthorized_title
    CatalogErrorKind.Generic -> R.string.error_generic_title
}

internal fun CatalogErrorKind.bodyRes(): Int = when (this) {
    CatalogErrorKind.Network -> R.string.error_network_body
    CatalogErrorKind.NotFound -> R.string.catalog_not_found_body
    CatalogErrorKind.Unauthorized -> R.string.catalog_unauthorized_body
    CatalogErrorKind.Generic -> R.string.error_generic_body
}

@Preview(showBackground = true, name = "Lesson detail")
@Composable
private fun LessonDetailPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        LessonDetailContent(
            state = LessonDetailUiState.Content(
                lesson = Lesson(
                    id = "0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f",
                    topicId = "0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e70",
                    slug = "present-simple",
                    title = "Present simple",
                    description = "When to use the present simple, with everyday examples.",
                    estimatedMinutes = 12,
                    difficulty = Difficulty.Beginner,
                    displayOrder = 0,
                ),
                origin = DataOrigin.Live,
            ),
            onRetry = {},
            onMarkComplete = {},
            onOpenQuizzes = {},
            onOpenTutor = {},
            onOpenFlashcards = {},
        )
    }
}
