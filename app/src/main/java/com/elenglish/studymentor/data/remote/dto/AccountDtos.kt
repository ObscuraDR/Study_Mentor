package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Wire types for `/me/profile` and `/me/settings`. */

@Serializable
data class ProfileDto(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarKey: String? = null,
    val educationLevel: String? = null,
    val updatedAt: String,
    val revision: String,
)

/**
 * `PATCH /me/profile` is a partial patch requiring at least one property.
 *
 * `avatarKey` and `educationLevel` are both nullable *and* omissible in the
 * contract, so "set to null" and "leave untouched" are genuinely different
 * requests. A data class cannot express that difference — the app-wide Json
 * instance uses `explicitNulls = false` and would silently drop a deliberate
 * clear — so the patch is built as an explicit [JsonObject] instead.
 *
 * Use [PatchValue] to say what you mean:
 * - [PatchValue.Unchanged] omits the key entirely.
 * - [PatchValue.Set] writes the value, including an explicit JSON `null`.
 */
sealed interface PatchValue<out T> {
    data object Unchanged : PatchValue<Nothing>
    data class Set<T>(val value: T?) : PatchValue<T>
}

/** Builds the `PATCH /me/profile` body, preserving null-vs-absent semantics. */
fun buildProfilePatch(
    displayName: PatchValue<String> = PatchValue.Unchanged,
    avatarKey: PatchValue<String> = PatchValue.Unchanged,
    educationLevel: PatchValue<String> = PatchValue.Unchanged,
): JsonObject = buildJsonObject {
    putPatch("displayName", displayName)
    putPatch("avatarKey", avatarKey)
    putPatch("educationLevel", educationLevel)
}

private fun JsonObjectBuilder.putPatch(key: String, value: PatchValue<String>) {
    when (value) {
        is PatchValue.Unchanged -> Unit
        is PatchValue.Set -> put(key, value.value?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}

@Serializable
data class SharedSettingsDto(
    val locale: String,
    val dailyGoalTargetXp: Int,
    val updatedAt: String,
    val revision: String,
)

/** `PUT /me/settings` is a full replacement: both fields are required. */
@Serializable
data class UpdateSettingsRequestDto(
    val locale: String,
    val dailyGoalTargetXp: Int,
)
