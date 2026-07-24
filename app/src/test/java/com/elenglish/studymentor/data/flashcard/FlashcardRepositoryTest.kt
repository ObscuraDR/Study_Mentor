package com.elenglish.studymentor.data.flashcard

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UUID_V7_REGEX
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.domain.model.ReviewOutcome
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

class FlashcardRepositoryTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: FlashcardRepository

    private var currentMillis = 1_774_000_000_000L

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        val timeSource = object : TimeSource {
            override fun nowEpochMillis(): Long = currentMillis
        }
        repository = FlashcardRepository(
            flashcardApi = harness.flashcardApi,
            uuidGenerator = UuidV7Generator(timeSource),
            timeSource = timeSource,
            json = harness.json,
        )
    }

    @After
    fun tearDown() = harness.shutdown()

    @Test
    fun `listing decks sends the required lessonId query parameter`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.flashcardDecks("lesson-1", "deck-1" to "Greeting basics")),
        )

        val result = repository.listDecks("lesson-1")

        assertEquals("Greeting basics", (result as ApiResult.Success).value.single().name)
        assertTrue(harness.server.takeRequest().path!!.contains("lessonId=lesson-1"))
    }

    @Test
    fun `the queue carries each card with its server review state`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.flashcardQueue("deck-1", Triple("card-1", "Good morning", 1)),
            ),
        )

        val entry = (repository.getQueue("deck-1") as ApiResult.Success).value.single()

        assertEquals("Good morning", entry.card.front)
        assertEquals(1, entry.state.box)
        assertEquals("leitner-5box-v1", entry.state.algorithmVersion)
        assertEquals(0, entry.state.memoryStrengthPercent)
    }

    @Test
    fun `a review is keyed with a contract-shaped uuid v7`() {
        val pending = repository.prepareReview("card-1", ReviewOutcome.Known)
        assertTrue(UUID_V7_REGEX.matches(pending.idempotencyKey))
    }

    @Test
    fun `the submitted payload carries only cardId, outcome and reviewedAt`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult()),
        )

        repository.submitReview(repository.prepareReview("card-1", ReviewOutcome.Known))

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("cardId", "outcome", "reviewedAt"), body.keys)
        // Box, due date and any reward value are server-owned and forbidden.
        assertFalse(body.containsKey("box"))
        assertFalse(body.containsKey("dueAt"))
        assertFalse(body.containsKey("xp"))
        assertEquals("known", body["outcome"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the idempotency key is sent as a header`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult()),
        )
        val pending = repository.prepareReview("card-1", ReviewOutcome.Known)

        repository.submitReview(pending)

        assertEquals(
            pending.idempotencyKey,
            harness.server.takeRequest().getHeader("Idempotency-Key"),
        )
    }

    @Test
    fun `the server's recalculated box is surfaced, not one derived on the device`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.flashcardReviewResult(box = 2, dueAt = "2026-07-25T08:00:00Z")),
        )

        val result = repository.submitReview(repository.prepareReview("card-1", ReviewOutcome.Known))

        val state = (result as ApiResult.Success).value.state
        assertEquals(2, state.box)
        assertEquals("2026-07-25T08:00:00Z", state.dueAt)
        assertFalse(result.value.wasReplay)
    }

    @Test
    fun `a 200 is reported as a replay`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.flashcardReviewResult()),
        )

        val result = repository.submitReview(repository.prepareReview("card-1", ReviewOutcome.Known))

        assertTrue((result as ApiResult.Success).value.wasReplay)
    }

    @Test
    fun `retrying resends the identical key and payload`() = runTest {
        val pending = repository.prepareReview("card-1", ReviewOutcome.Known)

        harness.server.enqueue(
            MockResponse().setResponseCode(500).setBody(Fixtures.error("server.internal")),
        )
        repository.submitReview(pending)

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.flashcardReviewResult()),
        )
        repository.submitReview(pending)

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
    }

    @Test
    fun `a reused key with different data is surfaced as a conflict`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody(Fixtures.error("flashcard.idempotency_key_reused")),
        )

        val result = repository.submitReview(repository.prepareReview("card-1", ReviewOutcome.Forgot))

        assertEquals(
            "flashcard.idempotency_key_reused",
            ((result as ApiResult.Failure).error as ApiError.Backend).code,
        )
    }

    @Test
    fun `reset reports how many cards were returned to box 1`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardResetResult(cardsReset = 3)),
        )

        val result = repository.resetDeck("deck-1")

        assertEquals(3, (result as ApiResult.Success).value)
        val request = harness.server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
    }

    @Test
    fun `the forgot outcome is sent verbatim`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult(box = 1)),
        )

        repository.submitReview(repository.prepareReview("card-1", ReviewOutcome.Forgot))

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("forgot", body["outcome"]?.jsonPrimitive?.content)
    }
}
