package com.elenglish.studymentor.data.learning

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UUID_V7_REGEX
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
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

class LearningRepositoryTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: LearningRepository

    private var currentMillis = 1_774_000_000_000L
    private val timeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = currentMillis
    }

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        repository = LearningRepository(
            learningApi = harness.learningApi,
            uuidGenerator = UuidV7Generator(timeSource),
            timeSource = timeSource,
            json = harness.json,
        )
    }

    @After
    fun tearDown() = harness.shutdown()

    @Test
    fun `a prepared completion carries a contract-shaped idempotency key`() {
        val pending = repository.prepareCompletion("lesson-1", durationSeconds = 600)

        assertTrue(UUID_V7_REGEX.matches(pending.idempotencyKey))
    }

    @Test
    fun `occurredAt is RFC 3339 UTC`() {
        currentMillis = 1_774_000_000_000L

        val pending = repository.prepareCompletion("lesson-1", 600)

        assertEquals("2026-03-20T09:46:40Z", pending.occurredAt)
        assertTrue(pending.occurredAt.endsWith("Z"))
    }

    @Test
    fun `the submitted payload contains no xp or progress value`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.learningEventSubmission()),
        )
        val pending = repository.prepareCompletion("lesson-1", 600)

        repository.submitCompletion(pending)

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        // XP is derived by the backend from the authoritative lesson. A client
        // that sent a value would be claiming an award it has no right to set.
        assertFalse(body.containsKey("xpEarned"))
        assertFalse(body.containsKey("totalXp"))
        assertFalse(body.containsKey("progress"))
        assertEquals(
            setOf("lessonId", "occurredAt", "durationSeconds", "eventType"),
            body.keys,
        )
        assertEquals("lesson.completed", body["eventType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the idempotency key is sent as a header`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.learningEventSubmission()),
        )
        val pending = repository.prepareCompletion("lesson-1", 600)

        repository.submitCompletion(pending)

        assertEquals(
            pending.idempotencyKey,
            harness.server.takeRequest().getHeader("Idempotency-Key"),
        )
    }

    @Test
    fun `a 201 is reported as a fresh acceptance`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.learningEventSubmission(xpEarned = 20, totalXp = 20)),
        )

        val result = repository.submitCompletion(repository.prepareCompletion("lesson-1", 600))

        val completion = (result as ApiResult.Success).value
        assertFalse(completion.wasReplay)
        assertEquals(20, completion.event.xpEarned)
        assertEquals(20, completion.progress.totalXp)
    }

    @Test
    fun `a 200 is reported as a replay of an already accepted submission`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.learningEventSubmission()),
        )

        val result = repository.submitCompletion(repository.prepareCompletion("lesson-1", 600))

        assertTrue((result as ApiResult.Success).value.wasReplay)
    }

    @Test
    fun `retrying sends the identical key and payload, so the server can replay it`() = runTest {
        val pending = repository.prepareCompletion("lesson-1", 600)

        // First attempt fails at the network; the outcome is unknown.
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        repository.submitCompletion(pending)

        // The caller resends the same object, unchanged.
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.learningEventSubmission()),
        )
        repository.submitCompletion(pending)

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()

        assertEquals(
            first.getHeader("Idempotency-Key"),
            second.getHeader("Idempotency-Key"),
        )
        // Byte-identical: the backend hashes the client-owned fields to decide
        // whether a repeated key is the same submission.
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
    }

    @Test
    fun `two separate completions use different keys`() {
        val first = repository.prepareCompletion("lesson-1", 600)
        currentMillis += 5_000
        val second = repository.prepareCompletion("lesson-1", 600)

        assertTrue(first.idempotencyKey != second.idempotencyKey)
    }

    @Test
    fun `a reused key with different data is surfaced as a conflict`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody(Fixtures.error(ApiErrorCodes.LEARNING_IDEMPOTENCY_KEY_REUSED)),
        )

        val result = repository.submitCompletion(repository.prepareCompletion("lesson-1", 600))

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(ApiErrorCodes.LEARNING_IDEMPOTENCY_KEY_REUSED, error.code)
        assertEquals(Fixtures.REQUEST_ID, error.requestId)
    }

    @Test
    fun `an unknown lesson is surfaced with its contract code`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody(Fixtures.error(ApiErrorCodes.LEARNING_UNKNOWN_LESSON)),
        )

        val result = repository.submitCompletion(repository.prepareCompletion("missing", 600))

        assertEquals(
            ApiErrorCodes.LEARNING_UNKNOWN_LESSON,
            ((result as ApiResult.Failure).error as ApiError.Backend).code,
        )
    }

    @Test
    fun `a negative duration is rejected before it reaches the network`() {
        val error = runCatching { repository.prepareCompletion("lesson-1", -1) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, harness.server.requestCount)
    }

    @Test
    fun `progress is read from the backend projection verbatim`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.progressEnvelope(totalXp = 40)),
        )

        val progress = (repository.getProgress() as ApiResult.Success).value

        assertEquals(40, progress.totalXp)
        assertEquals(1, progress.completedLessons)
        assertEquals(3, progress.totalLessons)
        assertEquals(33.33, progress.completionPercentage, 0.001)
        assertEquals(600, progress.learningTimeSeconds)
    }

    @Test
    fun `no completions is an empty list, not a failure`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.lessonCompletions()),
        )

        val completions = (repository.getLessonCompletions() as ApiResult.Success).value

        assertTrue(completions.isEmpty())
    }

    @Test
    fun `completions are mapped verbatim from the backend read model`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.lessonCompletions(
                    "lesson-1" to "2026-07-20T08:00:00Z",
                    "lesson-2" to "2026-07-21T08:00:00Z",
                ),
            ),
        )

        val completions = (repository.getLessonCompletions() as ApiResult.Success).value

        assertEquals(2, completions.size)
        assertEquals("lesson-1", completions[0].lessonId)
        assertEquals("2026-07-20T08:00:00Z", completions[0].completedAt)
        assertEquals("lesson-2", completions[1].lessonId)
    }

    @Test
    fun `a session failure reading completions carries its request id`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )

        val result = repository.getLessonCompletions()

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(ApiErrorCodes.AUTH_SESSION_EXPIRED, error.code)
        assertEquals(Fixtures.REQUEST_ID, error.requestId)
    }
}
