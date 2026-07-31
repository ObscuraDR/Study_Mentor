package com.elenglish.studymentor.ui.progress;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.FragmentProgressBinding;
import com.elenglish.studymentor.databinding.SectionEngagementBinding;
import com.elenglish.studymentor.domain.model.DailyEngagementMission;
import com.elenglish.studymentor.domain.model.EngagementProjection;
import com.elenglish.studymentor.domain.model.ProgressProjection;
import com.elenglish.studymentor.domain.model.WeeklyEngagementMission;
import com.elenglish.studymentor.ui.campaign.CampaignMapActivity;
import com.elenglish.studymentor.ui.engagement.EngagementUiState;
import com.elenglish.studymentor.ui.engagement.EngagementViewModel;
import com.elenglish.studymentor.ui.engagement.RecoveryClaimState;
import com.elenglish.studymentor.ui.engagement.StreakRecoveryUiState;
import com.elenglish.studymentor.ui.engagement.StreakRecoveryViewModel;
import com.elenglish.studymentor.ui.quiz.WrongAnswerActivity;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProgressFragment extends Fragment {

    private ProgressViewModel viewModel;
    private EngagementViewModel engagementViewModel;
    private StreakRecoveryViewModel recoveryViewModel;
    private FragmentProgressBinding binding;
    private SectionEngagementBinding engagementBinding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProgressViewModel.class);
        engagementViewModel = new ViewModelProvider(this).get(EngagementViewModel.class);
        recoveryViewModel = new ViewModelProvider(this).get(StreakRecoveryViewModel.class);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProgressBinding.inflate(inflater, container, false);
        View engagementView = inflater.inflate(
                R.layout.section_engagement, binding.engagementContainer, true);
        engagementBinding = SectionEngagementBinding.bind(engagementView);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), this::render);
        engagementViewModel.getUiStateLiveData().observe(
                getViewLifecycleOwner(), this::renderEngagement);
        recoveryViewModel.getUiStateLiveData().observe(
                getViewLifecycleOwner(), this::renderRecovery);
        engagementBinding.engagementRetry.setOnClickListener(v -> engagementViewModel.load());
        engagementBinding.recoveryAction.setOnClickListener(v -> {
            StreakRecoveryUiState state = recoveryViewModel.getUiState().getValue();
            if (state instanceof StreakRecoveryUiState.Failed) {
                recoveryViewModel.load();
            } else if (state instanceof StreakRecoveryUiState.Content
                    && ((StreakRecoveryUiState.Content) state).getClaim()
                    instanceof RecoveryClaimState.Failed) {
                recoveryViewModel.retryClaim(() -> {
                    engagementViewModel.load();
                    return kotlin.Unit.INSTANCE;
                });
            } else {
                recoveryViewModel.claim(() -> {
                    engagementViewModel.load();
                    return kotlin.Unit.INSTANCE;
                });
            }
        });
        engagementBinding.openWrongAnswers.setOnClickListener(v ->
                startActivity(WrongAnswerActivity.createIntent(requireContext())));
        engagementBinding.openCampaign.setOnClickListener(v ->
                startActivity(CampaignMapActivity.createIntent(requireContext())));
    }

    private void render(ProgressUiState state) {
        if (state instanceof ProgressUiState.Content) {
            ProgressProjection pp = ((ProgressUiState.Content) state).getProgress();
            boolean isNew = pp.getTotalXp() == 0 && pp.getCompletedLessons() == 0;
            binding.progressSubtitle.setText(isNew
                    ? R.string.progress_subtitle_new : R.string.progress_subtitle_returning);
            binding.totalXp.setText(String.valueOf(pp.getTotalXp()));
            binding.completionBar.setProgress((int)(pp.getCompletionPercentage() * 10));
            binding.completionPercentage.setText(
                    getString(R.string.progress_percentage, pp.getCompletionPercentage()));
            binding.statLessonsValue.setText(getString(
                    R.string.progress_ratio, pp.getCompletedLessons(), pp.getTotalLessons()));
        }
    }

    private void renderEngagement(EngagementUiState state) {
        engagementBinding.engagementLoading.setVisibility(View.GONE);
        engagementBinding.engagementContent.setVisibility(View.GONE);
        engagementBinding.engagementError.setVisibility(View.GONE);
        if (state instanceof EngagementUiState.Loading) {
            engagementBinding.engagementLoading.setVisibility(View.VISIBLE);
        } else if (state instanceof EngagementUiState.Failed) {
            engagementBinding.engagementError.setVisibility(View.VISIBLE);
        } else if (state instanceof EngagementUiState.Content) {
            engagementBinding.engagementContent.setVisibility(View.VISIBLE);
            EngagementProjection value = ((EngagementUiState.Content) state).getEngagement();
            engagementBinding.engagementLevel.setText(
                    getString(R.string.engagement_level, value.getLevel()));
            engagementBinding.engagementStreak.setText(value.getStreak() == 0
                    ? getString(R.string.engagement_streak_none)
                    : getString(R.string.engagement_streak, value.getStreak()));
            engagementBinding.engagementXp.setText(getString(
                    R.string.engagement_xp_to_next,
                    value.getCurrentLevelXp(), value.getNextLevelThreshold()));
            engagementBinding.engagementLevelProgress.setProgress(ratio(
                    value.getCurrentLevelXp(), value.getNextLevelThreshold()));

            DailyEngagementMission daily = value.getMissions().getDaily();
            engagementBinding.dailyMission.setText(getString(
                    R.string.engagement_daily_summary,
                    daily.getLesson().getProgress(), daily.getLesson().getTarget(),
                    daily.getQuiz().getProgress(), daily.getQuiz().getTarget(),
                    daily.getReviews().getProgress(), daily.getReviews().getTarget()));
            int dailyProgress = daily.getLesson().getProgress()
                    + daily.getQuiz().getProgress() + daily.getReviews().getProgress();
            int dailyTarget = daily.getLesson().getTarget()
                    + daily.getQuiz().getTarget() + daily.getReviews().getTarget();
            engagementBinding.dailyMissionProgress.setProgress(ratio(dailyProgress, dailyTarget));

            WeeklyEngagementMission weekly = value.getMissions().getWeekly();
            engagementBinding.weeklyMission.setText(getString(
                    R.string.engagement_weekly_summary,
                    weekly.getActions().getProgress(), weekly.getActions().getTarget(),
                    weekly.getDays().getProgress(), weekly.getDays().getTarget()));
            engagementBinding.weeklyMissionProgress.setProgress(ratio(
                    weekly.getActions().getProgress() + weekly.getDays().getProgress(),
                    weekly.getActions().getTarget() + weekly.getDays().getTarget()));
            engagementBinding.engagementAchievements.setText(
                    achievementNames(value.getAchievements()));
        }
    }

    private void renderRecovery(StreakRecoveryUiState state) {
        engagementBinding.recoveryAction.setVisibility(View.GONE);
        if (state instanceof StreakRecoveryUiState.Loading) {
            engagementBinding.recoveryMessage.setText(R.string.engagement_recovery_loading);
        } else if (state instanceof StreakRecoveryUiState.Failed) {
            engagementBinding.recoveryMessage.setText(R.string.engagement_recovery_unavailable);
            engagementBinding.recoveryAction.setVisibility(View.VISIBLE);
            engagementBinding.recoveryAction.setText(R.string.action_retry);
        } else if (state instanceof StreakRecoveryUiState.Content) {
            StreakRecoveryUiState.Content content = (StreakRecoveryUiState.Content) state;
            RecoveryClaimState claim = content.getClaim();
            if (claim instanceof RecoveryClaimState.Submitting) {
                engagementBinding.recoveryMessage.setText(R.string.engagement_recovery_submitting);
                return;
            }
            if (claim instanceof RecoveryClaimState.Success) {
                engagementBinding.recoveryMessage.setText(R.string.engagement_recovery_success);
                return;
            }
            if (claim instanceof RecoveryClaimState.Failed) {
                engagementBinding.recoveryMessage.setText(R.string.engagement_recovery_claim_failed);
                RecoveryClaimState.Failed failed = (RecoveryClaimState.Failed) claim;
                if (failed.getRetryable()) {
                    engagementBinding.recoveryAction.setVisibility(View.VISIBLE);
                    engagementBinding.recoveryAction.setText(R.string.action_retry);
                }
                return;
            }
            if (content.getEligibility().getEligible()) {
                engagementBinding.recoveryMessage.setText(getString(
                        R.string.engagement_recovery_eligible_with_date,
                        content.getEligibility().getMissedLocalDate()));
                engagementBinding.recoveryAction.setVisibility(View.VISIBLE);
                engagementBinding.recoveryAction.setText(R.string.engagement_recovery_action);
            } else {
                engagementBinding.recoveryMessage.setText(R.string.engagement_recovery_not_eligible);
            }
        }
    }

    private int ratio(int progress, int target) {
        if (target <= 0) return 0;
        return Math.min(1000, (int) ((progress * 1000.0) / target));
    }

    private String achievementNames(List<String> keys) {
        if (keys.isEmpty()) return getString(R.string.engagement_achievements_empty);
        List<String> labels = new ArrayList<>();
        for (String key : keys) {
            labels.add("• " + getString(achievementLabel(key)));
        }
        return android.text.TextUtils.join("\n", labels);
    }

    private int achievementLabel(String key) {
        switch (key) {
            case "first_lesson": return R.string.achievement_first_lesson;
            case "curious_learner": return R.string.achievement_curious_learner;
            case "quiz_explorer": return R.string.achievement_quiz_explorer;
            case "review_habit": return R.string.achievement_review_habit;
            case "seven_day_learner": return R.string.achievement_seven_day_learner;
            case "lesson_path_10": return R.string.achievement_lesson_path_10;
            case "lesson_path_25": return R.string.achievement_lesson_path_25;
            case "subject_explorer_3": return R.string.achievement_subject_explorer_3;
            case "quiz_first_attempt": return R.string.achievement_quiz_first_attempt;
            case "quiz_confident_5": return R.string.achievement_quiz_confident_5;
            case "reviewer_25": return R.string.achievement_reviewer_25;
            case "reviewer_100": return R.string.achievement_reviewer_100;
            case "streak_3_days": return R.string.achievement_streak_3_days;
            case "streak_14_days": return R.string.achievement_streak_14_days;
            default: return R.string.achievement_unknown_placeholder;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        engagementBinding = null;
    }
}
