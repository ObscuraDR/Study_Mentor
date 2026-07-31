package com.elenglish.studymentor.data.catalog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.data.local.StudyMentorDatabase
import com.elenglish.studymentor.domain.model.DataOrigin
import com.elenglish.studymentor.domain.model.Difficulty
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the real cache policy: live Retrofit against MockWebServer plus a
 * real in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var database: StudyMentorDatabase
    private lateinit var repository: CatalogRepository

    private val fixedTime = object : TimeSource {
        override fun nowEpochMillis(): Long = 1_700_000_000_000
    }

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build()
        // The cache is a disposable view of the backend; referential integrity
        // is the backend's responsibility. Disable FK enforcement so tests can
        // seed individual cache rows without replicating the full hierarchy.
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")

        repository = CatalogRepository(
            catalogApi = harness.catalogApi,
            catalogDao = database.catalogDao(),
            cacheMetadataDao = database.cacheMetadataDao(),
            json = harness.json,
            timeSource = fixedTime,
        )
    }

    @After
    fun tearDown() {
        database.close()
        harness.shutdown()
    }

    /** Makes the backend unreachable, which surfaces as ApiError.Network. */
    private fun enqueueNetworkFailure() = harness.goOffline()

    /**
     * A time source whose clock can be advanced by tests to simulate cache aging.
     * Its initial value matches [fixedTime] so the existing assertions still hold.
     */
    private class MutableTimeSource(
        private var currentMillis: Long = 1_700_000_000_000,
    ) : TimeSource {
        override fun nowEpochMillis(): Long = currentMillis
        fun advanceBy(millis: Long) { currentMillis += millis }
    }

    @Test
    fun `a live read is marked live and keeps the backend order exactly`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.subjects(
                    Triple("s-zeta", "Zeta", 0),
                    Triple("s-alpha", "Alpha", 1),
                    Triple("s-mid", "Mid", 1),
                ),
            ),
        )

        val result = repository.getSubjects()

        val data = (result as ApiResult.Success).value
        assertEquals(DataOrigin.Live, data.origin)
        // Backend order, not alphabetical and not by displayOrder alone.
        assertEquals(listOf("Zeta", "Alpha", "Mid"), data.value.map { it.name })
        assertEquals(Fixtures.REQUEST_ID, result.requestId)
    }

    @Test
    fun `an offline read falls back to the cache and says it is a saved copy`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.subjects(Triple("s-1", "Grammar", 0), Triple("s-2", "Vocabulary", 1)),
            ),
        )
        val first = repository.getSubjects()
        assertTrue("first read should succeed: $first", first is ApiResult.Success)
        assertEquals(2, database.catalogDao().getSubjects().size)

        enqueueNetworkFailure()
        val result = repository.getSubjects()

        val data = (result as ApiResult.Success).value
        assertEquals(DataOrigin.Cached, data.origin)
        assertEquals(listOf("Grammar", "Vocabulary"), data.value.map { it.name })
        assertEquals(fixedTime.nowEpochMillis(), data.cachedAtEpochMillis)
    }

    @Test
    fun `an offline read with no cache reports the failure instead of inventing content`() =
        runTest {
            enqueueNetworkFailure()

            val result = repository.getSubjects()

            assertTrue(result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Network)
        }

    @Test
    fun `a backend rejection is never masked by cached rows`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        repository.getSubjects()

        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )
        val result = repository.getSubjects()

        // A 401 is a real answer about access. Showing stale content instead
        // would display material the user may no longer be entitled to.
        assertTrue(result is ApiResult.Failure)
        assertEquals(
            ApiErrorCodes.AUTH_SESSION_EXPIRED,
            ((result as ApiResult.Failure).error as ApiError.Backend).code,
        )
    }

    @Test
    fun `an empty list is a valid answer, not a failure`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.subjects()))

        val result = repository.getSubjects()

        assertEquals(emptyList<String>(), (result as ApiResult.Success).value.value.map { it.name })
        assertEquals(DataOrigin.Live, result.value.origin)
    }

    @Test
    fun `a refreshed list drops rows the backend stopped returning`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.subjects(Triple("s-1", "Grammar", 0), Triple("s-2", "Vocabulary", 1)),
            ),
        )
        repository.getSubjects()

        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        repository.getSubjects()

        enqueueNetworkFailure()
        val cached = (repository.getSubjects() as ApiResult.Success).value
        assertEquals(listOf("Grammar"), cached.value.map { it.name })
    }

    @Test
    fun `topics are cached per subject`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.topics("subj-1", "t-1" to "Tenses", "t-2" to "Articles")),
        )
        repository.getTopics("subj-1")

        enqueueNetworkFailure()
        val cached = (repository.getTopics("subj-1") as ApiResult.Success).value
        assertEquals(listOf("Tenses", "Articles"), cached.value.map { it.name })

        enqueueNetworkFailure()
        val other = repository.getTopics("subj-2")
        assertTrue(other is ApiResult.Failure)
    }

    @Test
    fun `lessons map difficulty and survive a cache round trip`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lessons("topic-1", "l-1" to "Present simple")),
        )
        val live = (repository.getLessons("topic-1") as ApiResult.Success).value
        assertEquals(Difficulty.Beginner, live.value.single().difficulty)

        enqueueNetworkFailure()
        val cached = (repository.getLessons("topic-1") as ApiResult.Success).value
        assertEquals(Difficulty.Beginner, cached.value.single().difficulty)
        assertEquals("Present simple", cached.value.single().title)
    }

    @Test
    fun `an unrecognised difficulty degrades to null rather than crashing`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("l-1", "Future perfect", difficulty = "expert")),
        )

        val result = repository.getLesson("l-1")

        assertNull((result as ApiResult.Success).value.value.difficulty)
    }

    @Test
    fun `a single lesson falls back to its cached copy when offline`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lessons("topic-1", "l-1" to "Present simple")),
        )
        repository.getLessons("topic-1")

        enqueueNetworkFailure()
        val result = repository.getLesson("l-1")

        val data = (result as ApiResult.Success).value
        assertEquals(DataOrigin.Cached, data.origin)
        assertEquals("Present simple", data.value.title)
    }

    @Test
    fun `a missing lesson reports not found rather than falling back`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody(Fixtures.error("learning.resource_not_found")),
        )

        val result = repository.getLesson("missing")

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `a stale cache older than 24 hours is not used as a fallback`() = runTest {
        val mutableClock = MutableTimeSource()
        val staleRepository = CatalogRepository(
            catalogApi = harness.catalogApi,
            catalogDao = database.catalogDao(),
            cacheMetadataDao = database.cacheMetadataDao(),
            json = harness.json,
            timeSource = mutableClock,
        )

        // Seed the cache.
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        staleRepository.getSubjects()

        // Advance past the TTL threshold.
        mutableClock.advanceBy(24L * 60 * 60 * 1000 + 1)

        enqueueNetworkFailure()
        val result = staleRepository.getSubjects()

        // Cache is too old — the network error is surfaced instead.
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Network)
    }

    @Test
    fun `a cache within TTL is still returned as a fallback when offline`() = runTest {
        val mutableClock = MutableTimeSource()
        val staleRepository = CatalogRepository(
            catalogApi = harness.catalogApi,
            catalogDao = database.catalogDao(),
            cacheMetadataDao = database.cacheMetadataDao(),
            json = harness.json,
            timeSource = mutableClock,
        )

        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        staleRepository.getSubjects()

        // Advance just shy of 24 hours — cache should still be fresh.
        mutableClock.advanceBy((24L * 60 * 60 * 1000) - 1L)

        enqueueNetworkFailure()
        val result = staleRepository.getSubjects()

        val data = (result as ApiResult.Success).value
        assertEquals(DataOrigin.Cached, data.origin)
        assertEquals(listOf("Grammar"), data.value.map { it.name })
    }

    @Test
    fun `signing out wipes every cached row`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        repository.getSubjects()

        repository.clearForSignOut()

        enqueueNetworkFailure()
        assertTrue(repository.getSubjects() is ApiResult.Failure)
    }
}
