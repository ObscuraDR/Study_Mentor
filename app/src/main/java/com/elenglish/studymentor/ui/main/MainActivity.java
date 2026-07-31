package com.elenglish.studymentor.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityMainBinding;
import com.elenglish.studymentor.ui.home.HomeFragment;
import com.elenglish.studymentor.ui.learning.LearnFragment;
import com.elenglish.studymentor.ui.progress.ProgressFragment;
import com.elenglish.studymentor.ui.profile.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements
        HomeFragment.NavigationCallback,
        LearnFragment.SubjectSelectionListener {

    private ActivityMainBinding binding;
    private HomeFragment homeFragment;
    private LearnFragment learnFragment;
    private ProgressFragment progressFragment;
    private ProfileFragment profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            learnFragment = new LearnFragment();
            progressFragment = new ProgressFragment();
            profileFragment = new ProfileFragment();

            homeFragment.setNavigationCallback(this);
            learnFragment.setSelectionListener(this);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, homeFragment, "home")
                    .add(R.id.fragment_container, learnFragment, "learn")
                    .add(R.id.fragment_container, progressFragment, "progress")
                    .add(R.id.fragment_container, profileFragment, "profile")
                    .hide(learnFragment).hide(progressFragment).hide(profileFragment)
                    .show(homeFragment)
                    .commit();
        } else {
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("home");
            learnFragment = (LearnFragment) getSupportFragmentManager().findFragmentByTag("learn");
            progressFragment = (ProgressFragment) getSupportFragmentManager().findFragmentByTag("progress");
            profileFragment = (ProfileFragment) getSupportFragmentManager().findFragmentByTag("profile");
            if (homeFragment != null) homeFragment.setNavigationCallback(this);
            if (learnFragment != null) learnFragment.setSelectionListener(this);
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) showFragment(homeFragment);
            else if (id == R.id.nav_learn) showFragment(learnFragment);
            else if (id == R.id.nav_progress) showFragment(progressFragment);
            else if (id == R.id.nav_tutor) {
                startActivity(com.elenglish.studymentor.ui.tutor.TutorChatActivity
                        .createIntent(this, null));
            }
            else if (id == R.id.nav_profile) showFragment(profileFragment);
            else return false;
            return true;
        });
    }

    private void showFragment(Fragment fragment) {
        if (fragment == null) return;
        getSupportFragmentManager().beginTransaction()
                .hide(homeFragment != null ? homeFragment : fragment)
                .hide(learnFragment != null ? learnFragment : fragment)
                .hide(progressFragment != null ? progressFragment : fragment)
                .hide(profileFragment != null ? profileFragment : fragment)
                .show(fragment)
                .commit();
    }

    @Override
    public void onContinueLearning(String lessonId) {
        startActivity(com.elenglish.studymentor.ui.lesson.LessonDetailActivity
                .createIntent(this, lessonId));
    }

    @Override
    public void onReviewFlashcards(String deckId) {
        startActivity(com.elenglish.studymentor.ui.flashcards.FlashcardReviewActivity
                .createIntent(this, deckId));
    }

    @Override
    public void onSubjectSelected(com.elenglish.studymentor.domain.model.Subject subject) {
        startActivity(com.elenglish.studymentor.ui.learning.TopicsActivity
                .createIntent(this, subject.getId()));
    }

    @Override
    public void onCampaignMapSelected() {
        startActivity(new Intent(this, com.elenglish.studymentor.ui.campaign.CampaignMapActivity.class));
    }
}
