package com.elenglish.studymentor.ui.flashcards

import androidx.lifecycle.SavedStateHandle
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.flashcard.FlashcardRepository
import com.elenglish.studymentor.domain.model.ReviewOutcome
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
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlashcardReviewViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: FlashcardRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        val timeSource = object : TimeSource {
            override fun nowEpochMillis(): Long = 1_774_000_000_000L
        }
        repository = FlashcardRepository(
            flashcardApi = harness.flashcardApi,
            uuidGenerator = UuidV7Generator(timeSource),
            timeSource = timeSource,
            json = harness.json,
        )
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
    }

    private fun viewModel(): FlashcardReviewViewModel =
        FlashcardReviewViewModel(repository, SavedStateHandle(mapOf("deckId" to "deck-1")))

    private fun FlashcardReviewViewModel.awaitLoaded() = runBlocking {
        awaitCondition(describe = { "queue to load" }) {
            uiState.value !is FlashcardReviewUiState.Loading
        }
    }

    private fun FlashcardReviewViewModel.awaitSubmitted() = runBlocking {
        awaitCondition(describe = { "review to settle" }) {
            val s = uiState.value
            s !is FlashcardReviewUiState.Reviewing || s.submission !is ReviewSubmission.Submitting
        }
    }

    private fun reviewing(vm: FlashcardReviewViewModel) =
        vm.uiState.value as FlashcardReviewUiState.Reviewing

    private fun enqueueQueueOf2() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.flashcardQueue(
                    "deck-1",
                    Triple("card-1", "Good morning", 1),
                    Triple("card-2", "Goodbye", 1),
                ),
            ),
        )
    }

    @Test
    fun `an empty queue goes straight to a nothing-due summary`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.flashcardQueue("deck-1")),
        )

        val vm = viewModel()
        vm.awaitLoaded()

        val done = vm.uiState.value as FlashcardReviewUiState.Done
        assertEquals(0, done.reviewed)
    }

    @Test
    fun `the card back is hidden until revealed`() {
        enqueueQueueOf2()
        val vm = viewModel()
        vm.awaitLoaded()

        assertFalse(reviewing(vm).revealed)
        vm.reveal()
        assertTrue(reviewing(vm).revealed)
    }

    @Test
    fun `answering advances to the next card and resets the reveal`() {
        enqueueQueueOf2()
        val vm = viewModel()
        vm.awaitLoaded()
        vm.reveal()

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult()),
        )
        vm.answer(ReviewOutcome.Known)
        vm.awaitSubmitted()

        val state = reviewing(vm)
        assertEquals(1, state.index)
        assertFalse(state.revealed)
        assertEquals(1, state.reviewed)
        assertEquals(1, state.known)
    }

    @Test
    fun `answering every card ends the session with the known tally`() {
        enqueueQueueOf2()
        val vm = viewModel()
        vm.awaitLoaded()

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult()),
        )
        vm.reveal(); vm.answer(ReviewOutcome.Known); vm.awaitSubmitted()

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult(box = 1)),
        )
        vm.reveal(); vm.answer(ReviewOutcome.Forgot); vm.awaitSubmitted()

        val done = vm.uiState.value as FlashcardReviewUiState.Done
        assertEquals(2, done.reviewed)
        assertEquals(1, done.known)
    }

    @Test
    fun `the review submits the outcome the learner chose`() {
        enqueueQueueOf2()
        val vm = viewModel()
        vm.awaitLoaded()
        harness.server.takeRequest() // drain the queue-load GET
        vm.reveal()

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult(box = 1)),
        )
        vm.answer(ReviewOutcome.Forgot)
        vm.awaitSubmitted()

        val body = harness.server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"outcome\":\"forgot\""))
        assertTrue(body.contains("\"cardId\":\"card-1\""))
    }

    @Test
    fun `a network failure keeps the card and offers a retry`() {
        enqueueQueueOf2()
        val vm = viewModel()
        vm.awaitLoaded()
        vm.reveal()

        harness.goOffline()
        vm.answer(ReviewOutcome.Known)
        vm.awaitSubmitted()

        val state = reviewing(vm)
        val submission = state.submission as ReviewSubmission.Failed
        assertEquals(CatalogErrorKind.Network, submission.kind)
        assertTrue(submission.canRetry)
        // Still on the same card; nothing was recorded.
        assertEquals(0, state.index)
        assertEquals(0, state.reviewed)
    }

    @Test
    fun `retrying after a network failure reuses the same key and payload`() {
        enqueueQueueOf2()
        val vm = viewModel()
        vm.awaitLoaded()
        harness.server.takeRequest() // drain the queue-load GET
        vm.reveal()

        harness.server.enqueue(
            MockResponse().setResponseCode(500).setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        vm.answer(ReviewOutcome.Known)
        vm.awaitSubmitted()

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.flashcardReviewResult()),
        )
        vm.answer(ReviewOutcome.Known)
        vm.awaitSubmitted()

        val first = harness.server.takeRequest()
        val second = harness.server.takeRequest()
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        // The retry succeeded, so the session advanced.
        assertTrue(reviewing(vm).index == 1)
    }

    @Test
    fun `a session with one due card ends after a single review`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.flashcardQueue("deck-1", Triple("card-1", "Good morning", 3))),
        )
        val vm = viewModel()
        vm.awaitLoaded()

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(Fixtures.flashcardReviewResult(box = 4)),
        )
        vm.reveal(); vm.answer(ReviewOutcome.Known); vm.awaitSubmitted()

        assertTrue(vm.uiState.value is FlashcardReviewUiState.Done)
    }
}
