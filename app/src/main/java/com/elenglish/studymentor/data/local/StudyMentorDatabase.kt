package com.elenglish.studymentor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local read cache. Version history is tracked in `app/schemas`.
 *
 * Only non-authoritative catalog/cache data belongs here.
 */
@Database(
    entities = [
        CacheMetadataEntity::class,
        CachedSubjectEntity::class,
        CachedTopicEntity::class,
        CachedLessonEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class StudyMentorDatabase : RoomDatabase() {

    abstract fun cacheMetadataDao(): CacheMetadataDao

    abstract fun catalogDao(): CatalogDao

    companion object {
        const val NAME = "study_mentor.db"
    }
}
