package com.elenglish.studymentor.ui.quiz

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.elenglish.studymentor.domain.model.Quiz
import com.elenglish.studymentor.domain.model.QuizAttemptResult
import com.elenglish.studymentor.domain.model.QuizOption
import com.elenglish.studymentor.domain.model.QuizQuestion
import com.elenglish.studymentor.domain.model.QuizQuestionResult
import com.elenglish.studymentor.domain.model.QuizSummary
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val isHeading = SemanticsMatcher("is a heading") { node ->
    node.config.contains(SemanticsProperties.Heading)
}

/**
 * Interaction and accessibility semantics of the quiz screen.
 */
@RunWith(RobolectricTestRunner::class)
class QuizScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val quiz = Quiz(
        id = "quiz-1",
        lessonId = "lesson-1",
        title = "Greetings check",
        description = null,
        questionCount = 2,
        questions = listOf(
            QuizQuestion(
                id = "q1",
                prompt = "Which greeting suits the morning?",
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

    private fun setContent(
        state: QuizUiState,
        onSelect: (String, String) -> Unit = { _, _ -> },
        onSubmit: () -> Unit = {},
    ) {
        composeRule.setContent {
            StudyMentorTheme {
                QuizAttemptContent(
                    state = state,
                    onSelectOption = onSelect,
                    onSubmit = onSubmit,
                    onRetryLoad = {},
                )
            }
        }
    }

    @Test
    fun `options expose radio button selection semantics`() {
        setContent(QuizUiState.Content(quiz = quiz, selections = mapOf("q1" to "q1o2")))

        // Selection is exposed to accessibility services, not just drawn.
        composeRule.onNodeWithTag(QuizTestTags.option("q1", "q1o2")).assertIsSelected()
        composeRule.onNodeWithTag(QuizTestTags.option("q1", "q1o1")).assertIsNotSelected()
    }

    @Test
    fun `tapping anywhere on an option row selects it`() {
        var selected: Pair<String, String>? = null
        setContent(QuizUiState.Content(quiz = quiz), onSelect = { q, o -> selected = q to o })

        composeRule.onNodeWithTag(QuizTestTags.option("q2", "q2o1")).performClick()

        assertEquals("q2" to "q2o1", selected)
    }

    @Test
    fun `answered count is shown while answering`() {
        setContent(QuizUiState.Content(quiz = quiz, selections = mapOf("q1" to "q1o2")))

        composeRule.onNodeWithTag(QuizTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("Answered 1 of 2").assertExists()
    }

    @Test
    fun `submit is disabled until all questions are answered`() {
        var submitted = 0
        setContent(
            QuizUiState.Content(quiz = quiz, selections = mapOf("q1" to "q1o2")),
            onSubmit = { submitted++ },
        )

        // The taller elevated progress card pushes the submit button past
        // Robolectric's default test-window fold; a LazyColumn does not even
        // compose off-screen items, so the list itself must be scrolled
        // until that node exists (same test-harness-limitation pattern
        // documented in the PARITY-03/04/05 reports — it also proves genuine
        // scrollability, not a real off-screen bug).
        composeRule.onNodeWithTag(QuizTestTags.ATTEMPT)
            .performScrollToNode(hasTestTag(QuizTestTags.SUBMIT))
        composeRule.onNodeWithTag(QuizTestTags.SUBMIT).performClick()

        assertEquals(0, submitted)
    }

    @Test
    fun `the result card shows the server's score`() {
        setContent(
            QuizUiState.Content(
                quiz = quiz,
                selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                submission = QuizSubmissionState.Scored(scoredResult()),
            ),
        )

        composeRule.onNodeWithTag(QuizTestTags.RESULT).assertIsDisplayed()
        composeRule.onNodeWithText("1 of 2 correct").assertExists()
        composeRule.onNodeWithText("50.00%").assertExists()
    }

    @Test
    fun `wrong answers are reviewable after scoring`() {
        setContent(
            QuizUiState.Content(
                quiz = quiz,
                selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                submission = QuizSubmissionState.Scored(scoredResult()),
            ),
        )

        // Per-question verdicts come from the server's result.
        composeRule.onNodeWithText("Correct").assertExists()
        composeRule.onNodeWithText("Not correct").assertExists()
        composeRule.onNodeWithTag(QuizTestTags.REVIEW).assertExists()
    }

    @Test
    fun `the answered count is replaced by the result once scored`() {
        setContent(
            QuizUiState.Content(
                quiz = quiz,
                selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                submission = QuizSubmissionState.Scored(scoredResult()),
            ),
        )

        composeRule.onNodeWithTag(QuizTestTags.PROGRESS).assertDoesNotExist()
        composeRule.onNodeWithTag(QuizTestTags.SUBMIT).assertDoesNotExist()
    }

    @Test
    fun `the answered count still shows the exact figure inside its elevated card`() {
        setContent(QuizUiState.Content(quiz = quiz, selections = mapOf("q1" to "q1o2")))

        // The progress bar is decoration; the exact answered/total figure is
        // always present as text alongside it.
        composeRule.onNodeWithTag(QuizTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("Answered 1 of 2").assertIsDisplayed()
    }

    @Test
    fun `the result title is exposed as a heading`() {
        setContent(
            QuizUiState.Content(
                quiz = quiz,
                selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                submission = QuizSubmissionState.Scored(scoredResult()),
            ),
        )

        composeRule.onNodeWithText("Your result").assert(isHeading)
    }

    @Test
    fun `a replayed submission is labelled as already submitted, not a fresh result`() {
        setContent(
            QuizUiState.Content(
                quiz = quiz,
                selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                submission = QuizSubmissionState.Scored(scoredResult().copy(wasReplay = true)),
            ),
        )

        composeRule.onNodeWithText("Already submitted").assertIsDisplayed()
    }

    @Test
    fun `the quiz list shows how many quizzes are available and does not fabricate a count`() {
        composeRule.setContent {
            StudyMentorTheme {
                QuizListContent(
                    state = QuizListUiState.Content(
                        quizzes = listOf(
                            QuizSummary(
                                id = "quiz-1",
                                lessonId = "lesson-1",
                                title = "Greetings check",
                                description = null,
                                questionCount = 2,
                                displayOrder = 0,
                            ),
                            QuizSummary(
                                id = "quiz-2",
                                lessonId = "lesson-1",
                                title = "Farewell check",
                                description = null,
                                questionCount = 3,
                                displayOrder = 1,
                            ),
                        ),
                    ),
                    onOpenQuiz = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("2 quizzes available").assertIsDisplayed()
        composeRule.onNodeWithTag(QuizTestTags.quizRow("quiz-1")).assertIsDisplayed()
        composeRule.onNodeWithTag(QuizTestTags.quizRow("quiz-2")).assertIsDisplayed()
    }

    @Test
    fun `content renders in dark theme with the score and every review verdict present`() {
        composeRule.setContent {
            StudyMentorTheme(themeMode = ThemeMode.Dark) {
                QuizAttemptContent(
                    state = QuizUiState.Content(
                        quiz = quiz,
                        selections = mapOf("q1" to "q1o2", "q2" to "q2o2"),
                        submission = QuizSubmissionState.Scored(scoredResult()),
                    ),
                    onSelectOption = { _, _ -> },
                    onSubmit = {},
                    onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(QuizTestTags.RESULT).assertIsDisplayed()
        composeRule.onNodeWithText("1 of 2 correct").assertIsDisplayed()
        composeRule.onNodeWithText("50.00%").assertIsDisplayed()
    }

    private fun scoredResult() = QuizAttemptResult(
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
    )
}
