package com.elenglish.studymentor.ui.lesson;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityLessonDetailBinding;
import com.elenglish.studymentor.domain.model.CompletionResult;
import com.elenglish.studymentor.domain.model.DataOrigin;
import com.elenglish.studymentor.domain.model.Difficulty;
import com.elenglish.studymentor.domain.model.Lesson;
import com.elenglish.studymentor.domain.model.ProgressProjection;
import com.elenglish.studymentor.ui.catalog.CompletionState;
import com.elenglish.studymentor.ui.catalog.LessonDetailUiState;
import com.elenglish.studymentor.ui.catalog.LessonDetailViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Lesson detail activity with Mark Complete action and supporting links
 * to quizzes, flashcards, and tutor.
 */
@AndroidEntryPoint
public class LessonDetailActivity extends AppCompatActivity {

    private static final String EXTRA_LESSON_ID = "lessonId";

    private LessonDetailViewModel viewModel;
    private ActivityLessonDetailBinding binding;

    public static Intent createIntent(Context context, String lessonId) {
        Intent intent = new Intent(context, LessonDetailActivity.class);
        intent.putExtra(EXTRA_LESSON_ID, lessonId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLessonDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LessonDetailViewModel.class);

        viewModel.getUiStateLiveData().observe(this, state -> {
            if (state == null) return;
            binding.lessonContentCard.setVisibility(View.GONE);
            binding.loadingIndicator.setVisibility(View.GONE);

            if (state instanceof LessonDetailUiState.Content) {
                renderContent((LessonDetailUiState.Content) state);
            } else if (state instanceof LessonDetailUiState.Failed) {
                Toast.makeText(this, getString(R.string.error_generic_body), Toast.LENGTH_SHORT).show();
            } else {
                binding.loadingIndicator.setVisibility(View.VISIBLE);
            }
        });

        binding.quizButton.setOnClickListener(v ->
            Toast.makeText(this, "Quiz pending", Toast.LENGTH_SHORT).show());
        binding.flashcardsButton.setOnClickListener(v ->
            Toast.makeText(this, "Flashcards pending", Toast.LENGTH_SHORT).show());
        binding.tutorButton.setOnClickListener(v ->
            Toast.makeText(this, "Tutor pending", Toast.LENGTH_SHORT).show());
    }

    private void renderContent(LessonDetailUiState.Content content) {
        Lesson lesson = content.getLesson();

        binding.lessonContentCard.setVisibility(View.VISIBLE);
        binding.lessonTitle.setText(lesson.getTitle());
        binding.lessonDescription.setText(lesson.getDescription());

        binding.chipDuration.setText(
                getString(R.string.lesson_estimated_minutes, lesson.getEstimatedMinutes()));

        Difficulty d = lesson.getDifficulty();
        if (d != null) {
            binding.chipDifficulty.setVisibility(View.VISIBLE);
            int res;
            if (d == Difficulty.Beginner) {
                res = R.string.profile_education_beginner;
            } else if (d == Difficulty.Intermediate) {
                res = R.string.profile_education_intermediate;
            } else {
                res = R.string.profile_education_advanced;
            }
            binding.chipDifficulty.setText(res);
        } else {
            binding.chipDifficulty.setVisibility(View.GONE);
        }

        // Cached data banner
        if (content.getOrigin() == DataOrigin.Cached) {
            binding.cachedBanner.setVisibility(View.VISIBLE);
        }

        // Completion action
        renderCompletion(content.getCompletion());
    }

    private void renderCompletion(CompletionState completion) {
        binding.markCompleteButton.setVisibility(View.GONE);
        binding.completionAcceptedGroup.setVisibility(View.GONE);
        binding.completionFailedGroup.setVisibility(View.GONE);

        if (completion instanceof CompletionState.Idle) {
            binding.markCompleteButton.setVisibility(View.VISIBLE);
            binding.markCompleteButton.setText(R.string.completion_mark_complete);
            binding.markCompleteButton.setOnClickListener(v -> viewModel.markComplete(null));
        } else if (completion instanceof CompletionState.Submitting) {
            binding.markCompleteButton.setVisibility(View.VISIBLE);
            binding.markCompleteButton.setText(R.string.state_loading);
            binding.markCompleteButton.setEnabled(false);
        } else if (completion instanceof CompletionState.Accepted) {
            CompletionResult result = ((CompletionState.Accepted) completion).getResult();
            binding.completionAcceptedGroup.setVisibility(View.VISIBLE);
            binding.completionResultText.setText(
                    result.getWasReplay()
                            ? R.string.completion_already_recorded
                            : R.string.completion_recorded);
            binding.completionXpText.setText(
                    getString(R.string.completion_xp_awarded, result.getEvent().getXpEarned()));
            binding.completionProgressGroup.setVisibility(View.VISIBLE);

            ProgressProjection pp = result.getProgress();
            binding.progressLessonsRow.setText(
                    getString(R.string.progress_ratio,
                            pp.getCompletedLessons(), pp.getTotalLessons()));
            binding.progressTopicsRow.setText(
                    getString(R.string.progress_ratio,
                            pp.getCompletedTopics(), pp.getTotalTopics()));
            binding.progressSubjectsRow.setText(
                    getString(R.string.progress_ratio,
                            pp.getCompletedSubjects(), pp.getTotalSubjects()));
        } else if (completion instanceof CompletionState.AlreadyCompletedThisSession
                || completion instanceof CompletionState.CompletedPreviously) {
            binding.completionAcceptedGroup.setVisibility(View.VISIBLE);
            binding.completionResultText.setText(R.string.completion_already_recorded);
            binding.completionXpText.setVisibility(View.GONE);
            binding.completionProgressGroup.setVisibility(View.GONE);
        } else if (completion instanceof CompletionState.Failed) {
            CompletionState.Failed failed = (CompletionState.Failed) completion;
            binding.completionFailedGroup.setVisibility(View.VISIBLE);
            binding.completionFailedText.setText(getString(R.string.error_generic_body));
            if (failed.getCanRetry()) {
                binding.completionRetryButton.setVisibility(View.VISIBLE);
                binding.completionRetryButton.setOnClickListener(v -> viewModel.markComplete(null));
            }
        }
    }
}
