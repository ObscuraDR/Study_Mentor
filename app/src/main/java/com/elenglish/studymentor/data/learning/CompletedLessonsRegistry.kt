package com.elenglish.studymentor.data.learning

import com.elenglish.studymentor.core.session.SessionScopedStore
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lessons the backend confirmed as completed during this session.
 *
 * This is **memory of what the server said**, not a claim of its own. It exists
 * so a learner who reopens a lesson they just finished is shown "already
 * recorded" instead of silently submitting a second event.
 *
 * It is not authority and it is not persisted: after a restart, or on another
 * device, the app knows nothing until the backend tells it something. Whether a
 * repeated completion of the same lesson should award XP again is a product and
 * backend rule, not something a client may decide — see the Phase 5 report.
 */
@Singleton
class CompletedLessonsRegistry @Inject constructor() : SessionScopedStore {

    private val completedLessonIds =
        Collections.synchronizedSet(mutableSetOf<String>())

    fun markCompleted(lessonId: String) {
        completedLessonIds.add(lessonId)
    }

    fun wasCompletedThisSession(lessonId: String): Boolean =
        completedLessonIds.contains(lessonId)

    override suspend fun clearForSignOut() {
        completedLessonIds.clear()
    }
}
