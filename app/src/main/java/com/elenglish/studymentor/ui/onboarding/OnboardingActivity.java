package com.elenglish.studymentor.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityOnboardingBinding;
import com.elenglish.studymentor.databinding.PageOnboardingBinding;
import com.elenglish.studymentor.ui.auth.AuthActivity;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private ViewPager2 viewPager;
    private final List<ImageView> dots = new ArrayList<>();
    private static final String PREF_ONBOARDING = "studymentor_onboarding";
    private static final String KEY_DONE = "done";

    private static final int[][] PAGES = {
        {R.string.onboarding_page1_title, R.string.onboarding_page1_body, R.drawable.ic_menu_book},
        {R.string.onboarding_page2_title, R.string.onboarding_page2_body, R.drawable.ic_style},
        {R.string.onboarding_page3_title, R.string.onboarding_page3_body, R.drawable.ic_emoji_events},
    };

    public static boolean isDone(Context ctx) {
        return ctx.getSharedPreferences(PREF_ONBOARDING, MODE_PRIVATE)
                .getBoolean(KEY_DONE, false);
    }

    public static void markDone(Context ctx) {
        ctx.getSharedPreferences(PREF_ONBOARDING, MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, true).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewPager = binding.onboardingPager;
        viewPager.setAdapter(new PageAdapter());

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int pos) { updateUI(pos); }
        });

        for (int i = 0; i < PAGES.length; i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(24, 24);
            lp.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(lp);
            binding.dotsIndicator.addView(dot);
            dots.add(dot);
        }
        updateDots(0);

        binding.onboardingSkip.setOnClickListener(v -> finishOnboarding());
        binding.onboardingAction.setOnClickListener(v -> {
            int cur = viewPager.getCurrentItem();
            if (cur < PAGES.length - 1) viewPager.setCurrentItem(cur + 1, true);
            else finishOnboarding();
        });
    }

    private void updateUI(int pos) {
        updateDots(pos);
        boolean last = pos == PAGES.length - 1;
        binding.onboardingAction.setText(last ? R.string.onboarding_get_started : R.string.onboarding_next);
        binding.onboardingSkip.setVisibility(last ? View.GONE : View.VISIBLE);
    }

    private void updateDots(int pos) {
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setImageResource(i == pos
                    ? R.drawable.ic_circle_filled : R.drawable.ic_circle_outline);
        }
    }

    private void finishOnboarding() {
        markDone(this);
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageViewHolder> {
        @NonNull @Override public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            return new PageViewHolder(PageOnboardingBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override public void onBindViewHolder(@NonNull PageViewHolder h, int pos) {
            int[] page = PAGES[pos];
            h.b.pageTitle.setText(page[0]);
            h.b.pageBody.setText(page[1]);
            h.b.pageIcon.setImageResource(page[2]);
        }
        @Override public int getItemCount() { return PAGES.length; }
    }

    private static class PageViewHolder extends RecyclerView.ViewHolder {
        final PageOnboardingBinding b;
        PageViewHolder(PageOnboardingBinding b) { super(b.getRoot()); this.b = b; }
    }
}
