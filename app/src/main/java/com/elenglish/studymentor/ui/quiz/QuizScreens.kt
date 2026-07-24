package com.elenglish.studymentor.ui.quiz

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elenglish.studymentor.R
import com.elenglish.studymentor.domain.model.Quiz
import com.elenglish.studymentor.domain.model.QuizAttemptResult
import com.elenglish.studymentor.domain.model.QuizOption
import com.elenglish.studymentor.domain.model.QuizQuestion
import com.elenglish.studymentor.domain.model.QuizQuestionResult
import com.elenglish.studymentor.domain.model.QuizSummary
import com.elenglish.studymentor.ui.catalog.bodyRes
import com.elenglish.studymentor.ui.catalog.titleRes
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.EmptyState
import com.elenglish.studymentor.ui.components.ErrorState
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorCard
import com.elenglish.studymentor.ui.components.StudyMentorListItem
import com.elenglish.studymentor.ui.theme.LocalFeedbackColors
import com.elenglish.studymentor.ui.theme.MotionSpeed
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

object QuizTestTags {
    const val LIST = "quiz_list"
    const val ATTEMPT = "quiz_attempt"
    const val SUBMIT = "quiz_submit"
    const val PROGRESS = "quiz_answered_count"
    const val RESULT = "quiz_result"
    const val REVIEW = "quiz_review"

    fun quizRow(id: String) = "quiz_row_$id"
    fun option(questionId: String, optionId: String) = "quiz_option_${questionId}_$optionId"
    fun question(id: String) = "quiz_question_$id"
}

// Quiz list ------------------------------------------------------------------

@Composable
internal fun QuizListContent(
    state: QuizListUiState,
    onOpenQuiz: (QuizSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QuizListUiState.Loading -> LoadingState(modifier = modifier)

        is QuizListUiState.Failed -> ErrorState(
            title = stringResource(state.kind.titleRes()),
            description = stringResource(state.kind.bodyRes()),
            requestId = state.requestId,
            onRetry = onRetry,
            modifier = modifier,
        )

        QuizListUiState.Empty -> EmptyState(
            title = stringResource(R.string.quiz_empty_title),
            description = stringResource(R.string.quiz_empty_body),
            modifier = modifier,
        )

        is QuizListUiState.Content -> Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(QuizTestTags.LIST),
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.quiz_available_count,
                    state.quizzes.size,
                    state.quizzes.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(StudyMentorTheme.spacing.md),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items = state.quizzes, key = { it.id }) { quiz ->
                    StudyMentorListItem(
                        headline = quiz.title,
                        supportingText = quiz.description ?: pluralStringResource(
                            R.plurals.quiz_question_count,
                            quiz.questionCount,
                            quiz.questionCount,
                        ),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = { onOpenQuiz(quiz) },
                        modifier = Modifier.testTag(QuizTestTags.quizRow(quiz.id)),
                    )
                }
            }
        }
    }
}

// Quiz attempt ---------------------------------------------------------------

