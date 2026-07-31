package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.AndroidRefreshRequestDto
import com.elenglish.studymentor.data.remote.dto.LoginRequestDto
import com.elenglish.studymentor.data.remote.dto.RefreshedSessionDto
import com.elenglish.studymentor.data.remote.dto.RegisterRequestDto
import com.elenglish.studymentor.data.remote.dto.SessionDto
import com.elenglish.studymentor.data.remote.dto.UserDto
import com.elenglish.studymentor.data.remote.dto.PasswordResetRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Authentication endpoints of OpenAPI v1.
 *
 * `X-Client-Platform: android` is added for every request by
 * `ClientPlatformInterceptor`; the backend requires it and uses it to decide
 * that the refresh token belongs in the JSON body rather than a cookie.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<ApiEnvelope<SessionDto>>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<ApiEnvelope<SessionDto>>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body body: AndroidRefreshRequestDto,
    ): Response<ApiEnvelope<RefreshedSessionDto>>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("auth/logout-all")
    suspend fun logoutAll(): Response<Unit>

    @GET("auth/me")
    suspend fun me(): Response<ApiEnvelope<UserDto>>

    @POST("auth/password-reset/request")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequestDto): Response<Unit>
}
