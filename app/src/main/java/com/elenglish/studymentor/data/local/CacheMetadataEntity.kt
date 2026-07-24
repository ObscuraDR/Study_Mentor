package com.elenglish.studymentor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Freshness bookkeeping for non-authoritative read caches.
 *
 * Room stores cached *reads* only. It never stores tokens, and it is never the
 * authority for XP, progress, quiz results or authorization state.
 *
 * @param resourceKey stable key of the cached resource, e.g. `subjects`.
 * @param fetchedAtEpochMillis device clock time the cached copy was written.
 */
@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val resourceKey: String,
    val fetchedAtEpochMillis: Long,
)
