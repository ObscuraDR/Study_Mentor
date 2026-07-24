package com.elenglish.studymentor.ui.progress

import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.learning.LearningRepository
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.awaitQuiescence
import com.elenglish.studymentor.testing.Fixtures
import com.elenglish.studymentor.testing.awaitCondition
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: LearningRepository

    private val timeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = 1_774_000_000_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        repository = LearningRepository(
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
    }

    private fun ProgressViewModel.awaitSettled() = runBlocking {
        awaitCondition(describe = { "progress to settle" }) {
            uiState.value !is ProgressUiState.Loading
        }
    }

    @Test
    fun `renders the backend projection exactly as received`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.progressEnvelope(totalXp = 30)),
        )

        val viewModel = ProgressViewModel(repository)
        viewModel.awaitSettled()

        val progress = (viewModel.uiState.value as ProgressUiState.Content).progress
        assertEquals(30, progress.totalXp)
        assertEquals(1, progress.completedLessons)
        assertEquals(3, progress.totalLessons)
        assertEquals(33.33, progress.completionPercentage, 0.001)
    }

    @Test
    fun `a failure offers a retry rather than showing zeroes`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )

        val viewModel = ProgressViewModel(repository)
        viewModel.awaitSettled()

        // Rendering 0 XP on failure would misreport the learner's real standing.
        val state = viewModel.uiState.value as ProgressUiState.Failed
        assertEquals(Fixtures.REQUEST_ID, state.requestId)
    }

    @Test
    fun `an expired session is reported as unauthorized`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_SESSION_EXPIRED)),
        )

        val viewModel = ProgressViewModel(repository)
        viewModel.awaitSettled()

        assertEquals(
            CatalogErrorKind.Unauthorized,
            (viewModel.uiState.value as ProgressUiState.Failed).kind,
        )
    }

    @Test
    fun `retry reloads the projection`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        val viewModel = ProgressViewModel(repository)
        viewModel.awaitSettled()
        assertTrue(viewModel.uiState.value is ProgressUiState.Failed)

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.progressEnvelope(totalXp = 50)),
        )
        viewModel.load()
        viewModel.awaitSettled()

        assertEquals(
            50,
            (viewModel.uiState.value as ProgressUiState.Content).progress.totalXp,
        )
    }
}
