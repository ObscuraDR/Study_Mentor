package com.elenglish.studymentor.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.catalog.CatalogRepository
import com.elenglish.studymentor.data.learning.CompletedLessonsRegistry
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.data.local.StudyMentorDatabase
import com.elenglish.studymentor.domain.model.DataOrigin
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.awaitQuiescence
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var database: StudyMentorDatabase
    private lateinit var repository: CatalogRepository
    private lateinit var learningRepository: LearningRepository

    private val fixedTimeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = 1_700_000_000_000
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        val timeSource = fixedTimeSource
        repository = CatalogRepository(
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
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
        database.close()
    }

    private fun awaitSettled(state: () -> CatalogUiState<*>) = runBlocking {
        awaitCondition(describe = { "catalog state to settle" }) {
            state() !is CatalogUiState.Loading
        }
    }

    @Test
    fun `subjects load into content in backend order`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.subjects(Triple("s-1", "Zeta", 0), Triple("s-2", "Alpha", 1)),
            ),
        )

        val viewModel = SubjectsViewModel(repository)
        awaitSettled { viewModel.uiState.value }

        val state = viewModel.uiState.value as CatalogUiState.Content
        assertEquals(listOf("Zeta", "Alpha"), state.items.map { it.name })
        assertEquals(DataOrigin.Live, state.origin)
    }

    @Test
    fun `an empty catalog is Empty, not Failed`() {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.subjects()))

        val viewModel = SubjectsViewModel(repository)
        awaitSettled { viewModel.uiState.value }

        assertTrue(viewModel.uiState.value is CatalogUiState.Empty)
    }

    @Test
    fun `an unreachable backend with no cache reports a network failure`() {
        harness.goOffline()

        val viewModel = SubjectsViewModel(repository)
        awaitSettled { viewModel.uiState.value }

        val state = viewModel.uiState.value as CatalogUiState.Failed
        assertEquals(CatalogErrorKind.Network, state.kind)
    }

    @Test
    fun `an expired session is reported as unauthorized, with its request id`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )

        val viewModel = SubjectsViewModel(repository)
        awaitSettled { viewModel.uiState.value }

        val state = viewModel.uiState.value as CatalogUiState.Failed
        assertEquals(CatalogErrorKind.Unauthorized, state.kind)
        assertEquals(Fixtures.REQUEST_ID, state.requestId)
    }

    @Test
    fun `retry after a failure loads the list`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        val viewModel = SubjectsViewModel(repository)
        awaitSettled { viewModel.uiState.value }
        assertTrue(viewModel.uiState.value is CatalogUiState.Failed)

        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        viewModel.load()
        awaitSettled { viewModel.uiState.value }

        assertTrue(viewModel.uiState.value is CatalogUiState.Content)
    }

    @Test
    fun `offline content is flagged as cached so the ui can say so`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.subjects(Triple("s-1", "Grammar", 0))),
        )
        val first = SubjectsViewModel(repository)
        awaitSettled { first.uiState.value }

        harness.goOffline()
        val second = SubjectsViewModel(repository)
        awaitSettled { second.uiState.value }

        val state = second.uiState.value as CatalogUiState.Content
        assertEquals(DataOrigin.Cached, state.origin)
        assertEquals(1_700_000_000_000, state.cachedAtEpochMillis)
    }

    @Test
    fun `topics load for the subject in the saved state handle`() {
        // TopicsViewModel loads the topic list and the subject name concurrently,
        // so responses are routed by path rather than by arrival order.
        harness.respondByPath(
            mapOf(
                "/subjects/subj-1/topics" to Fixtures.topics("subj-1", "t-1" to "Tenses"),
                "/subjects/subj-1" to Fixtures.subjects(Triple("subj-1", "Grammar", 0)),
            ),
        )

        val viewModel = TopicsViewModel(repository, SavedStateHandle(mapOf("subjectId" to "subj-1")))
        awaitSettled { viewModel.uiState.value }

        val state = viewModel.uiState.value as CatalogUiState.Content
        assertEquals(listOf("Tenses"), state.items.map { it.name })
    }

    @Test
    fun `lessons load for the topic in the saved state handle`() {
        // Same concurrency as topics: the lesson list and the topic name load
        // together, so responses are routed by path.
        harness.respondByPath(
            mapOf(
                "/topics/topic-1/lessons" to Fixtures.lessons("topic-1", "l-1" to "Present simple"),
                "/topics/topic-1" to Fixtures.topics("subj-1", "topic-1" to "Tenses"),
            ),
        )

        val viewModel = LessonsViewModel(
            repository,
            learningRepository,
            SavedStateHandle(mapOf("topicId" to "topic-1")),
        )
        awaitSettled { viewModel.uiState.value }

        val state = viewModel.uiState.value as CatalogUiState.Content
        assertEquals(listOf("Present simple"), state.items.map { it.title })
    }

    @Test
    fun `a missing lesson is reported as not found`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody(Fixtures.error("learning.resource_not_found")),
        )

        val viewModel = LessonDetailViewModel(
            repository = repository,
            learningRepository = learningRepository,
            completedLessons = CompletedLessonsRegistry(),
            timeSource = fixedTimeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "missing")),
        )
        runBlocking {
            awaitCondition(describe = { "lesson detail to settle" }) {
                viewModel.uiState.value !is LessonDetailUiState.Loading
            }
        }

        val state = viewModel.uiState.value as LessonDetailUiState.Failed
        assertEquals(CatalogErrorKind.NotFound, state.kind)
    }

    @Test
    fun `lesson detail loads the lesson the route asked for`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("l-1", "Present simple")),
        )
        // Fetched after the lesson loads, to check whether it is already
        // completed from a prior session.
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.lessonCompletions()),
        )

        val viewModel = LessonDetailViewModel(
            repository = repository,
            learningRepository = learningRepository,
            completedLessons = CompletedLessonsRegistry(),
            timeSource = fixedTimeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "l-1")),
        )
        runBlocking {
            awaitCondition(describe = { "lesson detail to settle" }) {
                viewModel.uiState.value !is LessonDetailUiState.Loading
            }
        }

        val state = viewModel.uiState.value as LessonDetailUiState.Content
        assertEquals("Present simple", state.lesson.title)
        assertEquals(12, state.lesson.estimatedMinutes)
    }

    @Test
    fun `lessons list marks lessons the backend confirms as completed`() {
        harness.respondByPath(
            mapOf(
                "/topics/topic-1/lessons" to Fixtures.lessons("topic-1", "l-1" to "Present simple", "l-2" to "Past simple"),
                "/topics/topic-1" to Fixtures.topics("subj-1", "topic-1" to "Tenses"),
                "/me/lesson-completions" to Fixtures.lessonCompletions("l-1" to "2026-07-20T08:00:00Z"),
            ),
        )

        val viewModel = LessonsViewModel(
            repository,
            learningRepository,
            SavedStateHandle(mapOf("topicId" to "topic-1")),
        )
        awaitSettled { viewModel.uiState.value }
        runBlocking {
            awaitCondition(describe = { "completions to resolve" }) {
                viewModel.completedLessonIds.value.isNotEmpty()
            }
        }

        assertEquals(setOf("l-1"), viewModel.completedLessonIds.value)
    }

    @Test
    fun `a completions read failure leaves the lessons list with no completed marks, not an error`() {
        harness.respondByPath(
            mapOf(
                "/topics/topic-1/lessons" to Fixtures.lessons("topic-1", "l-1" to "Present simple"),
                "/topics/topic-1" to Fixtures.topics("subj-1", "topic-1" to "Tenses"),
                // No "/me/lesson-completions" route: it 404s, fixture-simulating a failure.
            ),
        )

        val viewModel = LessonsViewModel(
            repository,
            learningRepository,
            SavedStateHandle(mapOf("topicId" to "topic-1")),
        )
        awaitSettled { viewModel.uiState.value }

        // The list itself still renders successfully; only the enhancement is absent.
        assertTrue(viewModel.uiState.value is CatalogUiState.Content)
        assertEquals(emptySet<String>(), viewModel.completedLessonIds.value)
    }

    @Test
    fun `lesson detail shows a lesson already completed in a previous session, from the backend`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("l-1", "Present simple")),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lessonCompletions("l-1" to "2026-07-20T08:00:00Z")),
        )

        val viewModel = LessonDetailViewModel(
            repository = repository,
            learningRepository = learningRepository,
            completedLessons = CompletedLessonsRegistry(),
            timeSource = fixedTimeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "l-1")),
        )
        runBlocking {
            awaitCondition(describe = { "lesson detail to settle" }) {
                viewModel.uiState.value !is LessonDetailUiState.Loading
            }
        }

        val state = viewModel.uiState.value as LessonDetailUiState.Content
        val completion = state.completion as CompletionState.CompletedPreviously
        assertEquals("2026-07-20T08:00:00Z", completion.completedAt)
    }

    @Test
    fun `a completions read failure leaves lesson detail idle, not blocked`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.lesson("l-1", "Present simple")),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )

        val viewModel = LessonDetailViewModel(
            repository = repository,
            learningRepository = learningRepository,
            completedLessons = CompletedLessonsRegistry(),
            timeSource = fixedTimeSource,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "l-1")),
        )
        runBlocking {
            awaitCondition(describe = { "lesson detail to settle" }) {
                viewModel.uiState.value !is LessonDetailUiState.Loading
            }
        }

        val state = viewModel.uiState.value as LessonDetailUiState.Content
        assertEquals(CompletionState.Idle, state.completion)
    }
}
