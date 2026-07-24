package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.ProfileDto
import com.elenglish.studymentor.data.remote.dto.SharedSettingsDto
import com.elenglish.studymentor.data.remote.dto.UpdateSettingsRequestDto
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.PUT

/**
 * Profile and shared-settings endpoints.
 *
 * Both mutations require `If-Match` carrying the revision the client last read.
 * The backend answers `409 conflict.revision_mismatch` when the stored revision
 * has moved on, which the UI surfaces as a re-read prompt rather than silently
 * overwriting another device's change.
 */
interface AccountApi {

    @GET("me/profile")
    suspend fun getProfile(): Response<ApiEnvelope<ProfileDto>>

    @PATCH("me/profile")
    suspend fun updateProfile(
        @Header("If-Match") revision: String,
        @Body patch: JsonObject,
    ): Response<ApiEnvelope<ProfileDto>>

    @GET("me/settings")
    suspend fun getSettings(): Response<ApiEnvelope<SharedSettingsDto>>

    @PUT("me/settings")
    suspend fun replaceSettings(
        @Header("If-Match") revision: String,
        @Body body: UpdateSettingsRequestDto,
    ): Response<ApiEnvelope<SharedSettingsDto>>
}
