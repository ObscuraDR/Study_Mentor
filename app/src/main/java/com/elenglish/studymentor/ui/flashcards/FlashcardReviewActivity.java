package com.elenglish.studymentor.ui.flashcards;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityFlashcardReviewBinding;
import com.elenglish.studymentor.domain.model.ReviewOutcome;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FlashcardReviewActivity extends AppCompatActivity {

    private static final String EXTRA_DECK_ID = "deckId";
    private FlashcardReviewViewModel viewModel;
    private ActivityFlashcardReviewBinding binding;

    public static Intent createIntent(Context ctx, String deckId) {
        Intent intent = new Intent(ctx, FlashcardReviewActivity.class);
        intent.putExtra(EXTRA_DECK_ID, deckId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(FlashcardReviewViewModel.class);

        binding.flashcard.setOnClickListener(v -> viewModel.reveal());
        binding.revealButton.setOnClickListener(v -> viewModel.reveal());
        binding.knownButton.setOnClickListener(v -> viewModel.answer(ReviewOutcome.Known));
        binding.forgotButton.setOnClickListener(v -> viewModel.answer(ReviewOutcome.Forgot));
        binding.errorRetry.setOnClickListener(v -> viewModel.load());

        viewModel.getUiStateLiveData().observe(this, state -> {
            hideStates();
            if (state instanceof FlashcardReviewUiState.Reviewing) {
                FlashcardReviewUiState.Reviewing r = (FlashcardReviewUiState.Reviewing) state;
                String text = r.getRevealed()
                        ? r.getCurrent().getCard().getBack()
                        : r.getCurrent().getCard().getFront();
                binding.cardText.setText(text);
                binding.cardSideLabel.setText(r.getRevealed()
                        ? R.string.flashcards_back : R.string.flashcards_front);
                binding.cardState.setText(getString(
                        R.string.flashcards_box, r.getCurrent().getState().getBox()));
                binding.progressText.setText(getString(
                        R.string.flashcards_progress, r.getIndex() + 1, r.getQueue().size()));
                binding.progressBar.setProgress(
                        ((r.getIndex() + 1) * 100) / Math.max(r.getQueue().size(), 1));
                binding.answerButtons.setVisibility(r.getRevealed() ? View.VISIBLE : View.GONE);
                binding.revealButton.setVisibility(r.getRevealed() ? View.GONE : View.VISIBLE);
                binding.flashcard.setVisibility(View.VISIBLE);
                binding.progressRow.setVisibility(View.VISIBLE);
            } else if (state instanceof FlashcardReviewUiState.Done) {
                FlashcardReviewUiState.Done d = (FlashcardReviewUiState.Done) state;
                binding.doneSection.setVisibility(View.VISIBLE);
                binding.doneScore.setText(getResources().getQuantityString(
                        R.plurals.flashcards_session_score,
                        d.getKnown(), d.getKnown(), d.getReviewed()));
            } else if (state instanceof FlashcardReviewUiState.Loading) {
                binding.loading.setVisibility(View.VISIBLE);
            } else if (state instanceof FlashcardReviewUiState.Failed) {
                binding.errorState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideStates() {
        binding.progressRow.setVisibility(View.GONE);
        binding.flashcard.setVisibility(View.GONE);
        binding.answerButtons.setVisibility(View.GONE);
        binding.revealButton.setVisibility(View.GONE);
        binding.doneSection.setVisibility(View.GONE);
        binding.loading.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
    }
}
