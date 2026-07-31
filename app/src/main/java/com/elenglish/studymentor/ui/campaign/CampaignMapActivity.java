package com.elenglish.studymentor.ui.campaign;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityCampaignMapBinding;
import com.elenglish.studymentor.domain.model.CampaignProjection;
import com.elenglish.studymentor.ui.lesson.LessonDetailActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CampaignMapActivity extends AppCompatActivity {

    private CampaignViewModel viewModel;
    private ActivityCampaignMapBinding binding;
    private CampaignNodeAdapter adapter;

    public static Intent createIntent(Context ctx) {
        return new Intent(ctx, CampaignMapActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCampaignMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(CampaignViewModel.class);

        adapter = new CampaignNodeAdapter(lessonId ->
                startActivity(LessonDetailActivity.createIntent(this, lessonId)));
        binding.campaignRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.campaignRecycler.setAdapter(adapter);
        binding.campaignRetry.setOnClickListener(v -> viewModel.load());
        binding.openBossChallenge.setOnClickListener(v ->
                startActivity(BossChallengeActivity.createIntent(this)));
        binding.openEconomy.setOnClickListener(v ->
                startActivity(EconomyActivity.createIntent(this)));

        viewModel.getUiStateLiveData().observe(this, state -> {
            binding.campaignLoading.setVisibility(View.GONE);
            binding.campaignRecycler.setVisibility(View.GONE);
            binding.emptyCampaign.setVisibility(View.GONE);
            binding.campaignError.setVisibility(View.GONE);
            if (state instanceof CampaignUiState.Content) {
                CampaignProjection cp = ((CampaignUiState.Content) state).getCampaign();
                adapter.submit(cp);
                binding.campaignRecycler.setVisibility(View.VISIBLE);
                binding.campaignProgressText.setText(
                        getString(R.string.progress_ratio, cp.getCompletedLessons(), cp.getTotalLessons()));
                binding.campaignProgressBar.setProgress(
                        cp.getTotalLessons() > 0 ? (cp.getCompletedLessons() * 100 / cp.getTotalLessons()) : 0);
            } else if (state instanceof CampaignUiState.Empty) {
                binding.emptyCampaign.setVisibility(View.VISIBLE);
            } else if (state instanceof CampaignUiState.Failed) {
                binding.campaignError.setVisibility(View.VISIBLE);
            } else if (state instanceof CampaignUiState.Loading) {
                binding.campaignLoading.setVisibility(View.VISIBLE);
            }
        });
    }
}
