package com.elenglish.studymentor.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityAuthBinding;
import com.elenglish.studymentor.ui.forgotpassword.ForgotPasswordActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AuthActivity extends AppCompatActivity {

    private AuthViewModel viewModel;
    private ActivityAuthBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupInputs();
        setupButtons();

        viewModel.getUiStateLiveData().observe(this, state -> {
            if (state == null) return;

            // Mode-dependent UI
            boolean isRegister = state.getMode() == AuthMode.Register;
            binding.displayNameLayout.setVisibility(isRegister ? View.VISIBLE : View.GONE);
            binding.authSubtitle.setText(isRegister
                    ? R.string.auth_register_subtitle : R.string.auth_sign_in_subtitle);
            binding.authSubmit.setText(isRegister
                    ? R.string.auth_action_register : R.string.auth_action_sign_in);
            binding.modeToggle.setText(isRegister
                    ? R.string.auth_switch_to_sign_in : R.string.auth_switch_to_register);
            binding.forgotPasswordLink.setVisibility(isRegister ? View.GONE : View.VISIBLE);

            // Errors
            binding.displayNameLayout.setError(state.getDisplayNameError());
            binding.emailLayout.setError(state.getEmailError());
            binding.passwordLayout.setError(state.getPasswordError());

            // Submit button
            binding.authSubmit.setEnabled(state.getCanSubmit());
            binding.authSubmit.setText(state.getSubmitting()
                    ? getString(R.string.state_loading) : getString(isRegister
                    ? R.string.auth_action_register : R.string.auth_action_sign_in));

            // Failure message
            if (state.getFailure() != null) {
                binding.authFailure.setText(state.getFailure().getMessage());
                binding.authFailure.setVisibility(View.VISIBLE);
            } else {
                binding.authFailure.setVisibility(View.GONE);
            }
        });
    }

    private void setupInputs() {
        binding.displayNameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onDisplayNameChange(s.toString());
            }
        });
        binding.emailInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onEmailChange(s.toString());
            }
        });
        binding.passwordInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onPasswordChange(s.toString());
            }
        });
    }

    private void setupButtons() {
        binding.authSubmit.setOnClickListener(v -> viewModel.submit());
        binding.modeToggle.setOnClickListener(v -> {
            AuthUiState s = viewModel.getUiStateLiveData().getValue();
            if (s != null) {
                viewModel.setMode(s.getMode() == AuthMode.SignIn
                        ? AuthMode.Register : AuthMode.SignIn);
            }
        });
        binding.forgotPasswordLink.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
