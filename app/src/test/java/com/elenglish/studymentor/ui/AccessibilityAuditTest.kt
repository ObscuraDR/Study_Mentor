package com.elenglish.studymentor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.elenglish.studymentor.ui.components.ButtonVariant
import com.elenglish.studymentor.ui.components.EmptyState
import com.elenglish.studymentor.ui.components.ErrorState
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.components.StateSurfaceTestTags
import com.elenglish.studymentor.ui.components.StudyMentorButton
import com.elenglish.studymentor.ui.components.StudyMentorIconButton
import com.elenglish.studymentor.ui.components.StudyMentorListItem
import com.elenglish.studymentor.ui.components.StudyMentorTextField
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cross-cutting accessibility checks over the shared primitives.
 *
 * These assert the properties assistive technology actually consumes —
 * accessible names, roles, live regions, touch-target size and behaviour under
 * large font scales — rather than how anything looks.
 */
@RunWith(RobolectricTestRunner::class)
class AccessibilityAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val minimumTarget = 48.dp

    @Test
    fun `every interactive primitive meets the minimum touch target`() {
        composeRule.setContent {
            StudyMentorTheme {
                Column {
                    StudyMentorButton("Primary", {})
                    StudyMentorButton("Secondary", {}, variant = ButtonVariant.Secondary)
                    StudyMentorIconButton(Icons.Filled.Refresh, "Refresh", {})
                    StudyMentorListItem(headline = "Row", onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("Primary").assertHeightIsAtLeast(minimumTarget)
        composeRule.onNodeWithText("Secondary").assertHeightIsAtLeast(minimumTarget)
        composeRule.onNodeWithContentDescription("Refresh")
            .assertHeightIsAtLeast(minimumTarget)
            .assertWidthIsAtLeast(minimumTarget)
        composeRule.onNodeWithText("Row").assertHeightIsAtLeast(minimumTarget)
    }

    @Test
    fun `an icon-only control always has an accessible name`() {
        composeRule.setContent {
            StudyMentorTheme {
                StudyMentorIconButton(Icons.Filled.Refresh, "Reload subjects", {})
            }
        }

        // The signature makes this non-optional; this proves it reaches the tree.
        composeRule.onNodeWithContentDescription("Reload subjects").assertIsDisplayed()
    }

    @Test
    fun `outcome surfaces announce themselves as live regions`() {
        composeRule.setContent {
            StudyMentorTheme { EmptyState(title = "No lessons yet") }
        }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assertExists()
    }

    @Test
    fun `a busy state is announced rather than presenting an empty screen`() {
        composeRule.setContent {
            StudyMentorTheme { LoadingState(message = "Loading subjects") }
        }

        composeRule.onNodeWithTag(StateSurfaceTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading subjects").assertExists()
    }

    @Test
    fun `a field error is exposed through semantics, not colour alone`() {
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

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .assertExists()
    }

    @Test
    fun `controls keep their touch target at a 2x font scale`() {
        composeRule.setContent {
            // Someone using a large accessibility font must still be able to hit
            // the control, and its label must still be found.
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                StudyMentorTheme {
                    Column {
                        StudyMentorButton("Continue", {})
                        StudyMentorListItem(headline = "Lesson row", onClick = {})
                    }
                }
            }
        }

        composeRule.onNodeWithText("Continue").assertHeightIsAtLeast(minimumTarget)
        composeRule.onNodeWithText("Lesson row").assertHeightIsAtLeast(minimumTarget)
    }

    @Test
    fun `content still renders at the largest common font scale`() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                StudyMentorTheme {
                    ErrorState(
                        title = "Could not load subjects",
                        description = "Check your connection and try again.",
                        requestId = "0191f3a0-7d5c-7b3a-9f11-000000000001",
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Could not load subjects").assertExists()
        composeRule.onNodeWithText("Try again").assertExists()
    }
}
