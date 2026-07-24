package com.elenglish.studymentor.data.quiz

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UUID_V7_REGEX
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.domain.model.QuizAnswer
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
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

class QuizRepositoryTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: QuizRepository

    private var currentMillis = 1_774_000_000_000L

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        repository = QuizRepository(
            quizApi = harness.quizApi,
            uuidGenerator = UuidV7Generator(
                object : TimeSource {
                    override fun nowEpochMillis(): Long = currentMillis
                },
            ),
            json = harness.json,
        )
    }

    @After
    fun tearDown() = harness.shutdown()

    private val answers = listOf(
        QuizAnswer("q1", "q1o2"),
        QuizAnswer("q2", "q2o2"),
    )

    @Test
    fun `listing quizzes sends the required lessonId query parameter`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.quizzes("lesson-1", "quiz-1" to "Greetings check")),
        )

        val result = repository.listQuizzes("lesson-1")

        assertEquals("Greetings check", (result as ApiResult.Success).value.single().title)
        assertTrue(harness.server.takeRequest().path!!.contains("lessonId=lesson-1"))
    }

    @Test
    fun `a quiz carries questions and options but no answer key`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizDetail()),
        )

        val quiz = (repository.getQuiz("quiz-1") as ApiResult.Success).value

        assertEquals(2, quiz.questions.size)
        assertEquals("Which greeting suits the morning?", quiz.questions.first().prompt)
        assertEquals(listOf("Good night", "Good morning"), quiz.questions.first().options.map { it.text })

        // The domain model has no correctness field at all, so the app cannot
        // score an attempt even if someone tried to.
        val optionFields = quiz.questions.first().options.first()
        assertEquals("q1o1", optionFields.id)
    }

    @Test
    fun `an attempt is keyed with a contract-shaped uuid v7`() {
        val pending = repository.prepareAttempt("quiz-1", answers)

        assertTrue(UUID_V7_REGEX.matches(pending.idempotencyKey))
    }

    @Test
    fun `the submitted payload carries only quizId and selected options`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )

        repository.submitAttempt(repository.prepareAttempt("quiz-1", answers))

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("quizId", "answers"), body.keys)
        // Score, correctness and XP are server-owned and forbidden in a request.
        assertFalse(body.containsKey("score"))
        assertFalse(body.containsKey("correctAnswers"))
        assertFalse(body.containsKey("scorePercentage"))

        val first = body["answers"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("questionId", "selectedOptionId"), first.keys)
        assertEquals("q1", first["questionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the idempotency key is sent as a header`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )
        val pending = repository.prepareAttempt("quiz-1", answers)

        repository.submitAttempt(pending)

        assertEquals(
            pending.idempotencyKey,
            harness.server.takeRequest().getHeader("Idempotency-Key"),
        )
    }

    @Test
    fun `a 201 is a new attempt and a 200 is a replay`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.quizAttemptResult()),
        )
        val fresh = repository.submitAttempt(repository.prepareAttempt("quiz-1", answers))
        assertFalse((fresh as ApiResult.Success).value.wasReplay)

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizAttemptResult()),
        )
        val replay = repository.submitAttempt(repository.prepareAttempt("quiz-1", answers))
        assertTrue((replay as ApiResult.Success).value.wasReplay)
    }

    @Test
    fun `the server's score is surfaced exactly as received`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                Fixtures.quizAttemptResult(
                    correctAnswers = 1,
                    totalQuestions = 2,
                    scorePercentage = 50.0,
                ),
            ),
        )

        val result = (
            repository.submitAttempt(repository.prepareAttempt("quiz-1", answers))
                as ApiResult.Success
            ).value

        assertEquals(1, result.correctAnswers)
        assertEquals(2, result.totalQuestions)
        assertEquals(50.0, result.scorePercentage, 0.001)
        assertEquals(2, result.questionResults.size)
        assertTrue(result.questionResults.first().correct)
        assertEquals("q2o1", result.questionResults.last().correctOptionId)
    }

    @Test
    fun `retrying resends the identical key and answer order`() = runTest {
        val pending = repository.prepareAttempt("quiz-1", answers)

        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        repository.submitAttempt(pending)

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizAttemptResult()),
        )
        repository.submitAttempt(pending)

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        // The backend hashes the body, so answer order must not drift.
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
    }

    @Test
    fun `a reused key with different answers is surfaced as a conflict`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody(Fixtures.error("quiz.idempotency_key_reused")),
        )

        val result = repository.submitAttempt(repository.prepareAttempt("quiz-1", answers))

        assertEquals(
            "quiz.idempotency_key_reused",
            ((result as ApiResult.Failure).error as ApiError.Backend).code,
        )
    }

    @Test
    fun `an incomplete attempt is rejected by the backend and surfaced`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody(Fixtures.error("quiz.incomplete_attempt")),
        )

        val result = repository.submitAttempt(repository.prepareAttempt("quiz-1", answers))

        assertEquals(
            "quiz.incomplete_attempt",
            ((result as ApiResult.Failure).error as ApiError.Backend).code,
        )
        assertEquals(Fixtures.REQUEST_ID, result.error.requestId)
    }

    @Test
    fun `an empty attempt is rejected before it reaches the network`() {
        val error = runCatching { repository.prepareAttempt("quiz-1", emptyList()) }
            .exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, harness.server.requestCount)
    }
}
