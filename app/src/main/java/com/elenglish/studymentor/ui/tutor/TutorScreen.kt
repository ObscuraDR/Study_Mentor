package com.elenglish.studymentor.ui.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.TutorAnswer
import com.elenglish.studymentor.domain.model.TutorAnswerStatus
import com.elenglish.studymentor.domain.model.TutorTurn
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.EmptyState
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorTextField
import com.elenglish.studymentor.ui.theme.LocalFeedbackColors
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

object TutorTestTags {
    const val SCREEN = "tutor_screen"
    const val TRANSCRIPT = "tutor_transcript"
    const val INPUT = "tutor_input"
    const val SEND = "tutor_send"
    const val FAILURE = "tutor_failure"
    const val THINKING = "tutor_thinking"
    const val PRIVACY_NOTE = "tutor_privacy_note"

    fun turn(id: String) = "tutor_turn_$id"
}

private const val BUBBLE_WIDTH_FRACTION = 0.85f

@Composable
fun TutorScreen(
    modifier: Modifier = Modifier,
    viewModel: TutorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TutorScreenContent(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        modifier = modifier,
    )
}

@Composable
internal fun TutorScreenContent(
    state: TutorUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Keep the newest turn in view as the conversation grows.
    LaunchedEffect(state.turns.size, state.sending) {
        val lastIndex = state.turns.lastIndex
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TutorTestTags.SCREEN)
            .imePadding(),
    ) {
        // A persistent privacy cue, not just an empty-state message: it stays
        // visible once the conversation starts too, since the same lesson-only,
        // not-saved guarantee applies for the whole screen's lifetime.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = StudyMentorTheme.spacing.md,
                    vertical = StudyMentorTheme.spacing.xs,
                )
                .testTag(TutorTestTags.PRIVACY_NOTE),
            horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(PRIVACY_ICON_SIZE_DP.dp),
            )
            Text(
                text = stringResource(R.string.tutor_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.turns.isEmpty() && !state.sending) {
            EmptyState(
                title = stringResource(R.string.tutor_empty_title),
                description = stringResource(R.string.tutor_empty_body),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(TutorTestTags.TRANSCRIPT),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    StudyMentorTheme.spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
            ) {
                items(items = state.turns, key = { it.id }) { turn ->
                    when (turn) {
                        is TutorTurn.Question -> QuestionBubble(turn)
                        is TutorTurn.Answer -> AnswerBubble(turn.answer)
                    }
                }

                if (state.sending) {
                    item {
                        Text(
                            text = stringResource(R.string.tutor_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .testTag(TutorTestTags.THINKING)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }

        if (state.failure != null) {
            TutorFailureBanner(state.failure, onRetry = onSend)
        }

        Composer(state = state, onDraftChange = onDraftChange, onSend = onSend)
    }
}

/**
 * The learner's own turn, aligned to the trailing edge and tinted with the
 * primary container so it reads as distinctly "mine" against the tutor's
 * leading-aligned, neutral-surface turns below.
 */
@Composable
private fun QuestionBubble(turn: TutorTurn.Question) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(BUBBLE_WIDTH_FRACTION)
                .testTag(TutorTestTags.turn(turn.id)),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(StudyMentorTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.tutor_you),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(text = turn.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * One tutor answer, aligned to the leading edge on a neutral surface so the
 * two participants are visually distinct without relying on colour alone
 * (position + label both carry the distinction).
 *
 * A refusal is presented as a refusal, never as teaching content, with its
 * own icon and muted tone so it doesn't read as a normal completed answer;
 * a truncated answer keeps its existing warning label — passing either off
 * as a complete answer would misrepresent what the provider actually
 * produced.
 */
@Composable
private fun AnswerBubble(answer: TutorAnswer) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(BUBBLE_WIDTH_FRACTION)
                .testTag(TutorTestTags.turn(answer.responseId))
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = StudyMentorTheme.elevation.sm),
        ) {
            Column(
                modifier = Modifier.padding(StudyMentorTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.tutor_tutor),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalFeedbackColors.current.info,
                )

                when (answer.status) {
                    TutorAnswerStatus.Refused -> Row(
                        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(REFUSAL_ICON_SIZE_DP.dp),
                        )
                        Text(
                            text = stringResource(R.string.tutor_refused),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TutorAnswerStatus.Completed, TutorAnswerStatus.Truncated -> Text(
                        text = answer.answer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                if (answer.status == TutorAnswerStatus.Truncated) {
                    Text(
                        text = stringResource(R.string.tutor_truncated),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalFeedbackColors.current.warning,
                    )
                }
            }
        }
    }
}

private const val PRIVACY_ICON_SIZE_DP = 14
private const val REFUSAL_ICON_SIZE_DP = 20

@Composable
private fun TutorFailureBanner(failure: TutorFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TutorTestTags.FAILURE)
            .padding(horizontal = StudyMentorTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(failure.kind.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (failure.requestId != null) {
            Text(
                text = stringResource(R.string.error_reference, failure.requestId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (failure.canRetry) {
            StudyMentorButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                variant = ButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun Composer(
    state: TutorUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(StudyMentorTheme.spacing.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
    ) {
        StudyMentorTextField(
            value = state.draft,
            onValueChange = onDraftChange,
            label = stringResource(R.string.tutor_input_label),
            errorText = if (state.draftTooLong) {
                pluralStringResource(
                    R.plurals.tutor_too_long,
                    MAX_MESSAGE_LENGTH,
                    MAX_MESSAGE_LENGTH,
                )
            } else {
                null
            },
            // Only surfaced once there's a draft to measure, so an empty,
            // untouched field doesn't show a "0 / 2000" count before the
            // learner has typed anything.
            helperText = if (state.draft.isNotEmpty() && !state.draftTooLong) {
                stringResource(R.string.tutor_char_count, state.draftLength, MAX_MESSAGE_LENGTH)
            } else {
                null
            },
            enabled = !state.sending,
            singleLine = false,
            modifier = Modifier
                .weight(1f)
                .testTag(TutorTestTags.INPUT),
        )
        StudyMentorButton(
            text = stringResource(R.string.tutor_send),
            onClick = onSend,
            enabled = state.canSend,
            loading = state.sending,
            leadingIcon = Icons.AutoMirrored.Filled.Send,
            modifier = Modifier.testTag(TutorTestTags.SEND),
        )
    }
}

private const val MAX_MESSAGE_LENGTH =
    com.elenglish.studymentor.data.remote.dto.TutorResponseRequestDto.MAX_MESSAGE_LENGTH

private fun TutorErrorKind.messageRes(): Int = when (this) {
    TutorErrorKind.Network -> R.string.error_network_body
    TutorErrorKind.RateLimited -> R.string.tutor_error_rate_limited
    TutorErrorKind.ProviderUnavailable -> R.string.tutor_error_provider
    TutorErrorKind.ProviderTimeout -> R.string.tutor_error_timeout
    TutorErrorKind.LessonNotFound -> R.string.catalog_not_found_body
    TutorErrorKind.Unauthorized -> R.string.catalog_unauthorized_body
    TutorErrorKind.Generic -> R.string.error_generic_body
}

@Preview(showBackground = true, name = "Tutor — conversation")
@Composable
private fun TutorScreenPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        TutorScreenContent(
            state = TutorUiState(
                turns = listOf(
                    TutorTurn.Question("q1", "When do I use 'good evening'?"),
                    TutorTurn.Answer(
                        "a1",
                        TutorAnswer(
                            responseId = "a1",
                            lessonId = "lesson-1",
                            answer = "Use 'good evening' as a greeting after about 6pm.",
                            createdAt = "2026-07-23T08:00:00Z",
                            status = TutorAnswerStatus.Completed,
                            wasReplay = false,
                        ),
                    ),
                ),
                draft = "",
            ),
            onDraftChange = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, name = "Tutor — refused, dark")
@Composable
private fun TutorRefusedDarkPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) {
        TutorScreenContent(
            state = TutorUiState(
                turns = listOf(
                    TutorTurn.Question("q1", "Can you do my homework for me?"),
                    TutorTurn.Answer(
                        "a1",
                        TutorAnswer(
                            responseId = "a1",
                            lessonId = "lesson-1",
                            answer = "",
                            createdAt = "2026-07-23T08:00:00Z",
                            status = TutorAnswerStatus.Refused,
                            wasReplay = false,
                        ),
                    ),
                ),
                draft = "",
            ),
            onDraftChange = {},
            onSend = {},
        )
    }
}
