package com.elenglish.studymentor.ui.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.Flashcard
import com.elenglish.studymentor.domain.model.FlashcardQueueEntry
import com.elenglish.studymentor.domain.model.FlashcardReviewState
import com.elenglish.studymentor.domain.model.ReviewOutcome
import com.elenglish.studymentor.ui.catalog.bodyRes
import com.elenglish.studymentor.ui.catalog.titleRes
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.EmptyState
import com.elenglish.studymentor.ui.components.ErrorState
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorListItem
import com.elenglish.studymentor.ui.components.StudyMentorTag
import com.elenglish.studymentor.ui.theme.MotionSpeed
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

object FlashcardTestTags {
    const val DECK_LIST = "flashcard_deck_list"
    const val REVIEW = "flashcard_review"
    const val CARD_FRONT = "flashcard_front"
    const val CARD_BACK = "flashcard_back"
    const val REVEAL = "flashcard_reveal"
    const val KNOWN = "flashcard_known"
    const val FORGOT = "flashcard_forgot"
    const val PROGRESS = "flashcard_progress"
    const val DONE = "flashcard_done"

    fun deckRow(id: String) = "flashcard_deck_$id"
}

// Deck list ------------------------------------------------------------------

@Composable
internal fun FlashcardDeckListContent(
    state: FlashcardDeckListUiState,
    onOpenDeck: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        FlashcardDeckListUiState.Loading -> LoadingState(modifier = modifier)

        is FlashcardDeckListUiState.Failed -> ErrorState(
            title = stringResource(state.kind.titleRes()),
            description = stringResource(state.kind.bodyRes()),
            requestId = state.requestId,
            onRetry = onRetry,
            modifier = modifier,
        )

        FlashcardDeckListUiState.Empty -> EmptyState(
            title = stringResource(R.string.flashcards_empty_title),
            description = stringResource(R.string.flashcards_empty_body),
            modifier = modifier,
        )

        is FlashcardDeckListUiState.Content -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag(FlashcardTestTags.DECK_LIST),
        ) {
            items(items = state.decks, key = { it.id }) { deck ->
                StudyMentorListItem(
                    headline = deck.name,
                    supportingText = deck.description ?: pluralStringResource(
                        R.plurals.flashcards_card_count,
                        deck.cardCount,
                        deck.cardCount,
                    ),
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { onOpenDeck(deck.id) },
                    modifier = Modifier.testTag(FlashcardTestTags.deckRow(deck.id)),
                )
            }
        }
    }
}

// Review session -------------------------------------------------------------

@Composable
internal fun FlashcardReviewContent(
    state: FlashcardReviewUiState,
    onReveal: () -> Unit,
    onAnswer: (ReviewOutcome) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        FlashcardReviewUiState.Loading -> LoadingState(modifier = modifier)

        is FlashcardReviewUiState.Failed -> ErrorState(
            title = stringResource(state.kind.titleRes()),
            description = stringResource(state.kind.bodyRes()),
            requestId = state.requestId,
            onRetry = onRetryLoad,
            modifier = modifier,
        )

        is FlashcardReviewUiState.Done -> SessionSummary(state, modifier)

        is FlashcardReviewUiState.Reviewing -> ReviewingCard(state, onReveal, onAnswer, modifier)
    }
}

