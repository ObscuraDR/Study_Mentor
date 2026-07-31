package com.elenglish.studymentor.ui.quiz;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityQuizAttemptBinding;
import com.elenglish.studymentor.domain.model.QuizAttemptResult;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class QuizAttemptActivity extends AppCompatActivity {

    private static final String EXTRA_QUIZ_ID = "quizId";
    private QuizAttemptViewModel viewModel;
    private ActivityQuizAttemptBinding binding;
    private QuizQuestionRecyclerAdapter adapter;

    public static Intent createIntent(Context ctx, String quizId) {
        Intent intent = new Intent(ctx, QuizAttemptActivity.class);
        intent.putExtra(EXTRA_QUIZ_ID, quizId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizAttemptBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(QuizAttemptViewModel.class);

        adapter = new QuizQuestionRecyclerAdapter((qId, oId) -> viewModel.selectOption(qId, oId));
        binding.questionsRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.questionsRecycler.setAdapter(adapter);

        binding.submitButton.setOnClickListener(v -> viewModel.submit());

        viewModel.getUiStateLiveData().observe(this, state -> {
            if (state instanceof QuizUiState.Content) {
                QuizUiState.Content c = (QuizUiState.Content) state;
                binding.quizTitle.setText(c.getQuiz().getTitle());
                int answered = c.getAnsweredCount();
                int total = c.getQuiz().getQuestions().size();
                binding.progressText.setText(getString(R.string.quiz_answered_of, answered, total));
                binding.progressBar.setProgress(total > 0 ? (answered * 100 / total) : 0);
                adapter.submitList(c.getQuiz().getQuestions());

                QuizSubmissionState sub = c.getSubmission();
                binding.submitButton.setVisibility(View.VISIBLE);
                binding.resultSection.setVisibility(View.GONE);

                if (sub instanceof QuizSubmissionState.Idle) {
                    binding.submitButton.setText(R.string.quiz_submit);
                    binding.submitButton.setEnabled(c.getCanSubmit());
                } else if (sub instanceof QuizSubmissionState.Submitting) {
                    binding.submitButton.setText(R.string.state_loading);
                    binding.submitButton.setEnabled(false);
                } else if (sub instanceof QuizSubmissionState.Scored) {
                    QuizAttemptResult result = ((QuizSubmissionState.Scored) sub).getResult();
                    binding.submitButton.setVisibility(View.GONE);
                    binding.resultSection.setVisibility(View.VISIBLE);
                    binding.resultTitle.setText(result.getWasReplay()
                            ? R.string.quiz_already_submitted : R.string.quiz_result_title);
                    binding.resultScore.setText(getResources().getQuantityString(
                            R.plurals.quiz_result_score,
                            result.getCorrectAnswers(),
                            result.getCorrectAnswers(),
                            result.getTotalQuestions()));
                    adapter.setResult(result);
                } else if (sub instanceof QuizSubmissionState.Failed) {
                    QuizSubmissionState.Failed f = (QuizSubmissionState.Failed) sub;
                    binding.submitButton.setText(R.string.action_retry);
                    binding.submitButton.setEnabled(f.getCanRetry());
                }
            }
        });
    }
}
