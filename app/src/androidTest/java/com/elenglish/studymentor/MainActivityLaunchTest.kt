package com.elenglish.studymentor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.elenglish.studymentor.ui.navigation.StudyMentorTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * On-device launch check: the Hilt graph builds and the launcher lands on the
 * guest shell, because no session exists on a fresh install.
 */
@HiltAndroidTest
class MainActivityLaunchTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesIntoTheGuestShell() {
        hiltRule.inject()
        composeRule.onNodeWithTag(StudyMentorTestTags.GUEST_WELCOME_SCREEN).assertIsDisplayed()
    }
}
