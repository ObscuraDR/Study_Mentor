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
import com.elenglish.studymentor.domain.model.Topic;
import com.elenglish.studymentor.ui.adapters.TopicAdapter;
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind;
import com.elenglish.studymentor.ui.catalog.CatalogUiState;
import com.elenglish.studymentor.ui.catalog.TopicsViewModel;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Topics fragment — shows all topics within a selected subject.
 *
 * The subject ID is passed via the fragment arguments bundle.
 */
@AndroidEntryPoint
public class TopicsFragment extends Fragment {

    private static final String ARG_SUBJECT_ID = "subjectId";

    private TopicsViewModel viewModel;
    private FragmentLearnBinding binding;
    private TopicAdapter adapter;
    private TopicSelectionListener selectionListener;

    public static TopicsFragment newInstance(String subjectId) {
        TopicsFragment fragment = new TopicsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SUBJECT_ID, subjectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TopicsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLearnBinding.inflate(inflater, container, false);

        // Hide campaign entry button for topics level
        binding.campaignEntryButton.setVisibility(View.GONE);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Context line
        binding.contextLine.setText(R.string.catalog_topics_context);

        // Setup RecyclerView
        adapter = new TopicAdapter(topic -> {
            if (selectionListener != null) {
                selectionListener.onTopicSelected(topic);
            }
        });

        binding.subjectsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.subjectsRecycler.setAdapter(adapter);

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.load();
            binding.swipeRefresh.setRefreshing(false);
        });

        // Observe state
        viewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), this::renderState);

        // Observe subject name for context
        viewModel.getSubjectNameLiveData().observe(getViewLifecycleOwner(), name -> {
            if (name != null && !name.isEmpty()) {
                binding.contextLine.setText(
                        getString(R.string.catalog_topics_context) + " — " + name);
            }
        });
    }

    public void setSelectionListener(TopicSelectionListener listener) {
        this.selectionListener = listener;
    }

    private void renderState(CatalogUiState<Topic> state) {
        hideAllStates();

        if (state instanceof CatalogUiState.Loading) {
            binding.loadingIndicator.setVisibility(View.VISIBLE);
        } else if (state instanceof CatalogUiState.Content) {
            CatalogUiState.Content<Topic> contentState = (CatalogUiState.Content<Topic>) state;
            List<Topic> items = contentState.getItems();
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

    private void showEmpty(com.elenglish.studymentor.domain.model.DataOrigin origin) {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyTitle.setText(R.string.catalog_topics_empty_title);
        binding.emptyDescription.setText(R.string.catalog_topics_empty_body);
    }

    private void showError(CatalogUiState.Failed failed) {
        binding.errorState.setVisibility(View.VISIBLE);

        int[] texts = errorTextsForKind(failed.getKind());
        binding.errorTitle.setText(texts[0]);
        binding.errorDescription.setText(texts[1]);

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

    private int[] errorTextsForKind(CatalogErrorKind kind) {
        if (kind == CatalogErrorKind.Network) {
            return new int[]{R.string.error_network_title, R.string.error_network_body};
        } else if (kind == CatalogErrorKind.NotFound) {
            return new int[]{R.string.catalog_not_found_title, R.string.catalog_not_found_body};
        } else if (kind == CatalogErrorKind.Unauthorized) {
            return new int[]{R.string.catalog_unauthorized_title, R.string.catalog_unauthorized_body};
        } else {
            return new int[]{R.string.error_generic_title, R.string.error_generic_body};
        }
    }

    public interface TopicSelectionListener {
        void onTopicSelected(Topic topic);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
