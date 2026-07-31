package com.elenglish.studymentor.data.quiz

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.testing.ApiTestHarness
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class WrongAnswerRepositoryTest {
    private lateinit var harness: ApiTestHarness
    private lateinit var repository: QuizRepository

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        repository = QuizRepository(
            harness.quizApi,
            UuidV7Generator(object : TimeSource {
                override fun nowEpochMillis() = 1_774_000_000_000L
            }),
            harness.json,
        )
    }

    @After
    fun tearDown() = harness.shutdown()

    @Test
    fun `wrong answers are a paginated server read model`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"items":[{"questionId":"q2","quizId":"quiz-1","quizTitle":"Greetings","lessonId":"lesson-1","prompt":"How do you say goodbye?","selectedOptionId":"bad","selectedOptionText":"Go away","correctOptionId":"good","correctOptionText":"See you later","lastAnsweredAt":"2026-07-30T08:00:00Z","wrongCount":2}],"page":1,"pageSize":20,"totalItems":1,"hasNext":false},"meta":{"requestId":"req-1"}}""",
            ),
        )

        val page = (repository.getWrongAnswers() as ApiResult.Success).value

        assertEquals("See you later", page.items.single().correctOptionText)
        assertEquals(2, page.items.single().wrongCount)
        assertFalse(page.hasNext)
        assertEquals(
            "/api/v1/me/wrong-answers?page=1&pageSize=20",
            harness.server.takeRequest().path,
        )
    }
}
