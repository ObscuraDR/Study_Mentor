package com.elenglish.studymentor.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.account.AccountRepository
import com.elenglish.studymentor.data.remote.dto.PatchValue
import com.elenglish.studymentor.data.session.SessionRepository
import com.elenglish.studymentor.domain.model.AppLocale
import com.elenglish.studymentor.domain.model.EducationLevel
import com.elenglish.studymentor.domain.model.SharedSettings
import com.elenglish.studymentor.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Categories of outcome the screen can report. Wording lives in resources. */
enum class ProfileMessageKind {
    ProfileSaved,
    SettingsSaved,
    NetworkUnavailable,
    RevisionConflict,
    Generic,
}

/** How the screen should be rendered. */
sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Failed(val kind: ProfileMessageKind, val requestId: String?) : ProfileLoadState
    data object Loaded : ProfileLoadState
}

/**
 * One-shot message for the user.
 *
 * [requiresReload] marks a revision conflict: another device changed the record,
 * so the local copy must be re-read before saving again. The client never
 * force-overwrites the server's version.
 */
data class ProfileMessage(
    val kind: ProfileMessageKind,
    val requestId: String? = null,
    val isError: Boolean = false,
    val requiresReload: Boolean = false,
)

data class ProfileUiState(
    val loadState: ProfileLoadState = ProfileLoadState.Loading,
    val profile: UserProfile? = null,
    val settings: SharedSettings? = null,
    val displayNameInput: String = "",
    val educationLevelInput: EducationLevel? = null,
    val localeInput: AppLocale = AppLocale.English,
    val dailyGoalInput: String = "",
    val savingProfile: Boolean = false,
    val savingSettings: Boolean = false,
    val signingOut: Boolean = false,
    val message: ProfileMessage? = null,
) {
    val profileDirty: Boolean
        get() = profile != null &&
            (displayNameInput.trim() != profile.displayName ||
                educationLevelInput != profile.educationLevel)

    val settingsDirty: Boolean
        get() = settings != null &&
            (localeInput != settings.locale ||
                dailyGoalInput.toIntOrNull() != settings.dailyGoalTargetXp)

    val dailyGoalError: String?
        get() {
            val parsed = dailyGoalInput.toIntOrNull()
                ?: return "Enter a whole number."
            return if (parsed !in SharedSettings.MIN_DAILY_GOAL_XP..SharedSettings.MAX_DAILY_GOAL_XP) {
                "Choose between ${SharedSettings.MIN_DAILY_GOAL_XP} and ${SharedSettings.MAX_DAILY_GOAL_XP}."
            } else {
                null
            }
        }

    val canSaveProfile: Boolean
        get() = profileDirty && !savingProfile &&
            displayNameInput.trim().length in MIN_DISPLAY_NAME_LENGTH..MAX_DISPLAY_NAME_LENGTH &&
            !containsInvalidCharacters(displayNameInput)

    val displayNameError: String?
        get() {
            val trimmed = displayNameInput.trim()
            return when {
                trimmed.isEmpty() -> "Name cannot be empty."
                containsInvalidCharacters(displayNameInput) -> "Name contains unsupported characters."
                trimmed.length !in MIN_DISPLAY_NAME_LENGTH..MAX_DISPLAY_NAME_LENGTH ->
                    "Enter between $MIN_DISPLAY_NAME_LENGTH and $MAX_DISPLAY_NAME_LENGTH characters."
                else -> null
            }
        }

    val canSaveSettings: Boolean
        get() = settingsDirty && !savingSettings && dailyGoalError == null
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** Java-friendly LiveData. */
    val uiStateLiveData by lazy { _uiState.asLiveData() }

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loadState = ProfileLoadState.Loading, message = null) }

        viewModelScope.launch {
            val profileResult = accountRepository.getProfile()
            val settingsResult = accountRepository.getSettings()

            if (profileResult is ApiResult.Failure) {
                _uiState.update { it.copy(loadState = profileResult.error.toLoadFailure()) }
                return@launch
            }
            if (settingsResult is ApiResult.Failure) {
                _uiState.update { it.copy(loadState = settingsResult.error.toLoadFailure()) }
                return@launch
            }

            val profile = (profileResult as ApiResult.Success).value
            val settings = (settingsResult as ApiResult.Success).value

            _uiState.update {
                it.copy(
                    loadState = ProfileLoadState.Loaded,
                    profile = profile,
                    settings = settings,
                    displayNameInput = profile.displayName,
                    educationLevelInput = profile.educationLevel,
                    localeInput = settings.locale,
                    dailyGoalInput = settings.dailyGoalTargetXp.toString(),
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayNameInput = value, message = null) }
    }

    fun onEducationLevelChange(value: EducationLevel?) {
        _uiState.update { it.copy(educationLevelInput = value, message = null) }
    }

    fun onLocaleChange(value: AppLocale) {
        _uiState.update { it.copy(localeInput = value, message = null) }
    }

    fun onDailyGoalChange(value: String) {
        _uiState.update { it.copy(dailyGoalInput = value, message = null) }
    }

    /**
     * Sends only the fields that actually changed, with the revision this client
     * last read in `If-Match`.
     */
    fun saveProfile() {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (!state.canSaveProfile) return

        _uiState.update { it.copy(savingProfile = true, message = null) }

        viewModelScope.launch {
            val trimmedName = state.displayNameInput.trim()
            val result = accountRepository.updateProfile(
                revision = profile.revision,
                displayName = if (trimmedName != profile.displayName) {
                    PatchValue.Set(trimmedName)
                } else {
                    PatchValue.Unchanged
                },
                educationLevel = if (state.educationLevelInput != profile.educationLevel) {
                    // Set(null) is a deliberate clear, and stays distinct from
                    // "unchanged" all the way to the wire.
                    PatchValue.Set(state.educationLevelInput)
                } else {
                    PatchValue.Unchanged
                },
            )

            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        savingProfile = false,
                        profile = result.value,
                        displayNameInput = result.value.displayName,
                        educationLevelInput = result.value.educationLevel,
                        message = ProfileMessage(kind = ProfileMessageKind.ProfileSaved),
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(savingProfile = false, message = result.error.toSaveMessage())
                }
            }
        }
    }

    /** `PUT /me/settings` replaces the resource, so both fields are always sent. */
    fun saveSettings() {
        val state = _uiState.value
        val settings = state.settings ?: return
        if (!state.canSaveSettings) return
        val dailyGoal = state.dailyGoalInput.toIntOrNull() ?: return

        _uiState.update { it.copy(savingSettings = true, message = null) }

        viewModelScope.launch {
            val result = accountRepository.replaceSettings(
                revision = settings.revision,
                locale = state.localeInput,
                dailyGoalTargetXp = dailyGoal,
            )

            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        savingSettings = false,
                        settings = result.value,
                        localeInput = result.value.locale,
                        dailyGoalInput = result.value.dailyGoalTargetXp.toString(),
                        message = ProfileMessage(kind = ProfileMessageKind.SettingsSaved),
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(savingSettings = false, message = result.error.toSaveMessage())
                }
            }
        }
    }

    fun signOut() = endSession { sessionRepository.logout() }

    fun signOutEverywhere() = endSession { sessionRepository.logoutAll() }

    /**
     * Local session state is cleared by the repository whether or not the network
     * call succeeds, so an offline sign-out still leaves no usable token behind.
     */
    private fun endSession(call: suspend () -> ApiResult<Unit>) {
        if (_uiState.value.signingOut) return
        _uiState.update { it.copy(signingOut = true, message = null) }
        viewModelScope.launch {
            call()
            _uiState.update { it.copy(signingOut = false) }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

}

private fun ApiError.toLoadFailure(): ProfileLoadState.Failed = when (this) {
    is ApiError.Network -> ProfileLoadState.Failed(ProfileMessageKind.NetworkUnavailable, null)
    is ApiError.Backend -> ProfileLoadState.Failed(ProfileMessageKind.Generic, requestId)
    is ApiError.Unexpected -> ProfileLoadState.Failed(ProfileMessageKind.Generic, requestId)
}

private const val MIN_DISPLAY_NAME_LENGTH = 1
private const val MAX_DISPLAY_NAME_LENGTH = 50

/**
 * Returns true when [value] contains characters the contract forbids:
 * control characters (U+0000–001F, U+007F) and zero-width joiner abuse patterns.
 * Emoji and most Unicode are intentionally allowed — the backend validates further.
 */
private fun containsInvalidCharacters(value: String): Boolean =
    value.any { it.code in 0x0000..0x001F || it.code == 0x007F }

private fun ApiError.toSaveMessage(): ProfileMessage = when (this) {
    is ApiError.Network -> ProfileMessage(
        kind = ProfileMessageKind.NetworkUnavailable,
        isError = true,
    )

    is ApiError.Backend -> if (code == ApiErrorCodes.CONFLICT_REVISION_MISMATCH) {
        ProfileMessage(
            kind = ProfileMessageKind.RevisionConflict,
            requestId = requestId,
            isError = true,
            requiresReload = true,
        )
    } else {
        ProfileMessage(kind = ProfileMessageKind.Generic, requestId = requestId, isError = true)
    }

    is ApiError.Unexpected -> ProfileMessage(
        kind = ProfileMessageKind.Generic,
        requestId = requestId,
        isError = true,
    )
}
