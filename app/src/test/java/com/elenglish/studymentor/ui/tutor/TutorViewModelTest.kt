package com.elenglish.studymentor.ui.tutor

import androidx.lifecycle.SavedStateHandle
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.tutor.TutorRepository
import com.elenglish.studymentor.domain.model.TutorAnswerStatus
import com.elenglish.studymentor.domain.model.TutorTurn
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import com.elenglish.studymentor.testing.awaitCondition
import com.elenglish.studymentor.testing.awaitQuiescence
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TutorViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var viewModel: TutorViewModel

    private var currentMillis = 1_774_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        val generator = UuidV7Generator(
            object : TimeSource {
                override fun nowEpochMillis(): Long = currentMillis
            },
        )
        viewModel = TutorViewModel(
            repository = TutorRepository(
                tutorApi = harness.tutorApi,
                uuidGenerator = generator,
                json = harness.json,
            ),
            uuidGenerator = generator,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "lesson-1")),
        )
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
    }

    private fun awaitSettled() = runBlocking {
        awaitCondition(describe = { "tutor request to settle" }) { !viewModel.uiState.value.sending }
    }

    private fun ask(text: String = "When do I use 'good evening'?") {
        viewModel.onDraftChange(text)
        viewModel.send()
        awaitSettled()
    }

    @Test
    fun `sending is blocked until a question is typed`() {
        assertFalse(viewModel.uiState.value.canSend)

        viewModel.onDraftChange("   ")
        assertFalse(viewModel.uiState.value.canSend)

        viewModel.onDraftChange("Why?")
        assertTrue(viewModel.uiState.value.canSend)
    }

    @Test
    fun `a message longer than the contract allows is blocked locally`() {
        viewModel.onDraftChange("a".repeat(2001))

        assertTrue(viewModel.uiState.value.draftTooLong)
        assertFalse(viewModel.uiState.value.canSend)
        assertEquals(0, harness.server.requestCount)
    }

    @Test
    fun `the question appears in the transcript and the input clears`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.tutorResponse()),
        )

        ask("When do I use 'good evening'?")

        val turns = viewModel.uiState.value.turns
        assertEquals("When do I use 'good evening'?", (turns.first() as TutorTurn.Question).text)
        assertEquals("", viewModel.uiState.value.draft)
    }

    @Test
    fun `a completed answer is added to the transcript`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.tutorResponse(answer = "Use it after 6pm.")),
        )

        ask()

        val answer = viewModel.uiState.value.turns.last() as TutorTurn.Answer
        assertEquals("Use it after 6pm.", answer.answer.answer)
        assertEquals(TutorAnswerStatus.Completed, answer.answer.status)
        assertFalse(answer.answer.wasReplay)
    }

    @Test
    fun `a refusal is a successful response, not an error`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.tutorResponse(answer = "n/a", status = "refused")),
        )

        ask()

        val answer = viewModel.uiState.value.turns.last() as TutorTurn.Answer
        assertEquals(TutorAnswerStatus.Refused, answer.answer.status)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `a truncated answer keeps its status so the ui can say it was cut short`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.tutorResponse(answer = "Use it aft", status = "truncated")),
        )

        ask()

        val answer = viewModel.uiState.value.turns.last() as TutorTurn.Answer
        assertEquals(TutorAnswerStatus.Truncated, answer.answer.status)
    }

    @Test
    fun `an unknown status is treated as a refusal, not a complete answer`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.tutorResponse(status = "moderated_v2")),
        )

        ask()

        // A status this client version does not know must not be presented as a
        // full answer.
        val answer = viewModel.uiState.value.turns.last() as TutorTurn.Answer
        assertEquals(TutorAnswerStatus.Refused, answer.answer.status)
    }

    @Test
    fun `the request carries only lessonId and message`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.tutorResponse()),
        )

        ask("Explain please")

        val request = harness.server.takeRequest()
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("lessonId", "message"), body.keys)
        assertEquals("lesson-1", body["lessonId"]?.jsonPrimitive?.content)
        assertNotNull(request.getHeader("Idempotency-Key"))
    }

    @Test
    fun `a rate limit is reported and stays retryable`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody(Fixtures.error(ApiErrorCodes.RATE_LIMIT_EXCEEDED)),
        )

        ask()

        val failure = viewModel.uiState.value.failure!!
        assertEquals(TutorErrorKind.RateLimited, failure.kind)
        assertTrue(failure.canRetry)
        assertEquals(Fixtures.REQUEST_ID, failure.requestId)
    }

    @Test
    fun `a provider error is reported`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(502).setBody(Fixtures.error("ai.provider_error")),
        )

        ask()

        assertEquals(TutorErrorKind.ProviderUnavailable, viewModel.uiState.value.failure?.kind)
    }

    @Test
    fun `a provider timeout is reported`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(504).setBody(Fixtures.error("ai.timeout")),
        )

        ask()

        assertEquals(TutorErrorKind.ProviderTimeout, viewModel.uiState.value.failure?.kind)
    }

    @Test
    fun `an unknown lesson is reported and is not retryable`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody(Fixtures.error(ApiErrorCodes.LEARNING_RESOURCE_NOT_FOUND)),
        )

        ask()

        val failure = viewModel.uiState.value.failure!!
        assertEquals(TutorErrorKind.LessonNotFound, failure.kind)
        assertFalse(failure.canRetry)
    }

    @Test
    fun `retrying resends the identical question under the same key`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(504).setBody(Fixtures.error("ai.timeout")),
        )
        ask("Explain please")
        assertTrue(viewModel.uiState.value.failure!!.canRetry)

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.tutorResponse()),
        )
        viewModel.send()
        awaitSettled()

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertEquals(
            first.getHeader("Idempotency-Key"),
            second.getHeader("Idempotency-Key"),
        )
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        // The replay is surfaced as an answer, and the question is not duplicated.
        assertEquals(2, viewModel.uiState.value.turns.size)
    }

    @Test
    fun `a retry does not add the question to the transcript twice`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(502).setBody(Fixtures.error("ai.provider_error")),
        )
        ask("Explain please")
        assertEquals(1, viewModel.uiState.value.turns.size)

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.tutorResponse()),
        )
        viewModel.send()
        awaitSettled()

        val questions = viewModel.uiState.value.turns.filterIsInstance<TutorTurn.Question>()
        assertEquals(1, questions.size)
    }

    @Test
    fun `a second question continues the same on-screen conversation`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.tutorResponse()),
        )
        ask("First question")

        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.tutorResponse(responseId = "0191f3a0-7d5c-7b3a-9f11-00000000ab1f")),
        )
        ask("Second question")

        assertEquals(4, viewModel.uiState.value.turns.size)
    }

    @Test
    fun `two questions use different idempotency keys`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.tutorResponse()),
        )
        ask("First question")

        currentMillis += 5_000
        harness.server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(Fixtures.tutorResponse(responseId = "0191f3a0-7d5c-7b3a-9f11-00000000ab1f")),
        )
        ask("Second question")

        val first = harness.server.takeRequest().getHeader("Idempotency-Key")
        val second = harness.server.takeRequest().getHeader("Idempotency-Key")
        assertTrue(first != second)
    }
}
