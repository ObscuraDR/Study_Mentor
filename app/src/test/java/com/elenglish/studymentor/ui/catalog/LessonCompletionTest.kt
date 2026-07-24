package com.elenglish.studymentor.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.catalog.CatalogRepository
import com.elenglish.studymentor.data.learning.CompletedLessonsRegistry
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.data.local.StudyMentorDatabase
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.awaitQuiescence
import com.elenglish.studymentor.testing.Fixtures
import com.elenglish.studymentor.testing.awaitCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Completion behaviour on the lesson detail screen, with particular attention to
 * what makes a retry safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LessonCompletionTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var database: StudyMentorDatabase
    private lateinit var viewModel: LessonDetailViewModel

    private val completedLessons = CompletedLessonsRegistry()
    private var currentMillis = 1_774_000_000_000L
    private val timeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = currentMillis
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build()

        // The screen loads its lesson first, then checks the backend's own
        // completion read model for it (empty here: nothing completed yet).
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("lesson-1", "Present simple")),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.lessonCompletions()),
        )

        viewModel = LessonDetailViewModel(
            repository = CatalogRepository(
                catalogApi = harness.catalogApi,
                catalogDao = database.catalogDao(),
                cacheMetadataDao = database.cacheMetadataDao(),
                json = harness.json,
                timeSource = timeSource,
            ),
            learningRepository = LearningRepository(
                learningApi = harness.learningApi,
                uuidGenerator = UuidV7Generator(timeSource),
                timeSource = timeSource,
                json = harness.json,
            ),
            completedLessons = completedLessons,
            timeSource = timeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "lesson-1")),
        )
        awaitLoaded()
        harness.server.takeRequest()
        harness.server.takeRequest()
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
        database.close()
    }

    private fun awaitLoaded() = runBlocking {
        awaitCondition(describe = { "lesson to load" }) {
            viewModel.uiState.value is LessonDetailUiState.Content
        }
    }

    private fun awaitCompletionSettled() = runBlocking {
        awaitCondition(describe = { "completion to settle" }) {
            completion() !is CompletionState.Submitting
        }
    }

    private fun completion(): CompletionState =
        (viewModel.uiState.value as LessonDetailUiState.Content).completion

    @Test
    fun `starts idle, with nothing claimed about completion`() {
        assertEquals(CompletionState.Idle, completion())
    }

    @Test
    fun `an accepted completion shows the server's xp and progress`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.learningEventSubmission(xpEarned = 20, totalXp = 20)),
        )

        viewModel.markComplete(studiedSeconds = 600)
        awaitCompletionSettled()

        val accepted = completion() as CompletionState.Accepted
        assertEquals(20, accepted.result.event.xpEarned)
        assertEquals(20, accepted.result.progress.totalXp)
        assertFalse(accepted.result.wasReplay)
    }

    @Test
    fun `duration reports measured time on screen, not the advertised length`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.learningEventSubmission()),
        )
        // The learner spends 90 seconds on a lesson advertised as 12 minutes.
        currentMillis += 90_000

        viewModel.markComplete()
        awaitCompletionSettled()

        val body = Json.parseToJsonElement(
            harness.server.takeRequest().body.readUtf8(),
        ).jsonObject
        assertEquals("90", body["durationSeconds"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a network failure offers a retry`() {
        harness.goOffline()

        viewModel.markComplete(600)
        awaitCompletionSettled()

        val failed = completion() as CompletionState.Failed
        assertEquals(CatalogErrorKind.Network, failed.kind)
        assertTrue(failed.canRetry)
    }

    @Test
    fun `a server error is retryable, because it may have committed the event`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )

        viewModel.markComplete(600)
        awaitCompletionSettled()

        // A 5xx can be raised after the write. Treating it as a definitive
        // rejection and issuing a fresh key on retry could record the same
        // completion twice.
        assertTrue((completion() as CompletionState.Failed).canRetry)
    }

    @Test
    fun `a rate limit is retryable`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody(Fixtures.error(ApiErrorCodes.RATE_LIMIT_EXCEEDED)),
        )

        viewModel.markComplete(600)
        awaitCompletionSettled()

        assertTrue((completion() as CompletionState.Failed).canRetry)
    }

    @Test
    fun `retrying after an unknown outcome reuses the same key and payload`() {
        // First attempt: the server errors, so the outcome is unknown.
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        viewModel.markComplete(600)
        awaitCompletionSettled()

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.learningEventSubmission()),
        )
        viewModel.markComplete(600)
        awaitCompletionSettled()

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertEquals(
            first.getHeader("Idempotency-Key"),
            second.getHeader("Idempotency-Key"),
        )
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        assertTrue(completion() is CompletionState.Accepted)
    }

    @Test
    fun `a replayed submission is labelled as already recorded`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.learningEventSubmission()),
        )

        viewModel.markComplete(600)
        awaitCompletionSettled()

        assertTrue((completion() as CompletionState.Accepted).result.wasReplay)
    }

    @Test
    fun `an accepted completion cannot be submitted again`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.learningEventSubmission()),
        )
        viewModel.markComplete(600)
        awaitCompletionSettled()
        val requestsAfterFirst = harness.server.requestCount

        viewModel.markComplete(600)

        // No second submission: a duplicate would spend a key the backend has
        // already consumed.
        assertEquals(requestsAfterFirst, harness.server.requestCount)
    }

    @Test
    fun `reopening a lesson completed this session does not offer completion again`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.learningEventSubmission()),
        )
        viewModel.markComplete(600)
        awaitCompletionSettled()

        // Reopening the lesson creates a new screen, sharing the session-scoped
        // registry. Without this the app would submit a fresh key and the
        // backend would award XP a second time for the same lesson.
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("lesson-1", "Present simple")),
        )
        val reopened = LessonDetailViewModel(
            repository = CatalogRepository(
                catalogApi = harness.catalogApi,
                catalogDao = database.catalogDao(),
                cacheMetadataDao = database.cacheMetadataDao(),
                json = harness.json,
                timeSource = timeSource,
            ),
            learningRepository = LearningRepository(
                learningApi = harness.learningApi,
                uuidGenerator = UuidV7Generator(timeSource),
                timeSource = timeSource,
                json = harness.json,
            ),
            completedLessons = completedLessons,
            timeSource = timeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "lesson-1")),
        )
        runBlocking {
            awaitCondition(describe = { "reopened lesson to load" }) {
                reopened.uiState.value is LessonDetailUiState.Content
            }
        }

        val state = (reopened.uiState.value as LessonDetailUiState.Content).completion
        assertEquals(CompletionState.AlreadyCompletedThisSession, state)

        val requestsBefore = harness.server.requestCount
        reopened.markComplete(600)
        assertEquals(requestsBefore, harness.server.requestCount)
    }

    @Test
    fun `a lesson the backend shows completed from a previous session does not invite a resubmit`() {
        // A fresh registry simulates a real app restart: the in-memory,
        // session-only registry that made `AlreadyCompletedThisSession` above
        // possible is gone. Only the backend's own completion read model can
        // still know this lesson is done.
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("lesson-1", "Present simple")),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lessonCompletions("lesson-1" to "2026-03-01T08:00:00Z")),
        )
        val afterRestart = LessonDetailViewModel(
            repository = CatalogRepository(
                catalogApi = harness.catalogApi,
                catalogDao = database.catalogDao(),
                cacheMetadataDao = database.cacheMetadataDao(),
                json = harness.json,
                timeSource = timeSource,
            ),
            learningRepository = LearningRepository(
                learningApi = harness.learningApi,
                uuidGenerator = UuidV7Generator(timeSource),
                timeSource = timeSource,
                json = harness.json,
            ),
            completedLessons = CompletedLessonsRegistry(),
            timeSource = timeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "lesson-1")),
        )
        runBlocking {
            awaitCondition(describe = { "restarted lesson to load" }) {
                afterRestart.uiState.value is LessonDetailUiState.Content
            }
        }

        val state = (afterRestart.uiState.value as LessonDetailUiState.Content).completion
        assertEquals(CompletionState.CompletedPreviously("2026-03-01T08:00:00Z"), state)

        val requestsBefore = harness.server.requestCount
        afterRestart.markComplete(600)
        // No submission: the backend already has this lesson recorded as
        // done, and the UI offers no action that could resubmit it.
        assertEquals(requestsBefore, harness.server.requestCount)
    }

    @Test
    fun `the registry is cleared on sign-out`() = runBlocking {
        completedLessons.markCompleted("lesson-1")
        assertTrue(completedLessons.wasCompletedThisSession("lesson-1"))

        completedLessons.clearForSignOut()

        // One learner's completions must not silence the action for the next.
        assertFalse(completedLessons.wasCompletedThisSession("lesson-1"))
    }

    @Test
    fun `a backend rejection is not retryable`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody(Fixtures.error(ApiErrorCodes.LEARNING_UNKNOWN_LESSON)),
        )

        viewModel.markComplete(600)
        awaitCompletionSettled()

        val failed = completion() as CompletionState.Failed
        // The backend decided; resending the same payload would only repeat it.
        assertFalse(failed.canRetry)
        assertEquals(Fixtures.REQUEST_ID, failed.requestId)
    }

    @Test
    fun `a fresh submission after a backend rejection uses a new key`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody(Fixtures.error(ApiErrorCodes.LEARNING_UNKNOWN_LESSON)),
        )
        viewModel.markComplete(600)
        awaitCompletionSettled()

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.learningEventSubmission()),
        )
        viewModel.markComplete(600)
        awaitCompletionSettled()

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertTrue(
            "a rejected submission must not keep its key",
            first.getHeader("Idempotency-Key") != second.getHeader("Idempotency-Key"),
        )
    }
}
