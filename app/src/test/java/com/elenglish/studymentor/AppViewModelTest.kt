package com.elenglish.studymentor

import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.data.preferences.AppPreferencesRepository
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.awaitQuiescence
import com.elenglish.studymentor.testing.Fixtures
import com.elenglish.studymentor.testing.awaitCondition
import com.elenglish.studymentor.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val themeMode = MutableStateFlow(ThemeMode.System)
    private val preferences: AppPreferencesRepository = mockk {
        every { this@mockk.themeMode } returns this@AppViewModelTest.themeMode
    }
    private var harness: ApiTestHarness? = null
    private val collectorScope = CoroutineScope(Job() + Dispatchers.Default)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        harness?.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
    }

    /**
     * `uiState` is a `WhileSubscribed` StateFlow, so it only recomputes while it
     * has a collector; the test keeps one alive for the test's duration.
     */
    private fun createSubscribedViewModel(refreshToken: String? = null): AppViewModel {
        val h = ApiTestHarness(refreshToken).also { harness = it }
        val viewModel = AppViewModel(preferences, h.sessionStateHolder, h.sessionRepository)
        collectorScope.launch { viewModel.uiState.collect { } }
        return viewModel
    }

    private fun AppViewModel.awaitStatus(expected: SessionStatus) = runBlocking {
        awaitCondition(describe = { "session status $expected" }) {
            uiState.value.sessionStatus == expected
        }
    }

    @Test
    fun `starts in restoring so no shell is shown before the session is known`() {
        val h = ApiTestHarness(refreshToken = "refresh-1").also { harness = it }
        // Never answered, so restoration stays in flight.
        h.server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))

        val viewModel = AppViewModel(preferences, h.sessionStateHolder, h.sessionRepository)

        assertEquals(SessionStatus.Restoring, viewModel.uiState.value.sessionStatus)
    }

    @Test
    fun `no stored refresh token resolves to the guest shell`() {
        val viewModel = createSubscribedViewModel(refreshToken = null)

        viewModel.awaitStatus(SessionStatus.Guest)
        assertEquals(0, harness!!.server.requestCount)
    }

    @Test
    fun `a confirmed session resolves to the authenticated shell`() {
        val h = ApiTestHarness(refreshToken = "refresh-1").also { harness = it }
        h.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.refreshedSession()))
        h.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.user()))

        val viewModel = AppViewModel(preferences, h.sessionStateHolder, h.sessionRepository)
        collectorScope.launch { viewModel.uiState.collect { } }

        viewModel.awaitStatus(SessionStatus.Authenticated)
    }

    @Test
    fun `a rejected stored token resolves to the guest shell`() {
        val h = ApiTestHarness(refreshToken = "stale").also { harness = it }
        h.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_REFRESH_TOKEN_INVALID)),
        )

        val viewModel = AppViewModel(preferences, h.sessionStateHolder, h.sessionRepository)
        collectorScope.launch { viewModel.uiState.collect { } }

        viewModel.awaitStatus(SessionStatus.Guest)
    }

    @Test
    fun `reflects the stored theme preference`() {
        themeMode.value = ThemeMode.Dark

        val viewModel = createSubscribedViewModel()

        runBlocking {
            awaitCondition(describe = { "dark theme" }) {
                viewModel.uiState.value.themeMode == ThemeMode.Dark
            }
        }
    }

    @Test
    fun `follows a later theme preference change`() {
        val viewModel = createSubscribedViewModel()
        viewModel.awaitStatus(SessionStatus.Guest)

        themeMode.value = ThemeMode.Light

        runBlocking {
            awaitCondition(describe = { "light theme" }) {
                viewModel.uiState.value.themeMode == ThemeMode.Light
            }
        }
    }
}
