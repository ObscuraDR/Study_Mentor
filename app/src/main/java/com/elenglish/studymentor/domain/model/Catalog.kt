package com.elenglish.studymentor.domain.model

/** Lesson difficulty as constrained by the contract. */
enum class Difficulty(val wireValue: String) {
    Beginner("beginner"),
    Intermediate("intermediate"),
    Advanced("advanced"),
    ;

    companion object {
        fun fromWire(value: String?): Difficulty? = entries.firstOrNull { it.wireValue == value }
    }
}

data class Subject(
    val id: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
)

data class Topic(
    val id: String,
    val subjectId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
)

data class Lesson(
    val id: String,
    val topicId: String,
    val slug: String,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    /** `null` when the backend sends a difficulty this client version predates. */
    val difficulty: Difficulty?,
    val displayOrder: Int,
)

/**
 * Where a piece of catalog data came from.
 *
 * The UI must be able to tell a user that they are looking at a stored copy
 * rather than live data — a cache is never presented as authoritative.
 */
enum class DataOrigin { Live, Cached }

/**
 * A catalog read plus its provenance.
 *
 * @param cachedAtEpochMillis when the cached copy was stored; `null` for live data.
 */
data class CatalogData<T>(
    val value: T,
    val origin: DataOrigin,
    val cachedAtEpochMillis: Long? = null,
)
