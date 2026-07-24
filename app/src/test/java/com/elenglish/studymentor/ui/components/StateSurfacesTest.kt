package com.elenglish.studymentor.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StateSurfacesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `loading state announces itself instead of showing a blank screen`() {
        composeRule.setContent {
            StudyMentorTheme { LoadingState(message = "Loading subjects") }
        }

        composeRule.onNodeWithTag(StateSurfaceTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading subjects").assertExists()
    }

    @Test
    fun `empty state renders its title and description`() {
        composeRule.setContent {
            StudyMentorTheme {
                EmptyState(title = "No lessons yet", description = "Check back soon.")
            }
        }

        composeRule.onNodeWithTag(StateSurfaceTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No lessons yet").assertExists()
        composeRule.onNodeWithText("Check back soon.").assertExists()
    }

    @Test
    fun `empty state is not an error state`() {
        composeRule.setContent {
            StudyMentorTheme { EmptyState(title = "No lessons yet") }
        }

        composeRule.onNodeWithTag(StateSurfaceTestTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun `error state shows the backend request id for support correlation`() {
        composeRule.setContent {
            StudyMentorTheme {
                ErrorState(
                    title = "Could not load subjects",
                    description = "Check your connection.",
                    requestId = "0191f3a0-7d5c-7b3a-9f11-000000000001",
                )
            }
        }

        composeRule.onNodeWithTag(StateSurfaceTestTags.ERROR).assertIsDisplayed()
        composeRule
            .onNodeWithText("Reference: 0191f3a0-7d5c-7b3a-9f11-000000000001")
            .assertExists()
    }

    @Test
    fun `error state omits the reference line when there is no request id`() {
        composeRule.setContent {
            StudyMentorTheme { ErrorState(title = "Could not load subjects") }
        }

        composeRule.onNodeWithText("Reference: ", substring = true).assertDoesNotExist()
    }

    @Test
    fun `error state retry invokes the caller`() {
        var retries = 0
        composeRule.setContent {
            StudyMentorTheme {
                ErrorState(
                    title = "Could not load subjects",
                    retryText = "Try again",
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }
}
