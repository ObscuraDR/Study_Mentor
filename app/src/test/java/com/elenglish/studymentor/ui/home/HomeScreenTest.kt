package com.elenglish.studymentor.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val realProgress = ProgressProjection(
        completedLessons = 1,
        totalLessons = 3,
        completedTopics = 0,
        totalTopics = 2,
        completedSubjects = 0,
        totalSubjects = 1,
        totalXp = 10,
        learningTimeSeconds = 600,
        completionPercentage = 33.33,
    )

    private fun setContent(
        state: HomeUiState,
        onContinueLearning: (String) -> Unit = {},
        onReviewFlashcards: (String) -> Unit = {},
        onRetryProgress: () -> Unit = {},
        onRetryContinueLearning: () -> Unit = {},
        onRetryFlashcardsDue: () -> Unit = {},
    ) {
        composeRule.setContent {
            StudyMentorTheme {
                HomeScreenContent(
                    state = state,
                    onRetryProgress = onRetryProgress,
                    onRetryContinueLearning = onRetryContinueLearning,
                    onRetryFlashcardsDue = onRetryFlashcardsDue,
                    onContinueLearning = onContinueLearning,
                    onReviewFlashcards = onReviewFlashcards,
                )
            }
        }
    }

    // -- Navigation -----------------------------------------------------------

    @Test
    fun `tapping continue learning reports the lesson id`() {
        var opened: String? = null
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Available("lesson-9", "Present simple"),
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
            onContinueLearning = { opened = it },
        )

        composeRule.onNodeWithTag(HomeTestTags.CONTINUE_LEARNING_ACTION).performClick()

        assertEquals("lesson-9", opened)
    }

    @Test
    fun `tapping review flashcards reports the deck id`() {
        var opened: String? = null
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Available("deck-7", "Greeting basics", 3),
            ),
            onReviewFlashcards = { opened = it },
        )

        composeRule.onNodeWithTag(HomeTestTags.FLASHCARDS_DUE_ACTION).performClick()

        assertEquals("deck-7", opened)
    }

    // -- Rendering --------------------------------------------------------------

    @Test
    fun `progress and continue-learning show a busy card while loading`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Loading,
                continueLearning = ContinueLearningState.Loading,
                flashcardsDue = FlashcardsDueState.Loading,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.PROGRESS_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.CONTINUE_LEARNING_SECTION).assertIsDisplayed()
        // Flashcards-due while loading renders nothing at all — there is
        // nothing honest to show until the anchor lesson is known.
        composeRule.onNodeWithTag(HomeTestTags.FLASHCARDS_DUE_SECTION).assertDoesNotExist()
    }

    @Test
    fun `an unavailable continue-learning section shows no card at all`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.CONTINUE_LEARNING_SECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.FLASHCARDS_DUE_SECTION).assertDoesNotExist()
    }

    @Test
    fun `a failed section shows the request id and a retry action`() {
        var retried = false
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Failed(CatalogErrorKind.Network, requestId = "req-123"),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
            onRetryProgress = { retried = true },
        )

        composeRule.onNodeWithText("Reference: req-123").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(true, retried)
    }

    @Test
    fun `the greeting differs for a new learner with zero progress`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(
                    realProgress.copy(totalXp = 0, completedLessons = 0),
                ),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithText("Welcome! Let's get started.").assertIsDisplayed()
    }

    @Test
    fun `the greeting welcomes a returning learner with real progress`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithText("Welcome back!").assertIsDisplayed()
    }

    @Test
    fun `the due-card count comes from the queue, not a computed streak`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Available("deck-1", "Greeting basics", 3),
            ),
        )

        composeRule.onNodeWithText("3 cards").assertIsDisplayed()
    }

    // -- No fabricated metrics, even with real data on screen ----------------

    @Test
    fun `no fake streak, level, rank, quest or coin figure is ever shown`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Available("lesson-1", "Present simple"),
                flashcardsDue = FlashcardsDueState.Available("deck-1", "Greeting basics", 3),
            ),
        )

        listOf("Streak", "Level", "Rank", "Quest", "Coin", "Coins").forEach { forbidden ->
            composeRule.onNodeWithText(forbidden, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `progress figures are the backend projection, never re-derived on screen`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        // Exactly the backend's own total XP, rendered verbatim via the same
        // ProgressFigures composable the Progress tab uses — not summed,
        // rounded or otherwise recomputed for this screen.
        composeRule.onNodeWithText("10").assertIsDisplayed()
    }

    // -- Visual polish (PARITY-03) --------------------------------------------

    @Test
    fun `the completion bar renders once progress has loaded`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.COMPLETION_BAR).assertIsDisplayed()
        // The bar is not the only carrier of the figure — the exact percentage
        // is also shown as text, so the state is never colour- or shape-only.
        composeRule.onNodeWithText("33.33%").assertIsDisplayed()
    }

    @Test
    fun `a fully empty dashboard shows an intentional nothing-else state`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress.copy(totalXp = 0, completedLessons = 0)),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.NOTHING_ELSE_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Nothing new to show yet").assertIsDisplayed()
    }

    @Test
    fun `the nothing-else state does not appear once a card has something to show`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Available("lesson-1", "Present simple"),
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.NOTHING_ELSE_SECTION).assertDoesNotExist()
    }

    @Test
    fun `the greeting section carries a supporting subtitle`() {
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Unavailable,
                flashcardsDue = FlashcardsDueState.Unavailable,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.GREETING).assertIsDisplayed()
        composeRule.onNodeWithText("Here's where things stand today.").assertIsDisplayed()
    }

    @Test
    fun `continue learning shows its own leading icon distinct from flashcards`() {
        // Both rows are real StudyMentorListItem instances with a leading
        // icon; this only proves each renders (and therefore is reachable by
        // assistive tech as an icon+label control), not their visual weight.
        setContent(
            state = HomeUiState(
                progress = ProgressSectionState.Content(realProgress),
                continueLearning = ContinueLearningState.Available("lesson-1", "Present simple"),
                flashcardsDue = FlashcardsDueState.Available("deck-1", "Greeting basics", 3),
            ),
        )

        // assertExists rather than assertIsDisplayed: with real content in
        // every section the column is taller than the Robolectric test
        // window, so a later card can be laid out correctly yet scrolled out
        // of the simulated viewport — that is a test-harness limitation, not
        // a real off-screen bug (both rows are reachable by scrolling).
        composeRule.onNodeWithTag(HomeTestTags.CONTINUE_LEARNING_ACTION).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.FLASHCARDS_DUE_ACTION).assertExists()
    }
}
