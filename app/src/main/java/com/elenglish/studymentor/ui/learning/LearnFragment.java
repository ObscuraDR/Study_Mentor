package com.elenglish.studymentor.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.FragmentLearnBinding;
import com.elenglish.studymentor.domain.model.CatalogData;
import com.elenglish.studymentor.domain.model.DataOrigin;
import com.elenglish.studymentor.domain.model.Subject;
import com.elenglish.studymentor.ui.adapters.SubjectAdapter;
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind;
import com.elenglish.studymentor.ui.catalog.CatalogUiState;
import com.elenglish.studymentor.ui.catalog.SubjectsViewModel;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Learn fragment — entry point of the learning catalog.
 *
 * Displays subjects in a RecyclerView, each tappable to navigate to its topics.
 * Also provides a Campaign Map entry button at the top of the list.
 */
@AndroidEntryPoint
public class LearnFragment extends Fragment {

    private SubjectsViewModel viewModel;
    private FragmentLearnBinding binding;
    private SubjectAdapter adapter;
    private SubjectSelectionListener selectionListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SubjectsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLearnBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup RecyclerView
        adapter = new SubjectAdapter(subject -> {
            if (selectionListener != null) {
                selectionListener.onSubjectSelected(subject);
            }
        });

        binding.subjectsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.subjectsRecycler.setAdapter(adapter);

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.load();
            binding.swipeRefresh.setRefreshing(false);
        });

        // Campaign map button
        binding.campaignEntryButton.setOnClickListener(v -> {
            if (selectionListener != null) {
                selectionListener.onCampaignMapSelected();
            }
        });

        // Observe ViewModel
        viewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), this::renderState);
    }

    public void setSelectionListener(SubjectSelectionListener listener) {
        this.selectionListener = listener;
    }

    private void renderState(CatalogUiState<Subject> state) {
        hideAllStates();

        if (state instanceof CatalogUiState.Loading) {
            binding.loadingIndicator.setVisibility(View.VISIBLE);
        } else if (state instanceof CatalogUiState.Content) {
            CatalogUiState.Content<Subject> contentState = (CatalogUiState.Content<Subject>) state;
            List<Subject> items = contentState.getItems();
            if (items.isEmpty()) {
                showEmpty(contentState.getOrigin());
            } else {
                binding.subjectsRecycler.setVisibility(View.VISIBLE);
                binding.swipeRefresh.setVisibility(View.VISIBLE);
                adapter.submitList(items);
            }
        } else if (state instanceof CatalogUiState.Empty) {
            CatalogUiState.Empty emptyState = (CatalogUiState.Empty) state;
            showEmpty(emptyState.getOrigin());
        } else if (state instanceof CatalogUiState.Failed) {
            CatalogUiState.Failed failedState = (CatalogUiState.Failed) state;
            showError(failedState);
        }
    }

    private void showEmpty(DataOrigin origin) {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyTitle.setText(R.string.catalog_subjects_empty_title);
        binding.emptyDescription.setText(R.string.catalog_subjects_empty_body);
    }

    private void showError(CatalogUiState.Failed failed) {
        binding.errorState.setVisibility(View.VISIBLE);

        int titleRes, bodyRes;
        switch (failed.getKind()) {
            case Network:
                titleRes = R.string.error_network_title;
                bodyRes = R.string.error_network_body;
                break;
            case NotFound:
                titleRes = R.string.catalog_not_found_title;
                bodyRes = R.string.catalog_not_found_body;
                break;
            case Unauthorized:
                titleRes = R.string.catalog_unauthorized_title;
                bodyRes = R.string.catalog_unauthorized_body;
                break;
            default:
                titleRes = R.string.error_generic_title;
                bodyRes = R.string.error_generic_body;
                break;
        }

        binding.errorTitle.setText(titleRes);
        binding.errorDescription.setText(bodyRes);

        String requestId = failed.getRequestId();
        if (requestId != null) {
            binding.errorReference.setVisibility(View.VISIBLE);
            binding.errorReference.setText(getString(R.string.error_reference, requestId));
        } else {
            binding.errorReference.setVisibility(View.GONE);
        }

        binding.errorRetryButton.setOnClickListener(v -> viewModel.load());
    }

    private void hideAllStates() {
        binding.loadingIndicator.setVisibility(View.GONE);
        binding.subjectsRecycler.setVisibility(View.GONE);
        binding.swipeRefresh.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
    }

    /**
     * Callback interface for the host to handle subject/campaign navigation.
     */
    public interface SubjectSelectionListener {
        void onSubjectSelected(Subject subject);
        void onCampaignMapSelected();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
