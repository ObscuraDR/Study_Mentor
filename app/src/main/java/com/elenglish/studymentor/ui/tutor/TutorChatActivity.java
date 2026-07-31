package com.elenglish.studymentor.ui.tutor;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.databinding.ActivityTutorChatBinding;
import com.elenglish.studymentor.R;
import com.elenglish.studymentor.data.remote.dto.TutorResponseRequestDto;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TutorChatActivity extends AppCompatActivity {

    private static final String EXTRA_LESSON_ID = "lessonId";
    private TutorViewModel viewModel;
    private ActivityTutorChatBinding binding;
    private ChatMessageAdapter adapter;

    public static Intent createIntent(Context ctx, String lessonId) {
        Intent intent = new Intent(ctx, TutorChatActivity.class);
        intent.putExtra(EXTRA_LESSON_ID, lessonId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTutorChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(TutorViewModel.class);

        adapter = new ChatMessageAdapter();
        binding.chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecycler.setAdapter(adapter);

        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onDraftChange(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.sendButton.setOnClickListener(v -> {
            String text = binding.messageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                viewModel.send();
            }
        });
        binding.retryButton.setOnClickListener(v -> viewModel.send());

        viewModel.getUiStateLiveData().observe(this, state -> {
            if (state == null) return;
            adapter.submitList(state.getTurns());
            if (adapter.getItemCount() > 0) {
                binding.chatRecycler.post(() ->
                        binding.chatRecycler.smoothScrollToPosition(adapter.getItemCount() - 1));
            }
            binding.emptyChat.setVisibility(
                    state.getTurns().isEmpty() ? View.VISIBLE : View.GONE);
            if (!binding.messageInput.getText().toString().equals(state.getDraft())) {
                binding.messageInput.setText(state.getDraft());
                binding.messageInput.setSelection(state.getDraft().length());
            }
            binding.sendButton.setEnabled(state.getCanSend());
            binding.sendButton.setVisibility(state.getSending() ? View.GONE : View.VISIBLE);
            binding.sendingProgress.setVisibility(state.getSending() ? View.VISIBLE : View.GONE);
            binding.charCount.setVisibility(state.getDraftLength() > 0 ? View.VISIBLE : View.GONE);
            binding.charCount.setText(getString(
                    R.string.tutor_char_count,
                    state.getDraftLength(),
                    TutorResponseRequestDto.MAX_MESSAGE_LENGTH));
            binding.failureContainer.setVisibility(
                    state.getFailure() == null ? View.GONE : View.VISIBLE);
            if (state.getFailure() != null) {
                binding.failureText.setText(errorText(state.getFailure().getKind()));
                binding.retryButton.setVisibility(
                        state.getFailure().getCanRetry() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private int errorText(TutorErrorKind kind) {
        if (kind == TutorErrorKind.RateLimited) return R.string.tutor_error_rate_limited;
        if (kind == TutorErrorKind.ProviderTimeout) return R.string.tutor_error_timeout;
        if (kind == TutorErrorKind.ProviderUnavailable) return R.string.tutor_error_provider;
        return R.string.error_generic_body;
    }
}
