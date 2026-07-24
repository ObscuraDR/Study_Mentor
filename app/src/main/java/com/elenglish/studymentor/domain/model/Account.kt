package com.elenglish.studymentor.domain.model

/** Canonical authenticated identity (`User` in OpenAPI v1). */
data class AuthUser(
    val id: String,
    val displayName: String,
    val email: String,
    val createdAt: String,
)

/** Education level as constrained by the contract. */
enum class EducationLevel(val wireValue: String) {
    Beginner("beginner"),
    Intermediate("intermediate"),
    Advanced("advanced"),
    ;

    companion object {
        fun fromWire(value: String?): EducationLevel? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Editable profile. [revision] is the opaque server token that must be echoed
 * back in `If-Match`; it is what makes a concurrent edit detectable.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    /** Read-only in the contract. */
    val email: String,
    val avatarKey: String?,
    val educationLevel: EducationLevel?,
    val updatedAt: String,
    val revision: String,
)

/** Locale as constrained by the contract. */
enum class AppLocale(val wireValue: String) {
    Vietnamese("vi"),
    English("en"),
    ;

    companion object {
        fun fromWire(value: String?): AppLocale? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Settings shared across devices. Deliberately narrow: theme and other device UI
 * preferences stay local and are not synchronised.
 */
data class SharedSettings(
    val locale: AppLocale,
    val dailyGoalTargetXp: Int,
    val updatedAt: String,
    val revision: String,
) {
    companion object {
        const val MIN_DAILY_GOAL_XP = 1
        const val MAX_DAILY_GOAL_XP = 10_000
    }
}
