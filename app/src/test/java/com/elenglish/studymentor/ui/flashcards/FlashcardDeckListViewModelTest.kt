package com.elenglish.studymentor.ui.flashcards

import androidx.lifecycle.SavedStateHandle
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.flashcard.FlashcardRepository
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
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The lesson-scoped flashcard deck list: loading, content, empty and failed
 * states, plus retry. Mirrors the harness/fixture pattern already used by
 * [FlashcardReviewViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlashcardDeckListViewModelTest {

    private lateinit var harness: ApiTestHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
    }

    private fun viewModel(): FlashcardDeckListViewModel {
        val timeSource = object : TimeSource {
            override fun nowEpochMillis(): Long = 1_774_000_000_000L
        }
        return FlashcardDeckListViewModel(
            repository = FlashcardRepository(
                flashcardApi = harness.flashcardApi,
                uuidGenerator = UuidV7Generator(timeSource),
                timeSource = timeSource,
                json = harness.json,
            ),
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "lesson-1")),
        )
    }

    @Test
    fun `decks load into content`() = runBlocking {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.flashcardDecks("lesson-1", "deck-1" to "Greeting basics"),
            ),
        )
        val viewModel = viewModel()

        awaitCondition { viewModel.uiState.value is FlashcardDeckListUiState.Content }

        val content = viewModel.uiState.value as FlashcardDeckListUiState.Content
        assertEquals(1, content.decks.size)
        assertEquals("Greeting basics", content.decks.first().name)
    }

    @Test
    fun `no decks lands on the empty state, not a fabricated deck`() = runBlocking {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.flashcardDecks("lesson-1")),
        )
        val viewModel = viewModel()

        awaitCondition { viewModel.uiState.value is FlashcardDeckListUiState.Empty }
    }

    @Test
    fun `a load failure surfaces the request id and retry reloads`() = runBlocking {
        harness.server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val viewModel = viewModel()

        awaitCondition { viewModel.uiState.value is FlashcardDeckListUiState.Failed }
        val failed = viewModel.uiState.value as FlashcardDeckListUiState.Failed
        assertTrue(failed.requestId == null || failed.requestId!!.isNotBlank())

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.flashcardDecks("lesson-1", "deck-1" to "Greeting basics"),
            ),
        )
        viewModel.load()

        awaitCondition { viewModel.uiState.value is FlashcardDeckListUiState.Content }
    }
}
