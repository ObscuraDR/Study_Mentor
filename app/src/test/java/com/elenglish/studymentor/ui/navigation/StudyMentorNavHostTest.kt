package com.elenglish.studymentor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.navigation.compose.rememberNavController
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose smoke test for the Phase 1 navigation shells. Runs under Robolectric so
 * it is part of the `:app:testDebugUnitTest` gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyMentorNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Stands in for a destination whose real screen needs the Hilt graph. */
    @Composable
    private fun StubScreen(testTag: String) {
        Box(modifier = Modifier.fillMaxSize().testTag(testTag))
    }

    @Test
    fun `guest start graph renders the guest shell only`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorNavHost(
                    navController = rememberNavController(),
                    startGraph = StudyMentorGraph.Guest,
                    guestContent = { StubScreen(StudyMentorTestTags.GUEST_WELCOME_SCREEN) },
                    authenticatedContent = { StubScreen(StudyMentorTestTags.HOME_SCREEN) },
                )
            }
        }

        composeRule.onNodeWithTag(StudyMentorTestTags.GUEST_WELCOME_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_SCREEN).assertDoesNotExist()
    }

    @Test
    fun `authenticated start graph renders the home shell only`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorNavHost(
                    navController = rememberNavController(),
                    startGraph = StudyMentorGraph.Authenticated,
                    guestContent = { StubScreen(StudyMentorTestTags.GUEST_WELCOME_SCREEN) },
                    authenticatedContent = { StubScreen(StudyMentorTestTags.HOME_SCREEN) },
                )
            }
        }

        composeRule.onNodeWithTag(StudyMentorTestTags.HOME_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(StudyMentorTestTags.GUEST_WELCOME_SCREEN).assertDoesNotExist()
    }
}
