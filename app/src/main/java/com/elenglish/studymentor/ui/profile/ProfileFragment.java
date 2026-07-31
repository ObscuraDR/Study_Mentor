package com.elenglish.studymentor.ui.profile;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.FragmentProfileBinding;
import com.elenglish.studymentor.domain.model.AppLocale;
import com.elenglish.studymentor.domain.model.EducationLevel;
import com.elenglish.studymentor.notifications.StudyReminderWorker;
import com.elenglish.studymentor.ui.reminders.ReminderUiState;
import com.elenglish.studymentor.ui.reminders.ReminderViewModel;

import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;
    private ReminderViewModel reminderViewModel;
    private FragmentProfileBinding binding;
    private boolean rendering;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (reminderViewModel != null) reminderViewModel.onPermissionResult(granted);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.displayNameInput.addTextChangedListener(new SimpleTW() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onDisplayNameChange(s.toString());
            }
        });
        binding.dailyGoalInput.addTextChangedListener(new SimpleTW() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rendering) viewModel.onDailyGoalChange(s.toString());
            }
        });

        ArrayAdapter<String> educationAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.profile_education_not_set),
                        getString(R.string.profile_education_beginner),
                        getString(R.string.profile_education_intermediate),
                        getString(R.string.profile_education_advanced)});
        binding.educationSpinner.setAdapter(educationAdapter);
        binding.educationSpinner.setOnItemSelectedListener(new SimpleSelection() {
            @Override public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                if (!rendering) viewModel.onEducationLevelChange(educationFor(position));
            }
        });

        ArrayAdapter<String> localeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.settings_locale_en), getString(R.string.settings_locale_vi)});
        binding.localeSpinner.setAdapter(localeAdapter);
        binding.localeSpinner.setOnItemSelectedListener(new SimpleSelection() {
            @Override public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                if (!rendering) viewModel.onLocaleChange(
                        position == 1 ? AppLocale.Vietnamese : AppLocale.English);
            }
        });

        binding.saveProfileButton.setOnClickListener(v -> viewModel.saveProfile());
        binding.saveSettingsButton.setOnClickListener(v -> viewModel.saveSettings());
        binding.reloadButton.setOnClickListener(v -> viewModel.load());
        binding.signOutButton.setOnClickListener(v -> showSignOutDialog());

        binding.reminderSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) return;
            reminderViewModel.onEnabledChange(checked);
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !StudyReminderWorker.Companion.hasPostPermission(requireContext())) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        binding.reminderTimeButton.setOnClickListener(v -> {
            ReminderUiState current = reminderViewModel.getUiState().getValue();
            int hour = current.getPreference().getHour();
            int minute = current.getPreference().getMinute();
            new TimePickerDialog(requireContext(),
                    (picker, selectedHour, selectedMinute) ->
                            reminderViewModel.onTimeChange(selectedHour, selectedMinute),
                    hour, minute, true).show();
        });

        viewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            rendering = true;
            if (state.getProfile() != null) {
                if (!binding.displayNameInput.getText().toString().equals(state.getDisplayNameInput())) {
                    binding.displayNameInput.setText(state.getDisplayNameInput());
                    binding.displayNameInput.setSelection(state.getDisplayNameInput().length());
                }
                binding.emailText.setText(state.getProfile().getEmail());
            }
            if (!binding.dailyGoalInput.getText().toString().equals(state.getDailyGoalInput())) {
                binding.dailyGoalInput.setText(state.getDailyGoalInput());
            }
            binding.educationSpinner.setSelection(educationPosition(state.getEducationLevelInput()));
            binding.localeSpinner.setSelection(state.getLocaleInput() == AppLocale.Vietnamese ? 1 : 0);
            rendering = false;
            binding.displayNameLayout.setError(state.getDisplayNameError());
            binding.dailyGoalLayout.setError(state.getDailyGoalError());
            binding.saveProfileButton.setEnabled(state.getCanSaveProfile());
            binding.saveSettingsButton.setEnabled(state.getCanSaveSettings());

            if (state.getMessage() != null) {
                ProfileMessage msg = state.getMessage();
                binding.profileSavedText.setVisibility(View.VISIBLE);
                binding.profileSavedText.setText(messageText(msg));
                binding.profileSavedText.setTextColor(requireContext().getColor(
                        msg.isError() ? R.color.feedback_error : R.color.feedback_success));
                binding.reloadButton.setVisibility(msg.getRequiresReload() ? View.VISIBLE : View.GONE);
            } else {
                binding.profileSavedText.setVisibility(View.GONE);
                binding.reloadButton.setVisibility(View.GONE);
            }
        });

        reminderViewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            rendering = true;
            binding.reminderSwitch.setChecked(state.getPreference().getEnabled());
            rendering = false;
            binding.reminderTimeButton.setText(getString(R.string.reminder_time_at,
                    String.format(Locale.getDefault(), "%02d:%02d",
                            state.getPreference().getHour(), state.getPreference().getMinute())));
            binding.reminderPermissionNote.setVisibility(
                    state.getPreference().getEnabled() && !state.getPermissionGranted()
                            ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reminderViewModel.onPermissionStateChanged(
                StudyReminderWorker.Companion.hasPostPermission(requireContext()));
    }

    private CharSequence messageText(ProfileMessage message) {
        int res;
        if (message.getKind() == ProfileMessageKind.ProfileSaved) res = R.string.profile_saved;
        else if (message.getKind() == ProfileMessageKind.SettingsSaved) res = R.string.settings_saved;
        else if (message.getKind() == ProfileMessageKind.RevisionConflict) res = R.string.conflict_reload_required;
        else if (message.getKind() == ProfileMessageKind.NetworkUnavailable) res = R.string.error_network_body;
        else res = R.string.error_generic_body;
        String text = getString(res);
        return message.getRequestId() == null
                ? text : text + "\n" + getString(R.string.error_reference, message.getRequestId());
    }

    private EducationLevel educationFor(int position) {
        if (position == 1) return EducationLevel.Beginner;
        if (position == 2) return EducationLevel.Intermediate;
        if (position == 3) return EducationLevel.Advanced;
        return null;
    }

    private int educationPosition(EducationLevel value) {
        if (value == EducationLevel.Beginner) return 1;
        if (value == EducationLevel.Intermediate) return 2;
        if (value == EducationLevel.Advanced) return 3;
        return 0;
    }

    private void showSignOutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sign_out_confirm_title)
                .setMessage(R.string.sign_out_confirm_body)
                .setPositiveButton(R.string.action_sign_out, (d, which) -> viewModel.signOut())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private abstract static class SimpleTW implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }

    private abstract static class SimpleSelection implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
