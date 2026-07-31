package com.elenglish.studymentor.ui.flashcards;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.databinding.ActivityFlashcardListBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FlashcardListActivity extends AppCompatActivity {

    private static final String EXTRA_LESSON_ID = "lessonId";
    private ActivityFlashcardListBinding binding;
    private FlashcardDeckListViewModel viewModel;
    private FlashcardDeckAdapter adapter;

    public static Intent createIntent(Context ctx, String lessonId) {
        Intent intent = new Intent(ctx, FlashcardListActivity.class);
        intent.putExtra(EXTRA_LESSON_ID, lessonId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(FlashcardDeckListViewModel.class);
        adapter = new FlashcardDeckAdapter(deck -> startActivity(
                FlashcardReviewActivity.createIntent(this, deck.getId())));
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(viewModel::load);
        binding.errorRetry.setOnClickListener(v -> viewModel.load());

        viewModel.getUiStateLiveData().observe(this, state -> {
            hideStates();
            binding.swipeRefresh.setRefreshing(false);
            if (state instanceof FlashcardDeckListUiState.Content) {
                adapter.submitList(((FlashcardDeckListUiState.Content) state).getDecks());
                binding.recycler.setVisibility(android.view.View.VISIBLE);
            } else if (state instanceof FlashcardDeckListUiState.Empty) {
                binding.emptyState.setVisibility(android.view.View.VISIBLE);
            } else if (state instanceof FlashcardDeckListUiState.Loading) {
                binding.loading.setVisibility(android.view.View.VISIBLE);
            } else if (state instanceof FlashcardDeckListUiState.Failed) {
                binding.errorState.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    private void hideStates() {
        binding.recycler.setVisibility(android.view.View.GONE);
        binding.emptyState.setVisibility(android.view.View.GONE);
        binding.errorState.setVisibility(android.view.View.GONE);
        binding.loading.setVisibility(android.view.View.GONE);
    }
}
