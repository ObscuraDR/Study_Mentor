package com.elenglish.studymentor.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.appcompat.app.AppCompatActivity;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 1500L;
    private static final int FADE_DURATION_MS = 600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Animation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(FADE_DURATION_MS);
        fadeIn.setFillAfter(true);
        binding.splashAppName.startAnimation(fadeIn);
        binding.splashTagline.startAnimation(fadeIn);

        new Handler(Looper.getMainLooper()).postDelayed(() -> navigateToNext(), SPLASH_DURATION_MS);
    }

    private void navigateToNext() {
        Intent intent = new Intent(this, com.elenglish.studymentor.ui.auth.AuthActivity.class);
        startActivity(intent);
        finish();
    }
}
