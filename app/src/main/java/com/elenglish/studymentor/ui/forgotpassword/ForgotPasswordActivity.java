package com.elenglish.studymentor.ui.forgotpassword;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityForgotPwdBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ForgotPasswordActivity extends AppCompatActivity {

    private ForgotPasswordViewModel viewModel;
    private ActivityForgotPwdBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPwdBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);

        binding.emailInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onEmailChange(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.sendResetButton.setOnClickListener(v -> viewModel.submit());

        viewModel.getUiStateLiveData().observe(this, state -> {
            if (state == null) return;
            binding.sendResetButton.setEnabled(state.getCanSubmit());
            binding.sendResetButton.setText(state.getSubmitting()
                    ? R.string.state_loading : R.string.forgot_password_action);
            if (state.getSubmitted()) {
                binding.successMessage.setVisibility(View.VISIBLE);
                binding.sendResetButton.setVisibility(View.GONE);
            }
            if (state.getFailure() != null) {
                Toast.makeText(this, state.getFailure(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
