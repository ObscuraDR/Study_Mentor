package com.elenglish.studymentor.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Read cache for the learning catalog.
 *
 * Every list query orders by `position`, replaying the backend's own ordering.
 * A list refresh is transactional: stale rows for that parent are removed and
 * the new page written together, so a partially-updated list can never be shown.
 */
@Dao
interface CatalogDao {

    // Subjects ---------------------------------------------------------------

    @Query("SELECT * FROM cached_subjects ORDER BY position ASC")
    suspend fun getSubjects(): List<CachedSubjectEntity>

    @Query("SELECT * FROM cached_subjects WHERE id = :subjectId")
    suspend fun findSubject(subjectId: String): CachedSubjectEntity?

    @Upsert
    suspend fun upsertSubjects(subjects: List<CachedSubjectEntity>)

    @Query("DELETE FROM cached_subjects WHERE id NOT IN (:keepIds)")
    suspend fun deleteSubjectsNotIn(keepIds: List<String>)

    @Query("DELETE FROM cached_subjects")
    suspend fun deleteAllSubjects()

    @Transaction
    suspend fun replaceSubjects(subjects: List<CachedSubjectEntity>) {
        if (subjects.isEmpty()) deleteAllSubjects() else deleteSubjectsNotIn(subjects.map { it.id })
        upsertSubjects(subjects)
    }

    // Topics -----------------------------------------------------------------

    @Query("SELECT * FROM cached_topics WHERE subjectId = :subjectId ORDER BY position ASC")
    suspend fun getTopics(subjectId: String): List<CachedTopicEntity>

    @Query("SELECT * FROM cached_topics WHERE id = :topicId")
    suspend fun findTopic(topicId: String): CachedTopicEntity?

    @Upsert
    suspend fun upsertTopics(topics: List<CachedTopicEntity>)

    @Query("DELETE FROM cached_topics WHERE subjectId = :subjectId")
    suspend fun deleteTopicsOfSubject(subjectId: String)

    @Transaction
    suspend fun replaceTopics(subjectId: String, topics: List<CachedTopicEntity>) {
        deleteTopicsOfSubject(subjectId)
        upsertTopics(topics)
    }

    // Lessons ----------------------------------------------------------------

    @Query("SELECT * FROM cached_lessons WHERE topicId = :topicId ORDER BY position ASC")
    suspend fun getLessons(topicId: String): List<CachedLessonEntity>

    @Query("SELECT * FROM cached_lessons WHERE id = :lessonId")
    suspend fun findLesson(lessonId: String): CachedLessonEntity?

    @Upsert
    suspend fun upsertLessons(lessons: List<CachedLessonEntity>)

    @Query("DELETE FROM cached_lessons WHERE topicId = :topicId")
    suspend fun deleteLessonsOfTopic(topicId: String)

    @Transaction
    suspend fun replaceLessons(topicId: String, lessons: List<CachedLessonEntity>) {
        deleteLessonsOfTopic(topicId)
        upsertLessons(lessons)
    }

    // Sign-out ---------------------------------------------------------------

    @Query("DELETE FROM cached_lessons")
    suspend fun deleteAllLessons()

    @Query("DELETE FROM cached_topics")
    suspend fun deleteAllTopics()

    /** Called on sign-out so one user's cached catalog never greets the next. */
    @Transaction
    suspend fun clearAll() {
        deleteAllLessons()
        deleteAllTopics()
        deleteAllSubjects()
    }
}
