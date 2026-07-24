package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for the authentication endpoints of OpenAPI v1.
 *
 * `toString()` is overridden on every type that carries a secret so a token can
 * never leak through logging, crash reporting or a debugger-friendly data-class
 * `toString()`.
 */

@Serializable
data class RegisterRequestDto(
    val displayName: String,
    val email: String,
    val password: String,
) {
    override fun toString(): String = "RegisterRequestDto(displayName=***, email=***, password=***)"
}

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequestDto(email=***, password=***)"
}

@Serializable
data class AndroidRefreshRequestDto(
    val refreshToken: String,
) {
    override fun toString(): String = "AndroidRefreshRequestDto(refreshToken=***)"
}

@Serializable
data class UserDto(
    val id: String,
    val displayName: String,
    val email: String,
    val createdAt: String,
)

/** Response of register/login. Android additionally receives the refresh token. */
@Serializable
data class SessionDto(
    val user: UserDto,
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String? = null,
) {
    override fun toString(): String =
        "SessionDto(user=${user.id}, accessToken=***, accessTokenExpiresAt=$accessTokenExpiresAt, " +
            "refreshToken=${if (refreshToken == null) "null" else "***"})"
}

/** Response of refresh. Carries no user object. */
@Serializable
data class RefreshedSessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String? = null,
) {
    override fun toString(): String =
        "RefreshedSessionDto(accessToken=***, accessTokenExpiresAt=$accessTokenExpiresAt, " +
            "refreshToken=${if (refreshToken == null) "null" else "***"})"
}
