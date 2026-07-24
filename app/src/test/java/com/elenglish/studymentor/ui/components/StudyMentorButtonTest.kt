package com.elenglish.studymentor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyMentorButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `enabled button invokes its action`() {
        var clicks = 0
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorButton(text = "Continue", onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Continue").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `disabled button is not enabled and does not invoke its action`() {
        var clicks = 0
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorButton(text = "Continue", onClick = { clicks++ }, enabled = false)
            }
        }

        composeRule.onNodeWithText("Continue").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `loading button is disabled so an action cannot be submitted twice`() {
        var clicks = 0
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorButton(text = "Submit", onClick = { clicks++ }, loading = true)
            }
        }

        composeRule.onNodeWithText("Submit").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `every variant meets the minimum touch target`() {
        composeRule.setContent {
            StudyMentorTheme {
                Column {
                    StudyMentorButton("Primary", {}, variant = ButtonVariant.Primary)
                    StudyMentorButton("Secondary", {}, variant = ButtonVariant.Secondary)
                    StudyMentorButton("Text", {}, variant = ButtonVariant.Text)
                }
            }
        }

        composeRule.onNodeWithText("Primary").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Secondary").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Text").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `loading button keeps its label so the action stays identifiable`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorButton(text = "Sign in", onClick = {}, loading = true)
            }
        }

        composeRule.onNodeWithText("Sign in").assertExists()
    }
}
