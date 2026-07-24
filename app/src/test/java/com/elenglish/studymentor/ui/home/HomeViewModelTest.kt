package com.elenglish.studymentor.ui.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.catalog.CatalogRepository
import com.elenglish.studymentor.data.flashcard.FlashcardRepository
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.data.local.StudyMentorDatabase
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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [respondByStatus] gives per-path control over HTTP status, unlike
 * [ApiTestHarness.respondByPath] which always answers `200`. Home fires
 * progress and the catalog chain concurrently (like [CatalogRepository]'s own
 * concurrent reads), so every test here routes by path rather than by
 * arrival order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var database: StudyMentorDatabase
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var learningRepository: LearningRepository
    private lateinit var flashcardRepository: FlashcardRepository

    private val timeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = 1_774_000_000_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build()
        catalogRepository = CatalogRepository(
            catalogApi = harness.catalogApi,
            catalogDao = database.catalogDao(),
            cacheMetadataDao = database.cacheMetadataDao(),
            json = harness.json,
            timeSource = timeSource,
        )
        learningRepository = LearningRepository(
            learningApi = harness.learningApi,
            uuidGenerator = UuidV7Generator(timeSource),
            timeSource = timeSource,
            json = harness.json,
        )
        flashcardRepository = FlashcardRepository(
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
        database.close()
    }

    private fun newViewModel() = HomeViewModel(learningRepository, catalogRepository, flashcardRepository)

    private fun respondByStatus(routes: Map<String, Pair<Int, String>>) {
        harness.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val (status, body) = routes.entries.firstOrNull { path.contains(it.key) }?.value
                    ?: return MockResponse().setResponseCode(404)
                return MockResponse().setResponseCode(status).setBody(body)
            }
        }
    }

    private fun HomeViewModel.awaitSettled() = runBlocking {
        awaitCondition(describe = { "home sections to settle" }) {
            uiState.value.progress !is ProgressSectionState.Loading &&
                uiState.value.continueLearning !is ContinueLearningState.Loading &&
                uiState.value.flashcardsDue !is FlashcardsDueState.Loading
        }
    }

    /** A full chain that succeeds at every step, with one deck and one due card. */
    private fun fullSuccessRoutes(totalXp: Int = 10) = mapOf(
        "/me/progress" to (200 to Fixtures.progressEnvelope(totalXp = totalXp)),
        "/subjects/s-1/topics" to (200 to Fixtures.topics("s-1", "t-1" to "Zulu")),
        "/topics/t-1/lessons" to (
            200 to Fixtures.lessons("t-1", "l-1" to "Zebra basics", "l-2" to "Aardvark basics")
            ),
        "/flashcard-decks" to (200 to Fixtures.flashcardDecks("l-1", "d-1" to "Greeting basics")),
        "/me/flashcard-queue" to (
            200 to Fixtures.flashcardQueue(
                "d-1",
                Triple("c-1", "Hello", 1),
                Triple("c-2", "Goodbye", 1),
            )
            ),
        // Must stay last: a bare "/subjects" would otherwise also match the
        // longer "/subjects/s-1/topics" path above it.
        "/subjects" to (200 to Fixtures.subjects(Triple("s-1", "Zeta", 0), Triple("s-2", "Alpha", 1))),
    )

    // -- Loading -----------------------------------------------------------

    @Test
    fun `loading is the default state for every section`() {
        val state = HomeUiState()

        assertTrue(state.progress is ProgressSectionState.Loading)
        assertTrue(state.continueLearning is ContinueLearningState.Loading)
        assertTrue(state.flashcardsDue is FlashcardsDueState.Loading)
    }

    // -- Progress ------------------------------------------------------------

    @Test
    fun `progress renders the backend projection exactly as received`() {
        respondByStatus(fullSuccessRoutes(totalXp = 30))
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val progress = (viewModel.uiState.value.progress as ProgressSectionState.Content).progress
        assertEquals(30, progress.totalXp)
        assertEquals(1, progress.completedLessons)
        assertEquals(3, progress.totalLessons)
    }

    // -- Continue learning: backend ordering --------------------------------

    @Test
    fun `continue learning picks the first lesson in backend order, not alphabetical`() {
        respondByStatus(fullSuccessRoutes())
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val state = viewModel.uiState.value.continueLearning as ContinueLearningState.Available
        assertEquals("l-1", state.lessonId)
        assertEquals("Zebra basics", state.lessonTitle)
    }

    // -- Continue learning: real completion state -----------------------------

    @Test
    fun `continue learning skips a lesson the backend already shows completed`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/me/lesson-completions"] = 200 to Fixtures.lessonCompletions("l-1" to "2026-07-20T08:00:00Z")
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val state = viewModel.uiState.value.continueLearning as ContinueLearningState.Available
        assertEquals("l-2", state.lessonId)
        assertEquals("Aardvark basics", state.lessonTitle)
    }

    @Test
    fun `continue learning is unavailable, not an error, once every lesson is completed`() {
        // Built directly, not from fullSuccessRoutes(): this test is the only
        // one that walks past the first subject into the second (s-2), so it
        // needs an explicit (empty) topics list for s-2 — routed ahead of the
        // generic "/subjects" entry, which would otherwise shadow it.
        respondByStatus(
            mapOf(
                "/me/progress" to (200 to Fixtures.progressEnvelope()),
                "/me/lesson-completions" to (
                    200 to Fixtures.lessonCompletions(
                        "l-1" to "2026-07-20T08:00:00Z",
                        "l-2" to "2026-07-21T08:00:00Z",
                    )
                    ),
                "/subjects/s-1/topics" to (200 to Fixtures.topics("s-1", "t-1" to "Zulu")),
                "/topics/t-1/lessons" to (
                    200 to Fixtures.lessons("t-1", "l-1" to "Zebra basics", "l-2" to "Aardvark basics")
                    ),
                "/subjects/s-2/topics" to (200 to Fixtures.topics("s-2")),
                // Must stay last: a bare "/subjects" would otherwise also
                // match the longer "/subjects/s-1/topics" and
                // "/subjects/s-2/topics" paths above it.
                "/subjects" to (200 to Fixtures.subjects(Triple("s-1", "Zeta", 0), Triple("s-2", "Alpha", 1))),
            ),
        )
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        assertEquals(ContinueLearningState.Unavailable, viewModel.uiState.value.continueLearning)
        assertEquals(FlashcardsDueState.Unavailable, viewModel.uiState.value.flashcardsDue)
    }

    @Test
    fun `a completions read failure falls back to the first lesson in backend order`() {
        // No "/me/lesson-completions" route is registered, so it 404s. Continue
        // learning must still resolve rather than fail the whole section for
        // what is enhancement data, not a correctness-critical dependency.
        respondByStatus(fullSuccessRoutes())
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val state = viewModel.uiState.value.continueLearning as ContinueLearningState.Available
        assertEquals("l-1", state.lessonId)
    }

    // -- Empty / new-user state ----------------------------------------------

    @Test
    fun `an empty catalog and zero progress is an honest empty state, not an error`() {
        val zeroProgressEnvelope = """
            {"data":${Fixtures.progress(completedLessons = 0, totalXp = 0, completionPercentage = 0.0)},
             "meta":{"requestId":"${Fixtures.REQUEST_ID}"}}
        """.trimIndent()
        respondByStatus(
            mapOf(
                "/me/progress" to (200 to zeroProgressEnvelope),
                "/subjects" to (200 to Fixtures.subjects()),
            ),
        )
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val progress = (viewModel.uiState.value.progress as ProgressSectionState.Content).progress
        assertEquals(0, progress.totalXp)
        assertEquals(0, progress.completedLessons)
        assertEquals(ContinueLearningState.Unavailable, viewModel.uiState.value.continueLearning)
        assertEquals(FlashcardsDueState.Unavailable, viewModel.uiState.value.flashcardsDue)
    }

    // -- Flashcards due --------------------------------------------------------

    @Test
    fun `due flashcards render the deck and count from the queue`() {
        respondByStatus(fullSuccessRoutes())
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val state = viewModel.uiState.value.flashcardsDue as FlashcardsDueState.Available
        assertEquals("d-1", state.deckId)
        assertEquals("Greeting basics", state.deckName)
        assertEquals(2, state.dueCount)
    }

    @Test
    fun `no deck for the lesson is unavailable, not an error`() {
        respondByStatus(
            mapOf(
                "/me/progress" to (200 to Fixtures.progressEnvelope()),
                "/subjects/s-1/topics" to (200 to Fixtures.topics("s-1", "t-1" to "Zulu")),
                "/topics/t-1/lessons" to (200 to Fixtures.lessons("t-1", "l-1" to "Zebra basics")),
                "/flashcard-decks" to (200 to Fixtures.flashcardDecks("l-1")),
                "/subjects" to (200 to Fixtures.subjects(Triple("s-1", "Zeta", 0))),
            ),
        )
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        assertEquals(FlashcardsDueState.Unavailable, viewModel.uiState.value.flashcardsDue)
    }

    @Test
    fun `a deck with nothing due is unavailable, not an error`() {
        respondByStatus(
            mapOf(
                "/me/progress" to (200 to Fixtures.progressEnvelope()),
                "/subjects/s-1/topics" to (200 to Fixtures.topics("s-1", "t-1" to "Zulu")),
                "/topics/t-1/lessons" to (200 to Fixtures.lessons("t-1", "l-1" to "Zebra basics")),
                "/flashcard-decks" to (200 to Fixtures.flashcardDecks("l-1", "d-1" to "Greeting basics")),
                "/me/flashcard-queue" to (200 to Fixtures.flashcardQueue("d-1")),
                "/subjects" to (200 to Fixtures.subjects(Triple("s-1", "Zeta", 0))),
            ),
        )
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        assertEquals(FlashcardsDueState.Unavailable, viewModel.uiState.value.flashcardsDue)
    }

    // -- Independent failures ---------------------------------------------

    @Test
    fun `a progress failure does not hide continue-learning or flashcards`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/me/progress"] = 500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        val progressState = viewModel.uiState.value.progress as ProgressSectionState.Failed
        assertEquals(CatalogErrorKind.Generic, progressState.kind)
        assertEquals(Fixtures.REQUEST_ID, progressState.requestId)

        assertTrue(viewModel.uiState.value.continueLearning is ContinueLearningState.Available)
        assertTrue(viewModel.uiState.value.flashcardsDue is FlashcardsDueState.Available)
    }

    @Test
    fun `a catalog failure fails continue-learning and leaves flashcards unavailable, not progress`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/subjects"] = 500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        assertTrue(viewModel.uiState.value.progress is ProgressSectionState.Content)

        val continueState = viewModel.uiState.value.continueLearning as ContinueLearningState.Failed
        assertEquals(Fixtures.REQUEST_ID, continueState.requestId)
        assertEquals(FlashcardsDueState.Unavailable, viewModel.uiState.value.flashcardsDue)
    }

    @Test
    fun `a flashcard failure fails only that section, leaving progress and continue-learning intact`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/flashcard-decks"] = 500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()

        assertTrue(viewModel.uiState.value.progress is ProgressSectionState.Content)
        assertTrue(viewModel.uiState.value.continueLearning is ContinueLearningState.Available)

        val flashcardsState = viewModel.uiState.value.flashcardsDue as FlashcardsDueState.Failed
        assertEquals(Fixtures.REQUEST_ID, flashcardsState.requestId)
    }

    // -- Retry --------------------------------------------------------------

    @Test
    fun `retrying progress reloads it without affecting the other sections`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/me/progress"] = 500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()
        assertTrue(viewModel.uiState.value.progress is ProgressSectionState.Failed)

        respondByStatus(fullSuccessRoutes(totalXp = 42))
        viewModel.retryProgress()
        runBlocking {
            awaitCondition(describe = { "progress to recover" }) {
                viewModel.uiState.value.progress is ProgressSectionState.Content
            }
        }

        val progress = (viewModel.uiState.value.progress as ProgressSectionState.Content).progress
        assertEquals(42, progress.totalXp)
        // Unaffected by a retry scoped to progress alone.
        assertTrue(viewModel.uiState.value.continueLearning is ContinueLearningState.Available)
    }

    @Test
    fun `retrying continue-learning also re-resolves flashcards`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/subjects"] = 500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()
        assertTrue(viewModel.uiState.value.continueLearning is ContinueLearningState.Failed)

        respondByStatus(fullSuccessRoutes())
        viewModel.retryContinueLearning()
        runBlocking {
            awaitCondition(describe = { "continue-learning and flashcards to recover" }) {
                viewModel.uiState.value.continueLearning is ContinueLearningState.Available &&
                    viewModel.uiState.value.flashcardsDue is FlashcardsDueState.Available
            }
        }
    }

    @Test
    fun `retrying flashcards alone does not repeat the catalog chain`() {
        val routes = fullSuccessRoutes().toMutableMap()
        routes["/flashcard-decks"] = 500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)
        respondByStatus(routes)
        val viewModel = newViewModel()
        viewModel.awaitSettled()
        assertTrue(viewModel.uiState.value.flashcardsDue is FlashcardsDueState.Failed)

        // Only the flashcard endpoints are re-armed; the catalog endpoints are
        // deliberately left failing. A retry that redid the catalog chain
        // would therefore re-fail continue-learning too.
        respondByStatus(
            mapOf(
                "/subjects" to (500 to Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
                "/flashcard-decks" to (200 to Fixtures.flashcardDecks("l-1", "d-1" to "Greeting basics")),
                "/me/flashcard-queue" to (200 to Fixtures.flashcardQueue("d-1", Triple("c-1", "Hello", 1))),
            ),
        )
        viewModel.retryFlashcardsDue()
        runBlocking {
            awaitCondition(describe = { "flashcards to recover" }) {
                viewModel.uiState.value.flashcardsDue is FlashcardsDueState.Available
            }
        }

        // continue-learning still shows its earlier successful resolution.
        assertTrue(viewModel.uiState.value.continueLearning is ContinueLearningState.Available)
    }
}
