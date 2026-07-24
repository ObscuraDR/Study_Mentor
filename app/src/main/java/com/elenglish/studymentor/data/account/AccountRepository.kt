package com.elenglish.studymentor.data.account

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.data.remote.AccountApi
import com.elenglish.studymentor.data.remote.dto.PatchValue
import com.elenglish.studymentor.data.remote.dto.ProfileDto
import com.elenglish.studymentor.data.remote.dto.SharedSettingsDto
import com.elenglish.studymentor.data.remote.dto.UpdateSettingsRequestDto
import com.elenglish.studymentor.data.remote.dto.buildProfilePatch
import com.elenglish.studymentor.domain.model.AppLocale
import com.elenglish.studymentor.domain.model.EducationLevel
import com.elenglish.studymentor.domain.model.SharedSettings
import com.elenglish.studymentor.domain.model.UserProfile
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile and shared settings.
 *
 * Both mutations are optimistic-concurrency controlled: the caller passes the
 * revision it last read, and a `409 conflict.revision_mismatch` is surfaced as
 * [ApiError.Backend] with that code so the UI can prompt for a re-read instead
 * of overwriting a change made on another device.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountApi: AccountApi,
    private val json: Json,
) {

    suspend fun getProfile(): ApiResult<UserProfile> = safeApiCall(
        json = json,
        block = { accountApi.getProfile() },
        transform = ProfileDto::toDomain,
    )

    /**
     * Applies a partial profile patch.
     *
     * Each field is a [PatchValue] so "leave untouched" and "clear to null" stay
     * distinguishable on the wire, matching the contract's nullable-and-omissible
     * properties.
     */
    suspend fun updateProfile(
        revision: String,
        displayName: PatchValue<String> = PatchValue.Unchanged,
        avatarKey: PatchValue<String> = PatchValue.Unchanged,
        educationLevel: PatchValue<EducationLevel> = PatchValue.Unchanged,
    ): ApiResult<UserProfile> {
        val patch = buildProfilePatch(
            displayName = displayName,
            avatarKey = avatarKey,
            educationLevel = when (educationLevel) {
                is PatchValue.Unchanged -> PatchValue.Unchanged
                is PatchValue.Set -> PatchValue.Set(educationLevel.value?.wireValue)
            },
        )

        require(patch.isNotEmpty()) { "a profile patch must change at least one field" }

        return safeApiCall(
            json = json,
            block = { accountApi.updateProfile(revision, patch) },
            transform = ProfileDto::toDomain,
        )
    }

    suspend fun getSettings(): ApiResult<SharedSettings> = safeApiCall(
        json = json,
        block = { accountApi.getSettings() },
        transform = SharedSettingsDto::toDomain,
    )

    /** `PUT /me/settings` replaces the whole resource; both fields are required. */
    suspend fun replaceSettings(
        revision: String,
        locale: AppLocale,
        dailyGoalTargetXp: Int,
    ): ApiResult<SharedSettings> {
        require(
            dailyGoalTargetXp in SharedSettings.MIN_DAILY_GOAL_XP..SharedSettings.MAX_DAILY_GOAL_XP,
        ) { "dailyGoalTargetXp is outside the range the contract allows" }

        return safeApiCall(
            json = json,
            block = {
                accountApi.replaceSettings(
                    revision,
                    UpdateSettingsRequestDto(locale.wireValue, dailyGoalTargetXp),
                )
            },
            transform = SharedSettingsDto::toDomain,
        )
    }
}

/** True when the failure means another writer moved the resource on. */
fun ApiResult.Failure.isRevisionConflict(): Boolean {
    val backend = error as? ApiError.Backend ?: return false
    return backend.code == ApiErrorCodes.CONFLICT_REVISION_MISMATCH
}

private fun ProfileDto.toDomain(): UserProfile = UserProfile(
    id = id,
    displayName = displayName,
    email = email,
    avatarKey = avatarKey,
    educationLevel = EducationLevel.fromWire(educationLevel),
    updatedAt = updatedAt,
    revision = revision,
)

private fun SharedSettingsDto.toDomain(): SharedSettings = SharedSettings(
    // The contract constrains locale to vi|en. An unknown value would mean the
    // contract moved; fall back rather than crash on a user's settings screen.
    locale = AppLocale.fromWire(locale) ?: AppLocale.English,
    dailyGoalTargetXp = dailyGoalTargetXp,
    updatedAt = updatedAt,
    revision = revision,
)
