package com.elenglish.studymentor.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.elenglish.studymentor.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VisualParityLayoutTest {

    private LayoutInflater inflater;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        Context themed = new ContextThemeWrapper(app, R.style.Theme_StudyMentor);
        inflater = LayoutInflater.from(themed);
    }

    @Test
    public void quizHasProgressResultAndAccessibleQuestionHierarchy() {
        View attempt = inflater.inflate(R.layout.activity_quiz_attempt, null, false);
        View question = inflater.inflate(R.layout.item_quiz_question, null, false);
        assertNotNull(attempt.findViewById(R.id.progress_bar));
        assertNotNull(attempt.findViewById(R.id.result_section));
        assertTrue(question.findViewById(R.id.question_prompt).isAccessibilityHeading());
    }

    @Test
    public void flashcardsHaveDeckStatesReviewProgressAndSummary() {
        View list = inflater.inflate(R.layout.activity_flashcard_list, null, false);
        View review = inflater.inflate(R.layout.activity_flashcard_review, null, false);
        assertNotNull(list.findViewById(R.id.error_retry));
        assertNotNull(review.findViewById(R.id.progress_bar));
        assertNotNull(review.findViewById(R.id.card_side_label));
        assertTrue(review.findViewById(R.id.done_section).getVisibility() != View.VISIBLE);
    }

    @Test
    public void tutorAlwaysProvidesPrivacySendingAndRetrySurfaces() {
        View tutor = inflater.inflate(R.layout.activity_tutor_chat, null, false);
        assertEquals(View.VISIBLE, tutor.findViewById(R.id.privacy_note).getVisibility());
        assertNotNull(tutor.findViewById(R.id.sending_progress).getContentDescription());
        assertNotNull(tutor.findViewById(R.id.retry_button));
        assertNotNull(tutor.findViewById(R.id.char_count));
    }

    @Test
    public void profileExposesSettingsConflictReloadAndLocalReminderControls() {
        View profile = inflater.inflate(R.layout.fragment_profile, null, false);
        assertNotNull(profile.findViewById(R.id.education_spinner));
        assertNotNull(profile.findViewById(R.id.locale_spinner));
        assertNotNull(profile.findViewById(R.id.daily_goal_input));
        assertNotNull(profile.findViewById(R.id.reload_button));
        assertNotNull(profile.findViewById(R.id.reminder_switch));
        assertNotNull(profile.findViewById(R.id.reminder_time_button));
    }

    @Test
    public void onboardingSkipMeetsMinimumTouchTarget() {
        View onboarding = inflater.inflate(R.layout.activity_onboarding, null, false);
        int required = onboarding.getResources().getDimensionPixelSize(R.dimen.touch_button);
        assertTrue(onboarding.findViewById(R.id.onboarding_skip).getMinimumHeight() >= required);
    }

    @Test
    public void engagementRestoresLevelMissionRecoveryAndProductEntryPoints() {
        View engagement = inflater.inflate(R.layout.section_engagement, null, false);
        assertNotNull(engagement.findViewById(R.id.engagement_level_progress));
        assertNotNull(engagement.findViewById(R.id.daily_mission_progress));
        assertNotNull(engagement.findViewById(R.id.weekly_mission_progress));
        assertNotNull(engagement.findViewById(R.id.recovery_action));
        assertNotNull(engagement.findViewById(R.id.open_wrong_answers));
        assertNotNull(engagement.findViewById(R.id.open_campaign));
    }

    @Test
    public void campaignBossAndEconomyKeepServerOwnedActionsVisible() {
        View campaign = inflater.inflate(R.layout.activity_campaign_map, null, false);
        View boss = inflater.inflate(R.layout.activity_boss_challenge, null, false);
        View economy = inflater.inflate(R.layout.activity_economy, null, false);
        assertNotNull(campaign.findViewById(R.id.open_boss_challenge));
        assertNotNull(campaign.findViewById(R.id.open_economy));
        assertNotNull(boss.findViewById(R.id.boss_submit));
        assertNotNull(economy.findViewById(R.id.purchase_status));
    }

    @Test
    public void newFullProductActionsUseFortyEightDpTouchTargets() {
        int required = inflater.getContext().getResources()
                .getDimensionPixelSize(R.dimen.touch_button);
        View engagement = inflater.inflate(R.layout.section_engagement, null, false);
        View boss = inflater.inflate(R.layout.activity_boss_challenge, null, false);
        View economyItem = inflater.inflate(R.layout.item_shop_product, null, false);
        assertTrue(engagement.findViewById(R.id.open_campaign).getMinimumHeight() >= required);
        assertTrue(boss.findViewById(R.id.boss_submit).getMinimumHeight() >= required);
        assertTrue(economyItem.findViewById(R.id.shop_buy).getMinimumHeight() >= required);
    }
}
