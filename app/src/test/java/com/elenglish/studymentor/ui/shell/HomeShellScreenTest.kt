package com.elenglish.studymentor.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elenglish.studymentor.ui.navigation.HomeSection
import com.elenglish.studymentor.ui.navigation.StudyMentorTestTags
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeShellScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setShell() {
        composeRule.setContent {
            StudyMentorTheme {
                // The real Profile, Learn and Home screens need the Hilt
                // graph; the shell's own navigation is what this test covers.
                HomeShellScreen(
                    profileContent = { Box(Modifier.fillMaxSize()) },
                    learnContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                    progressContent = { Box(Modifier.fillMaxSize()) },
                    homeContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    @Test
    fun `renders the navigation chrome`() {
        setShell()

        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_TOP_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_BOTTOM_BAR).assertIsDisplayed()
    }

    @Test
    fun `starts on the home section`() {
        setShell()

        composeRule
            .onNodeWithTag(StudyMentorTestTags.sectionContent(HomeSection.Home))
            .assertIsDisplayed()
    }

    @Test
    fun `selecting a tab switches the visible section`() {
        setShell()

        // "Progress" appears both as a tab label and, once selected, as the title.
        composeRule.onAllNodesWithText("Progress")[0].performClick()

        composeRule
            .onNodeWithTag(StudyMentorTestTags.sectionContent(HomeSection.Progress))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(StudyMentorTestTags.sectionContent(HomeSection.Home))
            .assertDoesNotExist()
    }

    @Test
    fun `every section is reachable from the bottom bar`() {
        setShell()

        HomeSection.entries.forEach { section ->
            val label = when (section) {
                HomeSection.Home -> "Home"
                HomeSection.Learn -> "Learn"
                HomeSection.Progress -> "Progress"
                HomeSection.Tutor -> "Tutor"
                HomeSection.Profile -> "Profile"
            }
            composeRule.onAllNodesWithText(label)[0].performClick()
            composeRule
                .onNodeWithTag(StudyMentorTestTags.sectionContent(section))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `a section that hosts its own navigation does not get a second app bar`() {
        setShell()
        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_TOP_BAR).assertIsDisplayed()

        composeRule.onAllNodesWithText("Learn")[0].performClick()

        // The catalog supplies its own app bar with the right title and a back
        // action; stacking the shell's on top would show two headers.
        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_TOP_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_BOTTOM_BAR).assertIsDisplayed()
    }

    @Test
    fun `shell displays no fabricated learning metrics`() {
        setShell()

        // The client is never the author of XP, streaks, levels or scores. Until a
        // backend projection exists, none of these labels may appear.
        listOf("XP", "Streak", "Level", "Rank", "Score", "0%").forEach { forbidden ->
            composeRule.onNodeWithText(forbidden, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `home no longer falls through to the generic pending-backend placeholder`() {
        // Regression guard for the bug this issue fixes: HomeSection.Home used
        // to have no branch of its own and fell through a catch-all `else` to
        // this exact string. The `when` in HomeSectionContent is exhaustive
        // now, so this stays true even if a future section is added without
        // its own case — that would fail to compile instead of silently
        // reusing this message.
        setShell()

        composeRule
            .onNodeWithText("This section is not connected to the backend yet.")
            .assertDoesNotExist()
    }
}
