package com.elenglish.studymentor.ui.tutor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elenglish.studymentor.domain.model.TutorAnswer
import com.elenglish.studymentor.domain.model.TutorAnswerStatus
import com.elenglish.studymentor.domain.model.TutorTurn
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Presentation and interaction of the Tutor screen: privacy cue, transcript,
 * refusal/truncated distinction, composer state, and error/retry.
 */
@RunWith(RobolectricTestRunner::class)
class TutorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: TutorUiState,
        onDraftChange: (String) -> Unit = {},
        onSend: () -> Unit = {},
    ) {
        composeRule.setContent {
            StudyMentorTheme {
                TutorScreenContent(state = state, onDraftChange = onDraftChange, onSend = onSend)
            }
        }
    }

    private fun completedAnswer(id: String = "a1", text: String = "Use it after 6pm.") =
        TutorTurn.Answer(
            id,
            TutorAnswer(
                responseId = id,
                lessonId = "lesson-1",
                answer = text,
                createdAt = "2026-07-23T08:00:00Z",
                status = TutorAnswerStatus.Completed,
                wasReplay = false,
            ),
        )

    @Test
    fun `the privacy note is shown even before any question is asked`() {
        setContent(TutorUiState())

        composeRule.onNodeWithTag(TutorTestTags.PRIVACY_NOTE).assertIsDisplayed()
        composeRule.onNodeWithText("Answers are limited to this lesson, and this conversation isn't saved.")
            .assertIsDisplayed()
    }

    @Test
    fun `the privacy note stays visible once the conversation has turns`() {
        setContent(
            TutorUiState(
                turns = listOf(TutorTurn.Question("q1", "When do I use 'good evening'?"), completedAnswer()),
            ),
        )

        composeRule.onNodeWithTag(TutorTestTags.PRIVACY_NOTE).assertIsDisplayed()
    }

    @Test
    fun `a completed answer shows its real text, not a refusal or truncation notice`() {
        setContent(
            TutorUiState(
                turns = listOf(TutorTurn.Question("q1", "When?"), completedAnswer(text = "After 6pm.")),
            ),
        )

        composeRule.onNodeWithText("After 6pm.").assertIsDisplayed()
    }

    @Test
    fun `a refusal is shown as a refusal, not as the tutor's real answer`() {
        setContent(
            TutorUiState(
                turns = listOf(
                    TutorTurn.Question("q1", "Do my homework."),
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
            ),
        )

        composeRule.onNodeWithText("The tutor could not answer this one. Try rephrasing your question.")
            .assertIsDisplayed()
    }

    @Test
    fun `a truncated answer keeps its real text and adds a cut-short notice`() {
        setContent(
            TutorUiState(
                turns = listOf(
                    TutorTurn.Question("q1", "Explain everything."),
                    TutorTurn.Answer(
                        "a1",
                        TutorAnswer(
                            responseId = "a1",
                            lessonId = "lesson-1",
                            answer = "Here is part of the answer",
                            createdAt = "2026-07-23T08:00:00Z",
                            status = TutorAnswerStatus.Truncated,
                            wasReplay = false,
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Here is part of the answer").assertIsDisplayed()
        composeRule.onNodeWithText("This answer was cut short.").assertIsDisplayed()
    }

    @Test
    fun `an empty draft shows no character count yet`() {
        setContent(TutorUiState(draft = ""))

        composeRule.onNodeWithText("0 / 2000").assertDoesNotExist()
    }

    @Test
    fun `a non-empty draft shows its exact character count`() {
        setContent(TutorUiState(draft = "Hello"))

        composeRule.onNodeWithText("5 / 2000").assertIsDisplayed()
    }

    @Test
    fun `send is disabled while a request is in flight`() {
        var sent = 0
        setContent(TutorUiState(draft = "A question", sending = true), onSend = { sent++ })

        composeRule.onNodeWithTag(TutorTestTags.SEND).performClick()

        assertEquals(0, sent)
        composeRule.onNodeWithTag(TutorTestTags.THINKING).assertIsDisplayed()
    }

    @Test
    fun `a failure shows the request id and a working retry`() {
        var retried = 0
        setContent(
            TutorUiState(
                turns = listOf(TutorTurn.Question("q1", "Hi")),
                failure = TutorFailure(TutorErrorKind.ProviderUnavailable, requestId = "req-123", canRetry = true),
            ),
            onSend = { retried++ },
        )

        composeRule.onNodeWithTag(TutorTestTags.FAILURE).assertIsDisplayed()
        composeRule.onNodeWithText("The tutor is unavailable right now.").assertIsDisplayed()
        composeRule.onNodeWithText("Reference: req-123").assertIsDisplayed()

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `content renders in dark theme with the privacy note and transcript present`() {
        composeRule.setContent {
            StudyMentorTheme(themeMode = ThemeMode.Dark) {
                TutorScreenContent(
                    state = TutorUiState(
                        turns = listOf(TutorTurn.Question("q1", "Hi"), completedAnswer()),
                    ),
                    onDraftChange = {},
                    onSend = {},
                )
            }
        }

        composeRule.onNodeWithTag(TutorTestTags.PRIVACY_NOTE).assertIsDisplayed()
        composeRule.onNodeWithTag(TutorTestTags.TRANSCRIPT).assertIsDisplayed()
    }
}