@Composable
internal fun QuizAttemptContent(
    state: QuizUiState,
    onSelectOption: (questionId: String, optionId: String) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QuizUiState.Loading -> LoadingState(modifier = modifier)

        is QuizUiState.Failed -> ErrorState(
            title = stringResource(state.kind.titleRes()),
            description = stringResource(state.kind.bodyRes()),
            requestId = state.requestId,
            onRetry = onRetryLoad,
            modifier = modifier,
        )

        is QuizUiState.Content -> {
            val scored = (state.submission as? QuizSubmissionState.Scored)?.result

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .testTag(QuizTestTags.ATTEMPT)
                    .padding(horizontal = StudyMentorTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.md),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = StudyMentorTheme.spacing.md,
                ),
            ) {
                item {
                    if (scored != null) {
                        QuizScoreCard(scored)
                    } else {
                        QuizProgressCard(state.answeredCount, state.quiz.questions.size)
                    }
                }

                items(items = state.quiz.questions, key = { it.id }) { question ->
                    QuestionCard(
                        question = question,
                        selectedOptionId = state.selections[question.id],
                        result = scored?.questionResults?.firstOrNull {
                            it.questionId == question.id
                        },
                        enabled = scored == null &&
                            state.submission !is QuizSubmissionState.Submitting,
                        onSelectOption = { onSelectOption(question.id, it) },
                    )
                }

                item {
                    when (val submission = state.submission) {
                        is QuizSubmissionState.Failed -> SubmissionFailure(submission, onSubmit)

                        is QuizSubmissionState.Scored -> Unit

                        QuizSubmissionState.Idle, QuizSubmissionState.Submitting ->
                            StudyMentorButton(
                                text = stringResource(R.string.quiz_submit),
                                onClick = onSubmit,
                                enabled = state.canSubmit,
                                loading = submission == QuizSubmissionState.Submitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(QuizTestTags.SUBMIT),
                            )
                    }
                }
            }
        }
    }
}

/**
 * How many of the quiz's questions already have a selection, given the same
 * elevated hero-card treatment as Home's and Progress's primary summary
 * card. The bar is a display-only conversion of `answered / total` — the
 * server contract itself (every question must be answered before submit) is
 * unchanged; nothing here recomputes eligibility or correctness.
 */
@Composable
private fun QuizProgressCard(answered: Int, total: Int) {
    val targetFraction = if (total == 0) 0f else (answered.toFloat() / total).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = StudyMentorTheme.motion.duration(MotionSpeed.Normal)),
        label = "quiz_progress_fraction",
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
                text = stringResource(R.string.quiz_progress_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QUIZ_PROGRESS_BAR_HEIGHT_DP.dp)
                    .clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            // The exact count stays as text alongside the bar, so progress is
            // never communicated by the bar's fill alone.
            Text(
                text = stringResource(R.string.quiz_answered_of, answered, total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .testTag(QuizTestTags.PROGRESS)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val QUIZ_PROGRESS_BAR_HEIGHT_DP = 8

/**
 * One question with its options.
 *
 * Options form a single `selectableGroup` of `Role.RadioButton` nodes, which is
 * what gives screen readers the "option N of M, selected" semantics a radio
 * group is expected to have. The whole row is the target, not just the dot.
 */
@Composable
private fun QuestionCard(
    question: QuizQuestion,
    selectedOptionId: String?,
    result: QuizQuestionResult?,
    enabled: Boolean,
    onSelectOption: (String) -> Unit,
) {
    StudyMentorCard(modifier = Modifier.testTag(QuizTestTags.question(question.id))) {
        Text(
            text = question.prompt,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )

        Column(modifier = Modifier.selectableGroup()) {
            question.options.forEach { option ->
                OptionRow(
                    question = question,
                    option = option,
                    selected = selectedOptionId == option.id,
                    enabled = enabled,
                    result = result,
                    onSelect = { onSelectOption(option.id) },
                )
            }
        }

        if (result != null) {
            Text(
                text = stringResource(
                    if (result.correct) R.string.quiz_answer_correct else R.string.quiz_answer_wrong,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (result.correct) {
                    LocalFeedbackColors.current.success
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun OptionRow(
    question: QuizQuestion,
    option: QuizOption,
    selected: Boolean,
    enabled: Boolean,
    result: QuizQuestionResult?,
    onSelect: () -> Unit,
) {
    // After scoring, the server's verdict decides the colour. The app never
    // works out correctness itself; `correctOptionId` came from the backend and
    // is only resolved to text the backend also supplied.
    val markedCorrect = result != null && option.id == result.correctOptionId
    val markedWrongChoice = result != null && option.id == result.selectedOptionId && !result.correct

    val textColor = when {
        markedCorrect -> LocalFeedbackColors.current.success
        markedWrongChoice -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = StudyMentorTheme.touchTargets.listRow)
            .testTag(QuizTestTags.option(question.id, option.id))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = StudyMentorTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudyMentorTheme.spacing.sm),
    ) {
        RadioButton(
            selected = selected,
            // The row owns the click and the semantics; a second focusable node
            // here would make screen-reader navigation announce each option twice.
            onClick = null,
            enabled = enabled,
        )
        Text(text = option.text, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

/**
 * The server's score, given the same elevated hero-card treatment as
 * [QuizProgressCard] and Progress's completion-summary card. Every figure
 * here is a field of the backend's own [QuizAttemptResult] — nothing is
 * computed on the device.
 */
@Composable
private fun QuizScoreCard(result: QuizAttemptResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(QuizTestTags.RESULT)
            .semantics { liveRegion = LiveRegionMode.Polite },
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
                text = stringResource(
                    if (result.wasReplay) R.string.quiz_already_submitted else R.string.quiz_result_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = pluralStringResource(
                    R.plurals.quiz_result_score,
                    result.correctAnswers,
                    result.correctAnswers,
                    result.totalQuestions,
                ),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.progress_percentage, result.scorePercentage),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.quiz_review_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(QuizTestTags.REVIEW),
            )
        }
    }
}

@Composable
private fun SubmissionFailure(state: QuizSubmissionState.Failed, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                onClick = onRetry,
                variant = ButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QuizTestTags.SUBMIT),
            )
        }
    }
}

@Preview(showBackground = true, name = "Quiz — answering")
@Composable
private fun QuizAttemptPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        QuizAttemptContent(
            state = QuizUiState.Content(
                quiz = previewQuiz(),
                selections = mapOf("q1" to "q1o2"),
            ),
            onSelectOption = { _, _ -> },
            onSubmit = {},
            onRetryLoad = {},
        )
    }
}

