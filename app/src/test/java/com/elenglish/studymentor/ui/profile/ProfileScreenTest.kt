package com.elenglish.studymentor.ui.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.elenglish.studymentor.domain.model.AppLocale
import com.elenglish.studymentor.domain.model.EducationLevel
import com.elenglish.studymentor.domain.model.SharedSettings
import com.elenglish.studymentor.domain.model.UserProfile
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val isHeading = SemanticsMatcher("is a heading") { node ->
    node.config.contains(SemanticsProperties.Heading)
}

@RunWith(RobolectricTestRunner::class)
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun loadedState(message: ProfileMessage? = null) = ProfileUiState(
        loadState = ProfileLoadState.Loaded,
        profile = UserProfile(
            id = "0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f",
            displayName = "Mai",
            email = "mai@example.com",
            avatarKey = null,
            educationLevel = EducationLevel.Intermediate,
            updatedAt = "2026-07-20T08:00:00Z",
            revision = "rev-1",
        ),
        settings = SharedSettings(
            locale = AppLocale.English,
            dailyGoalTargetXp = 50,
            updatedAt = "2026-07-20T08:00:00Z",
            revision = "rev-1",
        ),
        displayNameInput = "Mai",
        educationLevelInput = EducationLevel.Intermediate,
        localeInput = AppLocale.English,
        dailyGoalInput = "50",
        message = message,
    )

    private fun setContent(state: ProfileUiState, onReload: () -> Unit = {}) {
        composeRule.setContent {
            StudyMentorTheme(themeMode = ThemeMode.Light) {
                ProfileScreenContent(
                    state = state,
                    onReload = onReload,
                    onDisplayNameChange = {},
                    onEducationLevelChange = {},
                    onLocaleChange = {},
                    onDailyGoalChange = {},
                    onSaveProfile = {},
                    onSaveSettings = {},
                    onSignOut = {},
                    onSignOutEverywhere = {},
                    reminderContent = {},
                )
            }
        }
    }

    @Test
    fun `the profile section title is exposed as a heading`() {
        setContent(loadedState())

        composeRule.onNodeWithText("Profile").assert(isHeading)
    }

    @Test
    fun `the settings section title is exposed as a heading`() {
        setContent(loadedState())

        composeRule.onNodeWithText("Settings").assert(isHeading)
    }

    @Test
    fun `a success message does not show a revision-conflict reload button`() {
        setContent(loadedState(message = ProfileMessage(kind = ProfileMessageKind.ProfileSaved)))

        composeRule.onNodeWithTag(ProfileTestTags.MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("Profile updated.").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").assertDoesNotExist()
    }

    @Test
    fun `a revision conflict shows the reload action and the request id`() {
        var reloaded = 0
        setContent(
            loadedState(
                message = ProfileMessage(
                    kind = ProfileMessageKind.RevisionConflict,
                    requestId = "req-999",
                    isError = true,
                    requiresReload = true,
                ),
            ),
            onReload = { reloaded++ },
        )

        composeRule.onNodeWithTag(ProfileTestTags.MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("This changed on another device. Reload to see the latest version before saving.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Reference: req-999").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").assertIsDisplayed()
    }

    @Test
    fun `content renders in dark theme with the profile fields present`() {
        composeRule.setContent {
            StudyMentorTheme(themeMode = ThemeMode.Dark) {
                ProfileScreenContent(
                    state = loadedState(),
                    onReload = {},
                    onDisplayNameChange = {},
                    onEducationLevelChange = {},
                    onLocaleChange = {},
                    onDailyGoalChange = {},
                    onSaveProfile = {},
                    onSaveSettings = {},
                    onSignOut = {},
                    onSignOutEverywhere = {},
                    reminderContent = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileTestTags.DISPLAY_NAME).assertIsDisplayed()
        // Below the elevated profile card in a scrollable Column, same
        // Robolectric-viewport pattern documented in the AP-03/04/05/06 reports.
        composeRule.onNodeWithTag(ProfileTestTags.DAILY_GOAL).performScrollTo().assertIsDisplayed()
    }
}
