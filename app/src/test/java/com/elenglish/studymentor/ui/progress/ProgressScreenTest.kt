package com.elenglish.studymentor.ui.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val realProgress = ProgressProjection(
        completedLessons = 2,
        totalLessons = 3,
        completedTopics = 1,
        totalTopics = 2,
        completedSubjects = 0,
        totalSubjects = 1,
        totalXp = 30,
        learningTimeSeconds = 900,
        completionPercentage = 66.67,
    )

    private fun setContent(
        state: ProgressUiState,
        onRetry: () -> Unit = {},
        themeMode: ThemeMode = ThemeMode.Light,
    ) {
        composeRule.setContent {
            StudyMentorTheme(themeMode = themeMode) {
                ProgressScreenContent(state = state, onRetry = onRetry)
            }
        }
    }

    // -- Successful rendering of every backend-derived value ------------------

    @Test
    fun `total xp is the backend value, rendered verbatim`() {
        setContent(ProgressUiState.Content(realProgress))

        composeRule.onNodeWithText("30").assertIsDisplayed()
        composeRule.onNodeWithText("Total XP").assertIsDisplayed()
    }

    @Test
    fun `the completion percentage is shown as exact text, not only a bar`() {
        setContent(ProgressUiState.Content(realProgress))

        composeRule.onNodeWithTag(ProgressTestTags.COMPLETION_BAR).assertIsDisplayed()
        // The state is never bar-fill-only: the exact figure is always text.
        composeRule.onNodeWithText("66.67%").assertIsDisplayed()
    }

    @Test
    fun `every learning statistic is the backend ratio, rendered verbatim`() {
        setContent(ProgressUiState.Content(realProgress))

        // The statistics section sits below the summary card, past the
        // Robolectric test window's fold, so each is scrolled into view first
        // — a test-harness limitation, not a real off-screen bug. Lessons 2
        // of 3, Subjects 0 of 1 — distinct ratios prove each field is read
        // independently, not derived from one another.
        composeRule.onNodeWithText("2 of 3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 of 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0 of 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("15 min").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Lessons").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Topics").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Subjects").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Study time").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `learning time is the backend seconds converted for display, not recalculated`() {
        // 900 backend seconds → "15 min" is a display unit conversion, the same
        // one the Home hero card already performs; the source value is unchanged.
        setContent(ProgressUiState.Content(realProgress.copy(learningTimeSeconds = 900)))

        composeRule.onNodeWithText("15 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the summary card and statistics section are distinct surfaces`() {
        setContent(ProgressUiState.Content(realProgress))

        composeRule.onNodeWithTag(ProgressTestTags.SUMMARY_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(ProgressTestTags.STATISTICS_SECTION).assertExists()
    }

    // -- Header and supporting description -----------------------------------

    @Test
    fun `the header shows the title and a returning-learner subtitle with real progress`() {
        setContent(ProgressUiState.Content(realProgress))

        composeRule.onNodeWithTag(ProgressTestTags.HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("Your progress").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Here's a full breakdown of what you've learned so far.",
        ).assertIsDisplayed()
    }

    // -- Empty / new-user state ----------------------------------------------

    @Test
    fun `a new learner with zero progress sees an intentional supporting line`() {
        setContent(
            ProgressUiState.Content(
                realProgress.copy(totalXp = 0, completedLessons = 0, completionPercentage = 0.0),
            ),
        )

        composeRule.onNodeWithText(
            "Complete a lesson to start tracking your progress here.",
        ).assertIsDisplayed()
        // The real zeroed figures still render honestly — not hidden, not faked.
        composeRule.onNodeWithText("0").assertIsDisplayed()
        composeRule.onNodeWithText("0.00%").assertIsDisplayed()
    }

    // -- Loading state --------------------------------------------------------

    @Test
    fun `the loading state shows the shared loading surface`() {
        setContent(ProgressUiState.Loading)

        composeRule.onNodeWithText("Loading").assertIsDisplayed()
        composeRule.onNodeWithTag(ProgressTestTags.SCREEN).assertDoesNotExist()
    }

    // -- Error / retry / requestId -------------------------------------------

    @Test
    fun `an error shows the request id and a working retry`() {
        var retried = false
        setContent(
            ProgressUiState.Failed(CatalogErrorKind.Network, requestId = "req-progress-1"),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithText("Reference: req-progress-1").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `an unauthorized error keeps the shared error semantics`() {
        setContent(
            ProgressUiState.Failed(CatalogErrorKind.Unauthorized, requestId = "req-progress-2"),
        )

        composeRule.onNodeWithText("Reference: req-progress-2").assertIsDisplayed()
        // The content surface must not appear when the read failed — a zeroed
        // fallback would misreport the learner's real standing.
        composeRule.onNodeWithTag(ProgressTestTags.SCREEN).assertDoesNotExist()
    }

    // -- Dark theme -----------------------------------------------------------

    @Test
    fun `content renders in dark theme with every value present`() {
        setContent(ProgressUiState.Content(realProgress), themeMode = ThemeMode.Dark)

        composeRule.onNodeWithText("30").assertIsDisplayed()
        composeRule.onNodeWithText("66.67%").assertIsDisplayed()
        composeRule.onNodeWithTag(ProgressTestTags.COMPLETION_BAR).assertIsDisplayed()
    }

    // -- No fabricated metrics, even with real data on screen ----------------

    @Test
    fun `no streak, level, rank, coin or quest figure is ever shown`() {
        setContent(ProgressUiState.Content(realProgress))

        listOf("Streak", "Level", "Rank", "Coin", "Coins", "Quest", "Badge", "Leaderboard").forEach { forbidden ->
            composeRule.onNodeWithText(forbidden, substring = true).assertDoesNotExist()
        }
    }
}
