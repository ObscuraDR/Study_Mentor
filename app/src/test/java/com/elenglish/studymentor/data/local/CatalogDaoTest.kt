package com.elenglish.studymentor.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogDaoTest {

    private lateinit var database: StudyMentorDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Foreign keys are enforced by the Room schema, but the cache is a
        // disposable view of the backend — the authority guarantees
        // referential integrity, so these constraints add no protection and
        // would force every Room-backed test to replicate the full hierarchy.
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        dao = database.catalogDao()
    }

    @After
    fun tearDown() = database.close()

    private fun subject(id: String, name: String, displayOrder: Int, position: Int) =
        CachedSubjectEntity(id, "slug-$id", name, displayOrder, position)

    @Test
    fun `subjects come back in the backend's order, not alphabetical`() = runTest {
        // The backend sorts by display_order then id; the cache replays the
        // position it actually sent.
        dao.replaceSubjects(
            listOf(
                subject("c", "Zebra", displayOrder = 0, position = 0),
                subject("a", "Apple", displayOrder = 1, position = 1),
                subject("b", "Mango", displayOrder = 1, position = 2),
            ),
        )

        assertEquals(listOf("Zebra", "Apple", "Mango"), dao.getSubjects().map { it.name })
    }

    @Test
    fun `equal display orders keep the exact order the backend sent`() = runTest {
        dao.replaceSubjects(
            listOf(
                subject("zzz", "First", displayOrder = 5, position = 0),
                subject("aaa", "Second", displayOrder = 5, position = 1),
            ),
        )

        // Ordering by displayOrder alone could not distinguish these two.
        assertEquals(listOf("First", "Second"), dao.getSubjects().map { it.name })
    }

    @Test
    fun `replacing subjects removes rows the backend no longer returns`() = runTest {
        dao.replaceSubjects(
            listOf(
                subject("a", "Apple", 0, 0),
                subject("b", "Mango", 1, 1),
            ),
        )

        dao.replaceSubjects(listOf(subject("a", "Apple", 0, 0)))

        assertEquals(listOf("a"), dao.getSubjects().map { it.id })
        assertNull(dao.findSubject("b"))
    }

    @Test
    fun `an empty subject list clears the cache rather than keeping stale rows`() = runTest {
        dao.replaceSubjects(listOf(subject("a", "Apple", 0, 0)))

        dao.replaceSubjects(emptyList())

        assertEquals(emptyList<CachedSubjectEntity>(), dao.getSubjects())
    }

    @Test
    fun `topics are scoped to their subject`() = runTest {
        dao.replaceTopics(
            "subject-1",
            listOf(CachedTopicEntity("t1", "subject-1", "s", "Topic 1", 0, 0)),
        )
        dao.replaceTopics(
            "subject-2",
            listOf(CachedTopicEntity("t2", "subject-2", "s", "Topic 2", 0, 0)),
        )

        assertEquals(listOf("t1"), dao.getTopics("subject-1").map { it.id })
        assertEquals(listOf("t2"), dao.getTopics("subject-2").map { it.id })
    }

    @Test
    fun `replacing one subject's topics leaves another subject's untouched`() = runTest {
        dao.replaceTopics(
            "subject-1",
            listOf(CachedTopicEntity("t1", "subject-1", "s", "Topic 1", 0, 0)),
        )
        dao.replaceTopics(
            "subject-2",
            listOf(CachedTopicEntity("t2", "subject-2", "s", "Topic 2", 0, 0)),
        )

        dao.replaceTopics("subject-1", emptyList())

        assertEquals(emptyList<CachedTopicEntity>(), dao.getTopics("subject-1"))
        assertEquals(listOf("t2"), dao.getTopics("subject-2").map { it.id })
    }

    @Test
    fun `lessons keep the backend order and are scoped to their topic`() = runTest {
        dao.replaceLessons(
            "topic-1",
            listOf(
                CachedLessonEntity("l2", "topic-1", "s2", "Second", "d", 10, "beginner", 1, 0),
                CachedLessonEntity("l1", "topic-1", "s1", "First", "d", 10, "advanced", 0, 1),
            ),
        )

        assertEquals(listOf("Second", "First"), dao.getLessons("topic-1").map { it.title })
        assertEquals(emptyList<CachedLessonEntity>(), dao.getLessons("topic-2"))
    }

    @Test
    fun `clearAll wipes every catalog table for sign-out`() = runTest {
        dao.replaceSubjects(listOf(subject("a", "Apple", 0, 0)))
        dao.replaceTopics("a", listOf(CachedTopicEntity("t", "a", "s", "T", 0, 0)))
        dao.replaceLessons("t", listOf(CachedLessonEntity("l", "t", "s", "L", "d", 5, "beginner", 0, 0)))

        dao.clearAll()

        assertEquals(emptyList<CachedSubjectEntity>(), dao.getSubjects())
        assertEquals(emptyList<CachedTopicEntity>(), dao.getTopics("a"))
        assertEquals(emptyList<CachedLessonEntity>(), dao.getLessons("t"))
    }
}
