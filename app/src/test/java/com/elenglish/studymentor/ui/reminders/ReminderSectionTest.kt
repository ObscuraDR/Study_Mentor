package com.elenglish.studymentor.ui.reminders

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.elenglish.studymentor.data.preferences.AppPreferencesRepository
import com.elenglish.studymentor.data.preferences.ReminderPreference
import com.elenglish.studymentor.notifications.ReminderScheduler
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val isHeading = SemanticsMatcher("is a heading") { node ->
    node.config.contains(SemanticsProperties.Heading)
}

/**
 * The reminder section's presentation, backed by a real [ReminderViewModel]
 * over mocked dependencies (same pattern as [ReminderViewModelTest]), since
 * the composable is not stateless like the other screens' Content functions.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(preference: ReminderPreference = ReminderPreference()): ReminderViewModel {
        val stored = MutableStateFlow(preference)
        val preferences: AppPreferencesRepository = mockk(relaxed = true) {
            every { reminder } returns stored
            coEvery { setReminder(any()) } answers { stored.value = firstArg() }
        }
        val scheduler: ReminderScheduler = mockk(relaxed = true)
        return ReminderViewModel(preferences, scheduler)
    }

    @Test
    fun `the section title is exposed as a heading`() {
        composeRule.setContent {
            StudyMentorTheme {
                ReminderSection(viewModel = viewModel())
            }
        }

        composeRule.onNodeWithText("Study reminder").assert(isHeading)
    }

    @Test
    fun `the switch and time control are displayed`() {
        composeRule.setContent {
            StudyMentorTheme {
                ReminderSection(viewModel = viewModel(ReminderPreference(enabled = true, hour = 8, minute = 30)))
            }
        }

        composeRule.onNodeWithTag(ReminderTestTags.SWITCH).assertIsDisplayed()
        composeRule.onNodeWithTag(ReminderTestTags.TIME).assertIsDisplayed()
        composeRule.onNodeWithText("Remind me at 08:30").assertIsDisplayed()
    }
}
