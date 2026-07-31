package com.elenglish.studymentor.ui.quiz;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityWrongAnswersBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class WrongAnswerActivity extends AppCompatActivity {
    private ActivityWrongAnswersBinding binding;
    private WrongAnswerViewModel viewModel;
    private WrongAnswerAdapter adapter;

    public static Intent createIntent(Context context) {
        return new Intent(context, WrongAnswerActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWrongAnswersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(WrongAnswerViewModel.class);
        adapter = new WrongAnswerAdapter(item ->
                startActivity(QuizAttemptActivity.createIntent(this, item.getQuizId())));
        binding.wrongRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.wrongRecycler.setAdapter(adapter);
        binding.wrongRetry.setOnClickListener(v -> viewModel.load());
        binding.wrongLoadMore.setOnClickListener(v -> viewModel.loadMore());
        viewModel.getUiStateLiveData().observe(this, this::render);
    }

    private void render(WrongAnswerUiState state) {
        binding.wrongLoading.setVisibility(View.GONE);
        binding.wrongRecycler.setVisibility(View.GONE);
        binding.wrongEmpty.setVisibility(View.GONE);
        binding.wrongError.setVisibility(View.GONE);
        binding.wrongLoadMore.setVisibility(View.GONE);

        if (state instanceof WrongAnswerUiState.Loading) {
            binding.wrongLoading.setVisibility(View.VISIBLE);
        } else if (state instanceof WrongAnswerUiState.Empty) {
            binding.wrongEmpty.setVisibility(View.VISIBLE);
        } else if (state instanceof WrongAnswerUiState.Content) {
            WrongAnswerUiState.Content content = (WrongAnswerUiState.Content) state;
            adapter.submitList(content.getItems());
            binding.wrongSummary.setText(getResources().getQuantityString(
                    R.plurals.wrong_answer_total, content.getTotalItems(), content.getTotalItems()));
            binding.wrongRecycler.setVisibility(View.VISIBLE);
            binding.wrongLoadMore.setVisibility(content.getCanLoadMore() ? View.VISIBLE : View.GONE);
            binding.wrongLoadMore.setEnabled(!content.getLoadingMore());
            binding.wrongLoadMore.setText(content.getLoadingMore()
                    ? R.string.state_loading : R.string.action_load_more);
        } else if (state instanceof WrongAnswerUiState.Failed) {
            binding.wrongError.setVisibility(View.VISIBLE);
            WrongAnswerUiState.Failed failed = (WrongAnswerUiState.Failed) state;
            binding.wrongErrorReference.setVisibility(
                    failed.getRequestId() == null ? View.GONE : View.VISIBLE);
            if (failed.getRequestId() != null) {
                binding.wrongErrorReference.setText(
                        getString(R.string.error_reference, failed.getRequestId()));
            }
        }
    }
}