@Preview(showBackground = true, name = "Quiz — result")
@Composable
private fun QuizResultPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) {
        QuizAttemptContent(
            state = QuizUiState.Content(
                quiz = previewQuiz(),
                selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                submission = QuizSubmissionState.Scored(
                    QuizAttemptResult(
                        attemptId = "0191f3a0-7d5c-7b3a-9f11-0000000a11ce",
                        quizId = "quiz-1",
                        submittedAt = "2026-07-23T08:00:00Z",
                        totalQuestions = 2,
                        correctAnswers = 1,
                        scorePercentage = 50.0,
                        questionResults = listOf(
                            QuizQuestionResult("q1", "q1o2", correct = true, correctOptionId = "q1o2"),
                            QuizQuestionResult("q2", "q2o2", correct = false, correctOptionId = "q2o1"),
                        ),
                        wasReplay = false,
                    ),
                ),
            ),
            onSelectOption = { _, _ -> },
            onSubmit = {},
            onRetryLoad = {},
        )
    }
}

@Preview(showBackground = true, name = "Quiz — dark")
@Composable
private fun QuizAttemptDarkPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) {
        QuizAttemptContent(
            state = QuizUiState.Content(
                quiz = previewQuiz(),
                selections = mapOf("q1" to "q1o2"),
            ),
            onSelectOption = { _, _ -> },
            onSubmit = {},
            onRetryLoad = {},
        )
    }
}

private fun previewQuiz() = Quiz(
    id = "quiz-1",
    lessonId = "lesson-1",
    title = "Greetings check",
    description = null,
    questionCount = 2,
    questions = listOf(
        QuizQuestion(
            id = "q1",
            prompt = "Which greeting suits a morning meeting?",
            displayOrder = 0,
            options = listOf(
                QuizOption("q1o1", "Good night", 0),
                QuizOption("q1o2", "Good morning", 1),
            ),
        ),
        QuizQuestion(
            id = "q2",
            prompt = "How do you say goodbye politely?",
            displayOrder = 1,
            options = listOf(
                QuizOption("q2o1", "See you later", 0),
                QuizOption("q2o2", "Go away", 1),
            ),
        ),
    ),
)
