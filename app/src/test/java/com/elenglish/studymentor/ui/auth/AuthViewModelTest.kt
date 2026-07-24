package com.elenglish.studymentor.ui.auth

import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.session.SessionState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the ViewModel against a real MockWebServer, so DTO mapping, error
 * envelopes and request ids are exercised rather than mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        // Unconfined so viewModelScope work starts eagerly; completion is then
        // awaited against the wall clock, because the network call is real.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = ApiTestHarness()
        viewModel = AuthViewModel(harness.sessionRepository)
    }

    @After
    fun tearDown() {
        harness.shutdown()
        awaitQuiescence()
        Dispatchers.resetMain()
    }

    private fun awaitSubmitted() = runBlocking {
        awaitCondition(describe = { "submit to finish" }) { !viewModel.uiState.value.submitting }
    }

    @Test
    fun `submit is blocked until both credentials are entered`() {
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onEmailChange("mai@example.com")
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onPasswordChange("correct-horse")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `registration additionally requires a name`() {
        viewModel.setMode(AuthMode.Register)
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("correct-horse")

        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onDisplayNameChange("Mai")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `local validation rejects a malformed email without calling the backend`() {
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("correct-horse")

        viewModel.submit()

        assertNotNull(viewModel.uiState.value.emailError)
        assertEquals(0, harness.server.requestCount)
    }

    @Test
    fun `registration enforces the contract's 8 character password minimum`() {
        viewModel.setMode(AuthMode.Register)
        viewModel.onDisplayNameChange("Mai")
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("short")

        viewModel.submit()

        assertNotNull(viewModel.uiState.value.passwordError)
        assertEquals(0, harness.server.requestCount)
    }

    @Test
    fun `a short password is accepted on sign-in, because only the backend decides`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_INVALID_CREDENTIALS)),
        )
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("old")

        viewModel.submit()
        awaitSubmitted()

        // The request was made; the client did not pre-judge the credentials.
        assertEquals(1, harness.server.requestCount)
    }

    @Test
    fun `a successful sign-in clears the password from ui state`() {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.session()))
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("correct-horse")

        viewModel.submit()
        awaitSubmitted()

        assertEquals("", viewModel.uiState.value.password)
        assertNull(viewModel.uiState.value.failure)
        assertEquals(
            SessionState.Authenticated(Fixtures.USER_ID),
            harness.sessionStateHolder.state.value,
        )
    }

    @Test
    fun `invalid credentials produce a user-facing message and keep the request id`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_INVALID_CREDENTIALS, "bad creds")),
        )
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("wrong-password")

        viewModel.submit()
        awaitSubmitted()

        val failure = viewModel.uiState.value.failure!!
        assertEquals("That email and password do not match.", failure.message)
        assertEquals(Fixtures.REQUEST_ID, failure.requestId)
        // The backend's developer-facing wording is not shown to the user.
        assertFalse(failure.message.contains("bad creds"))
    }

    @Test
    fun `a duplicate email during registration is explained clearly`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_EMAIL_ALREADY_REGISTERED)),
        )
        viewModel.setMode(AuthMode.Register)
        viewModel.onDisplayNameChange("Mai")
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("correct-horse")

        viewModel.submit()
        awaitSubmitted()

        assertEquals(
            "That email is already registered.",
            viewModel.uiState.value.failure?.message,
        )
    }

    @Test
    fun `switching mode clears a previous failure`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody(Fixtures.error(ApiErrorCodes.AUTH_INVALID_CREDENTIALS)),
        )
        viewModel.onEmailChange("mai@example.com")
        viewModel.onPasswordChange("wrong-password")
        viewModel.submit()
        awaitSubmitted()
        assertNotNull(viewModel.uiState.value.failure)

        viewModel.setMode(AuthMode.Register)

        assertNull(viewModel.uiState.value.failure)
    }
}
