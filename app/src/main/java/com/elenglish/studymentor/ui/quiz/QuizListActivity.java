package com.elenglish.studymentor.ui.quiz;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityQuizListBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class QuizListActivity extends AppCompatActivity {

    private static final String EXTRA_LESSON_ID = "lessonId";
    private QuizListViewModel viewModel;
    private ActivityQuizListBinding binding;
    private QuizSummaryAdapter adapter;

    public static Intent createIntent(Context ctx, String lessonId) {
        Intent intent = new Intent(ctx, QuizListActivity.class);
        intent.putExtra(EXTRA_LESSON_ID, lessonId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(QuizListViewModel.class);

        adapter = new QuizSummaryAdapter(quiz -> {
            startActivity(QuizAttemptActivity.createIntent(this, quiz.getId()));
        });
        binding.quizRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.quizRecycler.setAdapter(adapter);

        viewModel.getUiStateLiveData().observe(this, state -> {
            hideAll();
            if (state instanceof QuizListUiState.Content) {
                int count = ((QuizListUiState.Content) state).getQuizzes().size();
                adapter.submitList(((QuizListUiState.Content) state).getQuizzes());
                binding.quizCount.setText(getResources().getQuantityString(
                        R.plurals.quiz_available_count, count, count));
                binding.quizRecycler.setVisibility(View.VISIBLE);
            } else if (state instanceof QuizListUiState.Empty) {
                binding.emptyState.setVisibility(View.VISIBLE);
            } else if (state instanceof QuizListUiState.Loading) {
                binding.loading.setVisibility(View.VISIBLE);
            } else if (state instanceof QuizListUiState.Failed) {
                binding.errorState.setVisibility(View.VISIBLE);
                binding.errorTitle.setText(R.string.error_generic_title);
                binding.errorDesc.setText(R.string.error_generic_body);
                binding.errorRetry.setOnClickListener(v -> viewModel.load());
            }
        });
    }

    private void hideAll() {
        binding.quizRecycler.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.loading.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
    }
}
