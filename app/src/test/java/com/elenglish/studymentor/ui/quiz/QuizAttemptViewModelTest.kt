package com.elenglish.studymentor.ui.quiz

import androidx.lifecycle.SavedStateHandle
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.quiz.QuizRepository
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import com.elenglish.studymentor.testing.awaitCondition
import com.elenglish.studymentor.testing.awaitQuiescence
import com.elenglish.studymentor.ui.catalog.CatalogErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuizAttemptViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var viewModel: QuizAttemptViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizDetail()),
        )
        viewModel = QuizAttemptViewModel(
            repository = QuizRepository(
                quizApi = harness.quizApi,
                uuidGenerator = UuidV7Generator(
                    object : TimeSource {
                        override fun nowEpochMillis(): Long = 1_774_000_000_000L
                    },
                ),
                json = harness.json,
            ),
            savedStateHandle = SavedStateHandle(mapOf("quizId" to "quiz-1")),
        )
        awaitLoaded()
        harness.server.takeRequest()
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
    }

    private fun awaitLoaded() = runBlocking {
        awaitCondition(describe = { "quiz to load" }) {
            viewModel.uiState.value !is QuizUiState.Loading
        }
    }

    private fun awaitSubmitted() = runBlocking {
        awaitCondition(describe = { "submission to settle" }) {
            content().submission !is QuizSubmissionState.Submitting
        }
    }

    private fun content(): QuizUiState.Content = viewModel.uiState.value as QuizUiState.Content

    private fun answerAll() {
        viewModel.selectOption("q1", "q1o2")
        viewModel.selectOption("q2", "q2o2")
    }

    @Test
    fun `submission is blocked until every question is answered`() {
        assertEquals(0, content().answeredCount)
        assertFalse(content().canSubmit)

        viewModel.selectOption("q1", "q1o2")
        assertEquals(1, content().answeredCount)
        assertFalse(content().canSubmit)

        viewModel.selectOption("q2", "q2o1")
        assertEquals(2, content().answeredCount)
        assertTrue(content().canSubmit)
    }

    @Test
    fun `selecting another option replaces the previous single choice`() {
        viewModel.selectOption("q1", "q1o1")
        viewModel.selectOption("q1", "q1o2")

        assertEquals(1, content().answeredCount)
        assertEquals("q1o2", content().selections["q1"])
    }

    @Test
    fun `answers are submitted in the backend's question order`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )
        // Answer the second question first; order on the wire must still follow
        // the question order the backend sent, so retries stay byte-identical.
        viewModel.selectOption("q2", "q2o2")
        viewModel.selectOption("q1", "q1o2")

        viewModel.submit()
        awaitSubmitted()

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        val ids = body["answers"]!!.jsonArray.map { it.jsonObject["questionId"]!!.jsonPrimitive.content }
        assertEquals(listOf("q1", "q2"), ids)
    }

    @Test
    fun `the server's score is displayed, not one derived on the device`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                // The learner picked q2o2, which the server marks wrong.
                Fixtures.quizAttemptResult(correctAnswers = 1, scorePercentage = 50.0),
            ),
        )
        answerAll()

        viewModel.submit()
        awaitSubmitted()

        val scored = content().submission as QuizSubmissionState.Scored
        assertEquals(1, scored.result.correctAnswers)
        assertEquals(50.0, scored.result.scorePercentage, 0.001)
        assertFalse(scored.result.wasReplay)
    }

    @Test
    fun `wrong answer review carries the server's correct option`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )
        answerAll()

        viewModel.submit()
        awaitSubmitted()

        val results = (content().submission as QuizSubmissionState.Scored).result.questionResults
        val wrong = results.first { !it.correct }
        assertEquals("q2", wrong.questionId)
        assertEquals("q2o2", wrong.selectedOptionId)
        // The correct option is the server's, not a local answer key.
        assertEquals("q2o1", wrong.correctOptionId)
    }

    @Test
    fun `answers are locked once the attempt has been scored`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )
        answerAll()
        viewModel.submit()
        awaitSubmitted()

        viewModel.selectOption("q1", "q1o1")

        assertEquals("q1o2", content().selections["q1"])
        assertFalse(content().canSubmit)
    }

    @Test
    fun `a scored attempt is not submitted again`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )
        answerAll()
        viewModel.submit()
        awaitSubmitted()
        val requestsAfterFirst = harness.server.requestCount

        viewModel.submit()

        assertEquals(requestsAfterFirst, harness.server.requestCount)
    }

    @Test
    fun `a replayed attempt is labelled as already submitted`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizAttemptResult()),
        )
        answerAll()

        viewModel.submit()
        awaitSubmitted()

        assertTrue((content().submission as QuizSubmissionState.Scored).result.wasReplay)
    }

    @Test
    fun `an unknown outcome is retryable with the same key`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        answerAll()
        viewModel.submit()
        awaitSubmitted()

        assertTrue((content().submission as QuizSubmissionState.Failed).canRetry)

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizAttemptResult()),
        )
        viewModel.submit()
        awaitSubmitted()

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
    }

    @Test
    fun `a definitive rejection is not retryable`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody(Fixtures.error("quiz.invalid_submission")),
        )
        answerAll()

        viewModel.submit()
        awaitSubmitted()

        val failed = content().submission as QuizSubmissionState.Failed
        assertFalse(failed.canRetry)
        assertEquals(Fixtures.REQUEST_ID, failed.requestId)
    }

    @Test
    fun `an expired session is reported as unauthorized`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )
        answerAll()

        viewModel.submit()
        awaitSubmitted()

        assertEquals(
            CatalogErrorKind.Unauthorized,
            (content().submission as QuizSubmissionState.Failed).kind,
        )
    }
}
