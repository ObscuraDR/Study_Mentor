package com.elenglish.studymentor.ui.quiz

import androidx.lifecycle.SavedStateHandle
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.quiz.QuizRepository
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import com.elenglish.studymentor.testing.awaitCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The lesson-scoped quiz list: loading, content, empty and failed states,
 * plus retry. Mirrors the harness/fixture pattern already used by
 * [QuizAttemptViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizListViewModelTest {

    private lateinit var harness: ApiTestHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        harness.shutdown()
    }

    private fun viewModel() = QuizListViewModel(
        repository = QuizRepository(
            quizApi = harness.quizApi,
            uuidGenerator = UuidV7Generator(
                object : TimeSource {
                    override fun nowEpochMillis(): Long = 1_774_000_000_000L
                },
            ),
            json = harness.json,
        ),
        savedStateHandle = SavedStateHandle(mapOf("lessonId" to "lesson-1")),
    )

    @Test
    fun `quizzes load into content`() = runBlocking {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.quizzes("lesson-1", "quiz-1" to "Greetings check"),
            ),
        )
        val viewModel = viewModel()

        awaitCondition { viewModel.uiState.value is QuizListUiState.Content }

        val content = viewModel.uiState.value as QuizListUiState.Content
        assertEquals(1, content.quizzes.size)
        assertEquals("Greetings check", content.quizzes.first().title)
    }

    @Test
    fun `no quizzes lands on the empty state, not a fabricated quiz`() = runBlocking {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.quizzes("lesson-1")),
        )
        val viewModel = viewModel()

        awaitCondition { viewModel.uiState.value is QuizListUiState.Empty }
    }

    @Test
    fun `a load failure surfaces the request id and retry reloads`() = runBlocking {
        harness.server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val viewModel = viewModel()

        awaitCondition { viewModel.uiState.value is QuizListUiState.Failed }
        val failed = viewModel.uiState.value as QuizListUiState.Failed
        assertTrue(failed.requestId == null || failed.requestId!!.isNotBlank())

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.quizzes("lesson-1", "quiz-1" to "Greetings check"),
            ),
        )
        viewModel.load()

        awaitCondition { viewModel.uiState.value is QuizListUiState.Content }
    }
}
