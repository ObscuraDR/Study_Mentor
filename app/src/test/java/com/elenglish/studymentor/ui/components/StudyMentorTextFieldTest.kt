package com.elenglish.studymentor.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyMentorTextFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `reports typed text to the caller`() {
        var captured = ""
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorTextField(value = captured, onValueChange = { captured = it }, label = "Email")
            }
        }

        composeRule.onNodeWithText("Email").performTextInput("learner@example.com")

        assertEquals("learner@example.com", captured)
    }

    @Test
    fun `error text is rendered and exposed to accessibility services`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorTextField(
                    value = "",
                    onValueChange = {},
                    label = "Email",
                    errorText = "Enter a valid email address",
                )
            }
        }

        // Visible supporting text.
        composeRule.onNodeWithText("Enter a valid email address").assertExists()
        // And attached as a semantics error, not a visual-only cue.
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .assertExists()
    }

    @Test
    fun `helper text is shown when there is no error`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorTextField(
                    value = "",
                    onValueChange = {},
                    label = "Password",
                    helperText = "At least 12 characters",
                )
            }
        }

        composeRule.onNodeWithText("At least 12 characters").assertExists()
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .assertDoesNotExist()
    }

    @Test
    fun `error text replaces helper text so only one message is shown`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorTextField(
                    value = "",
                    onValueChange = {},
                    label = "Password",
                    helperText = "At least 12 characters",
                    errorText = "Password is too short",
                )
            }
        }

        composeRule.onNodeWithText("Password is too short").assertExists()
        composeRule.onNodeWithText("At least 12 characters").assertDoesNotExist()
    }

    @Test
    fun `disabled field is not editable`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorTextField(
                    value = "",
                    onValueChange = {},
                    label = "Email",
                    enabled = false,
                )
            }
        }

        composeRule.onNodeWithText("Email").assertIsNotEnabled()
    }
}
