package com.elenglish.studymentor.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheMetadataDaoTest {

    private lateinit var database: StudyMentorDatabase
    private lateinit var dao: CacheMetadataDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cacheMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert then read returns the stored freshness marker`() = runTest {
        dao.upsert(CacheMetadataEntity(resourceKey = "subjects", fetchedAtEpochMillis = 1_000L))

        assertEquals(1_000L, dao.findByKey("subjects")?.fetchedAtEpochMillis)
    }

    @Test
    fun `upsert replaces an existing key instead of duplicating it`() = runTest {
        dao.upsert(CacheMetadataEntity("subjects", 1_000L))
        dao.upsert(CacheMetadataEntity("subjects", 2_000L))

        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(2_000L, all.single().fetchedAtEpochMillis)
    }

    @Test
    fun `observeAll is ordered by resource key`() = runTest {
        dao.upsert(CacheMetadataEntity("topics", 1L))
        dao.upsert(CacheMetadataEntity("lessons", 2L))
        dao.upsert(CacheMetadataEntity("subjects", 3L))

        assertEquals(
            listOf("lessons", "subjects", "topics"),
            dao.observeAll().first().map { it.resourceKey },
        )
    }

    @Test
    fun `clear removes every cached marker on logout`() = runTest {
        dao.upsert(CacheMetadataEntity("subjects", 1L))
        dao.upsert(CacheMetadataEntity("topics", 2L))

        dao.clear()

        assertEquals(emptyList<CacheMetadataEntity>(), dao.observeAll().first())
        assertNull(dao.findByKey("subjects"))
    }

    @Test
    fun `deleteByKey removes only the requested resource`() = runTest {
        dao.upsert(CacheMetadataEntity("subjects", 1L))
        dao.upsert(CacheMetadataEntity("topics", 2L))

        dao.deleteByKey("subjects")

        assertNull(dao.findByKey("subjects"))
        assertEquals(2L, dao.findByKey("topics")?.fetchedAtEpochMillis)
    }
}
