package com.elenglish.studymentor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached catalog rows.
 *
 * These are **non-authoritative copies** of backend data, kept so the app can
 * show something useful while offline. They never become the source of truth.
 *
 * [position] is the index the row held in the backend's response. The backend
 * sorts by `display_order, id`, so `displayOrder` alone cannot reproduce its
 * order when two rows share one — storing the actual position is the only way
 * to replay the server's ordering exactly.
 */
@Entity(tableName = "cached_subjects")
data class CachedSubjectEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val position: Int,
)

@Entity(
    tableName = "cached_topics",
    indices = [Index("subjectId")],
)
data class CachedTopicEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val slug: String,
    val name: String,
    val displayOrder: Int,
    val position: Int,
)

@Entity(
    tableName = "cached_lessons",
    indices = [Index("topicId")],
)
data class CachedLessonEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val slug: String,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    /** Stored as the wire string so an unrecognised value survives a round trip. */
    val difficulty: String,
    val displayOrder: Int,
    val position: Int,
)
