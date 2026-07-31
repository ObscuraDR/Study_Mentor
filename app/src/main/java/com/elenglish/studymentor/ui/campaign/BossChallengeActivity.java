package com.elenglish.studymentor.ui.campaign;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityBossChallengeBinding;
import com.elenglish.studymentor.domain.model.BossAnswer;
import com.elenglish.studymentor.domain.model.BossChallenge;
import com.elenglish.studymentor.domain.model.BossOption;
import com.elenglish.studymentor.domain.model.BossQuestion;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BossChallengeActivity extends AppCompatActivity {
    private ActivityBossChallengeBinding binding;
    private BossChallengeViewModel viewModel;
    private BossChallenge renderedChallenge;
    private final List<RadioGroup> answerGroups = new ArrayList<>();

    public static Intent createIntent(Context context) {
        return new Intent(context, BossChallengeActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBossChallengeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(BossChallengeViewModel.class);
        binding.bossRetry.setOnClickListener(v -> viewModel.load());
        binding.bossSubmit.setOnClickListener(v -> submit());
        binding.bossRetrySubmit.setOnClickListener(v -> viewModel.retry());
        viewModel.getUiStateLiveData().observe(this, this::render);
    }

    private void render(BossChallengeUiState state) {
        binding.bossLoading.setVisibility(View.GONE);
        binding.bossContent.setVisibility(View.GONE);
        binding.bossError.setVisibility(View.GONE);
        if (state instanceof BossChallengeUiState.Loading) {
            binding.bossLoading.setVisibility(View.VISIBLE);
        } else if (state instanceof BossChallengeUiState.Failed) {
            binding.bossError.setVisibility(View.VISIBLE);
        } else if (state instanceof BossChallengeUiState.Content) {
            BossChallengeUiState.Content content = (BossChallengeUiState.Content) state;
            binding.bossContent.setVisibility(View.VISIBLE);
            if (renderedChallenge == null
                    || !renderedChallenge.getId().equals(content.getChallenge().getId())) {
                renderChallenge(content.getChallenge());
            }
            renderSubmission(content.getSubmission());
        }
    }

    private void renderChallenge(BossChallenge challenge) {
        renderedChallenge = challenge;
        binding.bossName.setText(challenge.getTitle());
        binding.bossDescription.setText(challenge.getDescription());
        binding.bossReward.setText(getString(
                R.string.boss_reward, challenge.getRewardShells()));
        binding.bossQuestions.removeAllViews();
        answerGroups.clear();
        for (BossQuestion question : challenge.getQuestions()) {
            TextView prompt = new TextView(this);
            prompt.setText(question.getPrompt());
            prompt.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            prompt.setPadding(0,
                    getResources().getDimensionPixelSize(R.dimen.spacing_md), 0, 0);
            binding.bossQuestions.addView(prompt);
            RadioGroup group = new RadioGroup(this);
            group.setTag(question.getId());
            for (BossOption option : question.getOptions()) {
                RadioButton button = new RadioButton(this);
                button.setId(View.generateViewId());
                button.setTag(option.getId());
                button.setText(option.getText());
                button.setMinHeight(
                        getResources().getDimensionPixelSize(R.dimen.touch_button));
                group.addView(button);
            }
            binding.bossQuestions.addView(group);
            answerGroups.add(group);
        }
        binding.bossSubmit.setEnabled(challenge.getAvailable());
        if (!challenge.getAvailable()) {
            binding.bossStatus.setText(R.string.boss_unavailable);
        }
    }

    private void renderSubmission(BossSubmissionState submission) {
        binding.bossRetrySubmit.setVisibility(View.GONE);
        binding.bossSubmit.setEnabled(
                renderedChallenge != null && renderedChallenge.getAvailable());
        if (submission instanceof BossSubmissionState.Idle) {
            binding.bossStatus.setText(R.string.boss_answer_all);
        } else if (submission instanceof BossSubmissionState.Submitting) {
            binding.bossStatus.setText(R.string.boss_submitting);
            binding.bossSubmit.setEnabled(false);
        } else if (submission instanceof BossSubmissionState.Success) {
            BossSubmissionState.Success success = (BossSubmissionState.Success) submission;
            binding.bossSubmit.setEnabled(false);
            binding.bossStatus.setText(getString(
                    success.getResult().getPassed()
                            ? R.string.boss_passed : R.string.boss_not_passed,
                    success.getResult().getCorrectAnswers(),
                    success.getResult().getTotalQuestions(),
                    success.getResult().getRewardShells(),
                    success.getResult().getWalletBalance()));
        } else if (submission instanceof BossSubmissionState.Failed) {
            BossSubmissionState.Failed failed = (BossSubmissionState.Failed) submission;
            binding.bossStatus.setText(R.string.boss_submit_failed);
            binding.bossRetrySubmit.setVisibility(
                    failed.getRetryable() ? View.VISIBLE : View.GONE);
        }
    }

    private void submit() {
        List<BossAnswer> answers = new ArrayList<>();
        for (RadioGroup group : answerGroups) {
            int checkedId = group.getCheckedRadioButtonId();
            if (checkedId == -1) {
                binding.bossStatus.setText(R.string.boss_answer_all);
                return;
            }
            View checked = group.findViewById(checkedId);
            answers.add(new BossAnswer(
                    (String) group.getTag(), (String) checked.getTag()));
        }
        viewModel.submit(answers);
    }
}
