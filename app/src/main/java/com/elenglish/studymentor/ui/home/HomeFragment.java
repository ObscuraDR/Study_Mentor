package com.elenglish.studymentor.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.CardFlashcardsDueBinding;
import com.elenglish.studymentor.databinding.CardNothingElseBinding;
import com.elenglish.studymentor.databinding.CardPrimaryActionBinding;
import com.elenglish.studymentor.databinding.CardProgressSectionBinding;
import com.elenglish.studymentor.databinding.FragmentHomeBinding;
import com.elenglish.studymentor.databinding.SectionHomeGreetingBinding;
import com.elenglish.studymentor.domain.model.ProgressProjection;
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind;

import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Home dashboard fragment.
 *
 * Displays three independently loading sections:
 * 1. Greeting header from user profile
 * 2. Progress overview
 * 3. Continue Learning action
 * 4. Flashcards due (secondary)
 *
 * A failure in one section never hides another that already succeeded.
 */
@AndroidEntryPoint
public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;
    private FragmentHomeBinding rootBinding;
    private SectionHomeGreetingBinding greetingBinding;
    private CardProgressSectionBinding progressBinding;
    private CardPrimaryActionBinding continueLearningBinding;
    private CardFlashcardsDueBinding flashcardsBinding;
    private CardNothingElseBinding nothingElseBinding;
    private NavigationCallback navigationCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootBinding = FragmentHomeBinding.inflate(inflater, container, false);

        // Inflate included sub-layouts into their containers
        View progressView = inflater.inflate(R.layout.card_progress_section,
                rootBinding.progressSectionContainer, true);
        progressBinding = CardProgressSectionBinding.bind(progressView);

        View continueView = inflater.inflate(R.layout.card_primary_action,
                rootBinding.continueLearningContainer, true);
        continueLearningBinding = CardPrimaryActionBinding.bind(continueView);

        View flashcardsView = inflater.inflate(R.layout.card_flashcards_due,
                rootBinding.flashcardsDueContainer, true);
        flashcardsBinding = CardFlashcardsDueBinding.bind(flashcardsView);

        View nothingView = inflater.inflate(R.layout.card_nothing_else,
                rootBinding.nothingElseContainer, true);
        nothingElseBinding = CardNothingElseBinding.bind(nothingView);

        // Greeting section is already included via <include> in fragment_home.xml
        greetingBinding = SectionHomeGreetingBinding.bind(rootBinding.getRoot());

        return rootBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        homeViewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), this::renderState);
    }

    public void setNavigationCallback(NavigationCallback callback) {
        this.navigationCallback = callback;
    }

    /**
     * Renders the entire home state. Each section is independently updated.
     */
    private void renderState(HomeUiState state) {
        if (state == null) return;

        renderGreeting(state);

        // ProgressSectionState is a sealed interface; instanceof dispatch
        ProgressSectionState progressState = state.getProgress();
        renderProgress(progressState);

        // ContinueLearningState
        ContinueLearningState continueState = state.getContinueLearning();
        renderContinueLearning(continueState);

        // FlashcardsDueState
        FlashcardsDueState flashcardsState = state.getFlashcardsDue();
        renderFlashcardsDue(flashcardsState);

        // "Nothing else" shown only when both optional cards are unavailable
        boolean nothingElse = (continueState instanceof ContinueLearningState.Unavailable)
                && (flashcardsState instanceof FlashcardsDueState.Unavailable);
        nothingElseBinding.nothingElseCard.setVisibility(nothingElse ? View.VISIBLE : View.GONE);
    }

    // ── Greeting ────────────────────────────────────────────────────────────

    private void renderGreeting(HomeUiState state) {
        String displayName = state.getDisplayName();

        // Check if this is a new learner: totalXp == 0 && completedLessons == 0
        boolean isNewLearner = false;
        if (state.getProgress() instanceof ProgressSectionState.Content) {
            ProgressSectionState.Content content = (ProgressSectionState.Content) state.getProgress();
            ProgressProjection pp = content.getProgress();
            if (pp != null && pp.getTotalXp() == 0 && pp.getCompletedLessons() == 0) {
                isNewLearner = true;
            }
        }

        if (displayName != null) {
            greetingBinding.greetingTitle.setText(
                    getString(R.string.home_greeting_named, displayName));
        } else if (isNewLearner) {
            greetingBinding.greetingTitle.setText(R.string.home_greeting_new);
        } else {
            greetingBinding.greetingTitle.setText(R.string.home_greeting_returning);
        }

        greetingBinding.greetingSubtitle.setText(
                isNewLearner ? R.string.home_subtitle_new : R.string.home_subtitle_returning);
    }

    // ── Progress section ────────────────────────────────────────────────────

    private void renderProgress(ProgressSectionState state) {
        if (state instanceof ProgressSectionState.Loading) {
            showProgressLoading();
        } else if (state instanceof ProgressSectionState.Content) {
            ProgressSectionState.Content content = (ProgressSectionState.Content) state;
            renderProgressContent(content.getProgress());
        } else if (state instanceof ProgressSectionState.Failed) {
            ProgressSectionState.Failed failed = (ProgressSectionState.Failed) state;
            renderProgressError(failed);
        }
    }

    private void showProgressLoading() {
        progressBinding.progressLoading.setVisibility(View.VISIBLE);
        progressBinding.progressHeroRow.setVisibility(View.GONE);
        progressBinding.completionBar.setVisibility(View.GONE);
        progressBinding.completionPercentage.setVisibility(View.GONE);
        progressBinding.progressErrorRow.setVisibility(View.GONE);
    }

    private void renderProgressContent(ProgressProjection progress) {
        progressBinding.progressLoading.setVisibility(View.GONE);
        progressBinding.progressErrorRow.setVisibility(View.GONE);
        progressBinding.progressHeroRow.setVisibility(View.VISIBLE);
        progressBinding.completionBar.setVisibility(View.VISIBLE);
        progressBinding.completionPercentage.setVisibility(View.VISIBLE);

        // XP figure
        progressBinding.progressTotalXp.setText(String.valueOf(progress.getTotalXp()));

        // Completion bar (percentage 0-100 mapped to 0-1000)
        int barValue = (int) (progress.getCompletionPercentage() * 10);
        progressBinding.completionBar.setProgress(Math.min(barValue, 1000));

        // Percentage text
        progressBinding.completionPercentage.setText(
                getString(R.string.progress_percentage, progress.getCompletionPercentage()));

        // Compact stats
        progressBinding.statLessons.setText(
                getString(R.string.progress_ratio,
                        progress.getCompletedLessons(), progress.getTotalLessons()));
        progressBinding.statTopics.setText(
                getString(R.string.progress_ratio,
                        progress.getCompletedTopics(), progress.getTotalTopics()));
        progressBinding.statSubjects.setText(
                getString(R.string.progress_ratio,
                        progress.getCompletedSubjects(), progress.getTotalSubjects()));
        progressBinding.statStudyTime.setText(
                getString(R.string.progress_minutes, progress.getLearningTimeSeconds() / 60));
    }

    private void renderProgressError(ProgressSectionState.Failed failed) {
        progressBinding.progressLoading.setVisibility(View.GONE);
        progressBinding.progressHeroRow.setVisibility(View.GONE);
        progressBinding.completionBar.setVisibility(View.GONE);
        progressBinding.completionPercentage.setVisibility(View.GONE);
        progressBinding.progressErrorRow.setVisibility(View.VISIBLE);

        CatalogErrorKind kind = failed.getKind();
        progressBinding.progressErrorText.setText(bodyResForKind(kind));

        String requestId = failed.getRequestId();
        if (requestId != null) {
            progressBinding.progressErrorRef.setVisibility(View.VISIBLE);
            progressBinding.progressErrorRef.setText(
                    getString(R.string.error_reference, requestId));
        } else {
            progressBinding.progressErrorRef.setVisibility(View.GONE);
        }

        progressBinding.progressRetryButton.setOnClickListener(v -> homeViewModel.retryProgress());
    }

    // ── Continue Learning section ───────────────────────────────────────────

    private void renderContinueLearning(ContinueLearningState state) {
        if (state instanceof ContinueLearningState.Loading) {
            continueLearningBinding.primaryActionCard.setVisibility(View.VISIBLE);
            continueLearningBinding.lessonActionRow.setVisibility(View.GONE);
            continueLearningBinding.actionLoadingRow.setVisibility(View.VISIBLE);
            continueLearningBinding.actionErrorRow.setVisibility(View.GONE);
        } else if (state instanceof ContinueLearningState.Available) {
            ContinueLearningState.Available available = (ContinueLearningState.Available) state;
            continueLearningBinding.primaryActionCard.setVisibility(View.VISIBLE);
            continueLearningBinding.lessonActionRow.setVisibility(View.VISIBLE);
            continueLearningBinding.actionLoadingRow.setVisibility(View.GONE);
            continueLearningBinding.actionErrorRow.setVisibility(View.GONE);

            continueLearningBinding.lessonTitle.setText(available.getLessonTitle());
            continueLearningBinding.lessonActionRow.setOnClickListener(v -> {
                if (navigationCallback != null) {
                    navigationCallback.onContinueLearning(available.getLessonId());
                }
            });
        } else if (state instanceof ContinueLearningState.Failed) {
            ContinueLearningState.Failed failed = (ContinueLearningState.Failed) state;
            continueLearningBinding.primaryActionCard.setVisibility(View.VISIBLE);
            continueLearningBinding.lessonActionRow.setVisibility(View.GONE);
            continueLearningBinding.actionLoadingRow.setVisibility(View.GONE);
            continueLearningBinding.actionErrorRow.setVisibility(View.VISIBLE);
            continueLearningBinding.actionErrorText.setText(bodyResForKind(failed.getKind()));

            String requestId = failed.getRequestId();
            if (requestId != null) {
                continueLearningBinding.actionErrorRef.setVisibility(View.VISIBLE);
                continueLearningBinding.actionErrorRef.setText(
                        getString(R.string.error_reference, requestId));
            } else {
                continueLearningBinding.actionErrorRef.setVisibility(View.GONE);
            }
            continueLearningBinding.actionRetryButton.setOnClickListener(
                    v -> homeViewModel.retryContinueLearning());
        } else {
            // Unavailable — no card shown
            continueLearningBinding.primaryActionCard.setVisibility(View.GONE);
        }
    }

    // ── Flashcards Due section ──────────────────────────────────────────────

    private void renderFlashcardsDue(FlashcardsDueState state) {
        if (state instanceof FlashcardsDueState.Available) {
            FlashcardsDueState.Available available = (FlashcardsDueState.Available) state;
            flashcardsBinding.flashcardsCard.setVisibility(View.VISIBLE);
            flashcardsBinding.flashcardsActionRow.setVisibility(View.VISIBLE);
            flashcardsBinding.flashcardsErrorRow.setVisibility(View.GONE);

            flashcardsBinding.flashcardsDeckName.setText(available.getDeckName());
            int dueCount = available.getDueCount();
            flashcardsBinding.flashcardsDueCount.setText(
                    getResources().getQuantityString(R.plurals.flashcards_card_count, dueCount, dueCount));
            flashcardsBinding.flashcardsActionRow.setOnClickListener(v -> {
                if (navigationCallback != null) {
                    navigationCallback.onReviewFlashcards(available.getDeckId());
                }
            });
        } else if (state instanceof FlashcardsDueState.Failed) {
            FlashcardsDueState.Failed failed = (FlashcardsDueState.Failed) state;
            flashcardsBinding.flashcardsCard.setVisibility(View.VISIBLE);
            flashcardsBinding.flashcardsActionRow.setVisibility(View.GONE);
            flashcardsBinding.flashcardsErrorRow.setVisibility(View.VISIBLE);
            flashcardsBinding.flashcardsErrorText.setText(bodyResForKind(failed.getKind()));

            String requestId = failed.getRequestId();
            if (requestId != null) {
                flashcardsBinding.flashcardsErrorRef.setVisibility(View.VISIBLE);
                flashcardsBinding.flashcardsErrorRef.setText(
                        getString(R.string.error_reference, requestId));
            } else {
                flashcardsBinding.flashcardsErrorRef.setVisibility(View.GONE);
            }
            flashcardsBinding.flashcardsRetryButton.setOnClickListener(
                    v -> homeViewModel.retryFlashcardsDue());
        } else {
            // Loading or Unavailable — no card shown
            flashcardsBinding.flashcardsCard.setVisibility(View.GONE);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private int bodyResForKind(CatalogErrorKind kind) {
        if (kind == CatalogErrorKind.Network) return R.string.error_network_body;
        if (kind == CatalogErrorKind.NotFound) return R.string.catalog_not_found_body;
        if (kind == CatalogErrorKind.Unauthorized) return R.string.catalog_unauthorized_body;
        return R.string.error_generic_body;
    }

    /** Callback interface for the host to handle navigation actions. */
    public interface NavigationCallback {
        void onContinueLearning(String lessonId);
        void onReviewFlashcards(String deckId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootBinding = null;
        greetingBinding = null;
        progressBinding = null;
        continueLearningBinding = null;
        flashcardsBinding = null;
        nothingElseBinding = null;
    }
}