@Composable
private fun ReviewingCard(
    state: FlashcardReviewUiState.Reviewing,
    onReveal: () -> Unit,
    onAnswer: (ReviewOutcome) -> Unit,
    modifier: Modifier,
) {
    val entry = state.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(FlashcardTestTags.REVIEW)
            .padding(StudyMentorTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md),
    ) {
        FlashcardProgressCard(reviewed = state.reviewed, total = state.queue.size)

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = StudyMentorTheme.elevation.md),
        ) {
            Column(
                modifier = Modifier.padding(StudyMentorTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm)) {
                    StudyMentorTag(
                        text = stringResource(R.string.flashcards_box, entry.state.box),
                    )
                    StudyMentorTag(
                        text = stringResource(
                            R.string.flashcards_memory,
                            entry.state.memoryStrengthPercent,
                        ),
                    )
                }
                Text(
                    text = entry.card.front,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FlashcardTestTags.CARD_FRONT)
                        .semantics { heading() },
                )
                if (state.revealed) {
                    Text(
                        text = entry.card.back,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(FlashcardTestTags.CARD_BACK)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else if (entry.card.hint != null) {
                    Text(
                        text = entry.card.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val submitting = state.submission is ReviewSubmission.Submitting
        if (state.revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md)) {
                StudyMentorButton(
                    text = stringResource(R.string.flashcards_forgot),
                    onClick = { onAnswer(ReviewOutcome.Forgot) },
                    variant = ButtonVariant.Secondary,
                    enabled = !submitting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(FlashcardTestTags.FORGOT),
                )
                StudyMentorButton(
                    text = stringResource(R.string.flashcards_known),
                    onClick = { onAnswer(ReviewOutcome.Known) },
                    loading = submitting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(FlashcardTestTags.KNOWN),
                )
            }
        } else {
            StudyMentorButton(
                text = stringResource(R.string.flashcards_reveal),
                onClick = onReveal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FlashcardTestTags.REVEAL),
            )
        }

        if (state.submission is ReviewSubmission.Failed && state.submission.canRetry) {
            StudyMentorButton(
                text = stringResource(R.string.action_retry),
                onClick = { onAnswer(ReviewOutcome.Known) },
                variant = ButtonVariant.Text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * How many of this session's cards have been reviewed, given the same
 * elevated hero-card treatment as Quiz's and Progress's summary cards. The
 * bar is a display-only conversion of `reviewed / total` — the server-owned
 * Leitner queue and scheduling are untouched; this only mirrors the queue
 * length the backend already returned.
 */
@Composable
private fun FlashcardProgressCard(reviewed: Int, total: Int) {
    val targetFraction = if (total == 0) 0f else (reviewed.toFloat() / total).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = StudyMentorTheme.motion.duration(MotionSpeed.Normal)),
        label = "flashcard_progress_fraction",
    )
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
                text = stringResource(R.string.flashcards_progress_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FLASHCARD_PROGRESS_BAR_HEIGHT_DP.dp)
                    .clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            // The exact card position stays as text alongside the bar, so
            // progress is never communicated by the bar's fill alone.
            Text(
                text = stringResource(R.string.flashcards_progress, reviewed + 1, total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .testTag(FlashcardTestTags.PROGRESS)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val FLASHCARD_PROGRESS_BAR_HEIGHT_DP = 8

/**
 * The session's tally, given the same elevated hero-card treatment as the
 * quiz result card. Every figure here — [FlashcardReviewUiState.Done.known]
 * and `reviewed` — is a count the ViewModel derived from the server's own
 * review outcomes; nothing is recomputed here.
 */
@Composable
private fun SessionSummary(state: FlashcardReviewUiState.Done, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(FlashcardTestTags.DONE)
            .padding(StudyMentorTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = StudyMentorTheme.elevation.md),
        ) {
            Column(
                modifier = Modifier
                    .padding(StudyMentorTheme.spacing.md)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.reviewed == 0) {
                    Text(
                        text = stringResource(R.string.flashcards_none_due_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.flashcards_none_due_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = StudyMentorTheme.spacing.sm),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.flashcards_session_done),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.flashcards_session_score,
                            state.known,
                            state.known,
                            state.reviewed,
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = StudyMentorTheme.spacing.sm),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Flashcard — revealed")
@Composable
private fun FlashcardReviewPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        FlashcardReviewContent(
            state = FlashcardReviewUiState.Reviewing(
                queue = listOf(
                    FlashcardQueueEntry(
                        card = Flashcard("c1", "d1", "Good morning", "A greeting used before midday.", "Sunrise", 0),
                        state = FlashcardReviewState("c1", 2, "2026-07-25T08:00:00Z", null, 1, 1, "leitner-5box-v1"),
                    ),
                ),
                index = 0,
                revealed = true,
                reviewed = 0,
                known = 0,
            ),
            onReveal = {},
            onAnswer = {},
            onRetryLoad = {},
        )
    }
}

@Preview(showBackground = true, name = "Flashcard — session summary")
@Composable
private fun FlashcardSummaryPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        FlashcardReviewContent(
            state = FlashcardReviewUiState.Done(reviewed = 5, known = 4),
            onReveal = {},
            onAnswer = {},
            onRetryLoad = {},
        )
    }
}

@Preview(showBackground = true, name = "Flashcard — dark")
@Composable
private fun FlashcardReviewDarkPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) {
        FlashcardReviewContent(
            state = FlashcardReviewUiState.Reviewing(
                queue = listOf(
                    FlashcardQueueEntry(
                        card = Flashcard("c1", "d1", "Good morning", "A greeting used before midday.", "Sunrise", 0),
                        state = FlashcardReviewState("c1", 2, "2026-07-25T08:00:00Z", null, 1, 1, "leitner-5box-v1"),
                    ),
                ),
                index = 0,
                revealed = false,
                reviewed = 0,
                known = 0,
            ),
            onReveal = {},
            onAnswer = {},
            onRetryLoad = {},
        )
    }
}
