package com.elenglish.studymentor.ui.flashcards

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elenglish.studymentor.domain.model.Flashcard
import com.elenglish.studymentor.domain.model.FlashcardDeck
import com.elenglish.studymentor.domain.model.FlashcardQueueEntry
import com.elenglish.studymentor.domain.model.FlashcardReviewState
import com.elenglish.studymentor.domain.model.ReviewOutcome
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

@RunWith(RobolectricTestRunner::class)
class FlashcardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(front: String = "Good morning", back: String = "A morning greeting.") =
        FlashcardQueueEntry(
            card = Flashcard("card-1", "deck-1", front, back, hint = "Sunrise", displayOrder = 0),
            state = FlashcardReviewState("card-1", 2, "2026-07-25T08:00:00Z", null, 1, 1, "leitner-5box-v1"),
        )

    @Test
    fun `deck list opens the chosen deck`() {
        var opened: String? = null
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardDeckListContent(
                    state = FlashcardDeckListUiState.Content(
                        listOf(FlashcardDeck("deck-1", "lesson-1", "Greeting basics", null, 3, 0)),
                    ),
                    onOpenDeck = { opened = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.deckRow("deck-1")).performClick()
        assertEquals("deck-1", opened)
    }

    @Test
    fun `the answer is hidden until the learner reveals it`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Reviewing(listOf(entry()), 0, revealed = false, 0, 0),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.CARD_FRONT).assertIsDisplayed()
        // The back is not shown, and the known/forgot buttons are not offered yet.
        composeRule.onNodeWithTag(FlashcardTestTags.CARD_BACK).assertDoesNotExist()
        composeRule.onNodeWithTag(FlashcardTestTags.KNOWN).assertDoesNotExist()
        composeRule.onNodeWithTag(FlashcardTestTags.REVEAL).assertIsDisplayed()
    }

    @Test
    fun `revealing shows the answer and the grading choices`() {
        // Drive the real revealed flag through recomposition, as the screen does.
        val revealed = mutableStateOf(false)
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Reviewing(listOf(entry()), 0, revealed = revealed.value, 0, 0),
                    onReveal = { revealed.value = true }, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.REVEAL).performClick()

        composeRule.onNodeWithTag(FlashcardTestTags.CARD_BACK).assertIsDisplayed()
        composeRule.onNodeWithTag(FlashcardTestTags.KNOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(FlashcardTestTags.FORGOT).assertIsDisplayed()
    }

    @Test
    fun `known and forgot report the outcome`() {
        var answered: ReviewOutcome? = null
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Reviewing(listOf(entry()), 0, revealed = true, 0, 0),
                    onReveal = {}, onAnswer = { answered = it }, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.FORGOT).performClick()
        assertEquals(ReviewOutcome.Forgot, answered)
    }

    @Test
    fun `progress shows the card position`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Reviewing(
                        listOf(entry(), entry("Goodbye", "A farewell.")),
                        index = 0, revealed = false, reviewed = 0, known = 0,
                    ),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("Card 1 of 2").assertExists()
    }

    @Test
    fun `the session summary shows the known tally`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Done(reviewed = 3, known = 2),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.DONE).assertIsDisplayed()
        composeRule.onNodeWithText("2 of 3 known").assertExists()
    }

    @Test
    fun `nothing-due is shown when the queue was empty`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Done(reviewed = 0, known = 0),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("Nothing due right now").assertExists()
    }

    @Test
    fun `the card position still shows the exact figure inside its elevated progress card`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Reviewing(
                        listOf(entry(), entry("Goodbye", "A farewell.")),
                        index = 0, revealed = false, reviewed = 0, known = 0,
                    ),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("Card 1 of 2").assertIsDisplayed()
    }

    @Test
    fun `the session summary title is exposed as a heading`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Done(reviewed = 3, known = 2),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("Review complete").assert(isHeading)
    }

    @Test
    fun `the none-due title is also exposed as a heading, not just body text`() {
        composeRule.setContent {
            StudyMentorTheme {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Done(reviewed = 0, known = 0),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("Nothing due right now").assert(isHeading)
    }

    @Test
    fun `content renders in dark theme with the card front and progress present`() {
        composeRule.setContent {
            StudyMentorTheme(themeMode = ThemeMode.Dark) {
                FlashcardReviewContent(
                    state = FlashcardReviewUiState.Reviewing(listOf(entry()), 0, revealed = false, 0, 0),
                    onReveal = {}, onAnswer = {}, onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag(FlashcardTestTags.CARD_FRONT).assertIsDisplayed()
        composeRule.onNodeWithTag(FlashcardTestTags.PROGRESS).assertIsDisplayed()
    }
}
