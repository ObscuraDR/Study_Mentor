package com.elenglish.studymentor.ui.profile

import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.data.account.AccountRepository
import com.elenglish.studymentor.domain.model.EducationLevel
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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

    /** Enqueues the profile+settings reads the screen performs on load. */
    private fun enqueueInitialLoad(profileRevision: String = "rev-1") {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.profile(profileRevision)),
        )
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.settings()))
    }

    private fun createViewModel(): ProfileViewModel = ProfileViewModel(
        accountRepository = AccountRepository(harness.accountApi, harness.json),
        sessionRepository = harness.sessionRepository,
    )

    private fun ProfileViewModel.awaitLoaded() = runBlocking {
        awaitCondition(describe = { "profile load to settle" }) {
            uiState.value.loadState != ProfileLoadState.Loading
        }
    }

    private fun ProfileViewModel.awaitSaved() = runBlocking {
        awaitCondition(describe = { "save to finish" }) {
            !uiState.value.savingProfile && !uiState.value.savingSettings
        }
    }

    /** Consumes the two requests issued by the initial load. */
    private fun drainInitialLoadRequests() {
        harness.server.takeRequest()
        harness.server.takeRequest()
    }

    @Test
    fun `loads profile and settings into the form`() {
        enqueueInitialLoad()

        val viewModel = createViewModel()
        viewModel.awaitLoaded()

        val state = viewModel.uiState.value
        assertEquals(ProfileLoadState.Loaded, state.loadState)
        assertEquals("Mai", state.displayNameInput)
        assertEquals(EducationLevel.Intermediate, state.educationLevelInput)
        assertEquals("50", state.dailyGoalInput)
        assertFalse(state.profileDirty)
        assertFalse(state.settingsDirty)
    }

    @Test
    fun `a failed load offers a retry instead of an empty form`() {
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )

        val viewModel = createViewModel()
        viewModel.awaitLoaded()

        val loadState = viewModel.uiState.value.loadState
        assertTrue(loadState is ProfileLoadState.Failed)
        assertEquals(Fixtures.REQUEST_ID, (loadState as ProfileLoadState.Failed).requestId)
    }

    @Test
    fun `saving is disabled until something actually changes`() {
        enqueueInitialLoad()
        val viewModel = createViewModel()
        viewModel.awaitLoaded()

        assertFalse(viewModel.uiState.value.canSaveProfile)

        viewModel.onDisplayNameChange("Mai Anh")

        assertTrue(viewModel.uiState.value.canSaveProfile)
    }

    @Test
    fun `saving a profile sends only the changed field with the read revision`() {
        enqueueInitialLoad(profileRevision = "rev-1")
        val viewModel = createViewModel()
        viewModel.awaitLoaded()
        drainInitialLoadRequests()

        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.profile("rev-2")),
        )
        viewModel.onDisplayNameChange("Mai Anh")
        viewModel.saveProfile()
        viewModel.awaitSaved()

        val request = harness.server.takeRequest()
        assertEquals("rev-1", request.getHeader("If-Match"))

        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Mai Anh", body["displayName"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("educationLevel"))

        // The refreshed revision is adopted for the next save.
        assertEquals("rev-2", viewModel.uiState.value.profile?.revision)
    }

    @Test
    fun `clearing the education level sends an explicit null`() {
        enqueueInitialLoad()
        val viewModel = createViewModel()
        viewModel.awaitLoaded()
        drainInitialLoadRequests()

        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.profile("rev-2", educationLevel = null)),
        )
        viewModel.onEducationLevelChange(null)
        viewModel.saveProfile()
        viewModel.awaitSaved()

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(body.containsKey("educationLevel"))
        assertNull(viewModel.uiState.value.profile?.educationLevel)
    }

    @Test
    fun `a revision conflict asks the user to reload rather than overwriting`() {
        enqueueInitialLoad()
        val viewModel = createViewModel()
        viewModel.awaitLoaded()

        harness.server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody(Fixtures.error(ApiErrorCodes.CONFLICT_REVISION_MISMATCH)),
        )
        viewModel.onDisplayNameChange("Mai Anh")
        viewModel.saveProfile()
        viewModel.awaitSaved()

        val message = viewModel.uiState.value.message!!
        assertEquals(ProfileMessageKind.RevisionConflict, message.kind)
        assertTrue(message.requiresReload)
        assertTrue(message.isError)
        assertEquals(Fixtures.REQUEST_ID, message.requestId)
        // The local edit is kept so the user does not lose their typing.
        assertEquals("Mai Anh", viewModel.uiState.value.displayNameInput)
    }

    @Test
    fun `an out-of-range daily goal blocks saving`() {
        enqueueInitialLoad()
        val viewModel = createViewModel()
        viewModel.awaitLoaded()

        viewModel.onDailyGoalChange("99999")
        assertNotNull(viewModel.uiState.value.dailyGoalError)
        assertFalse(viewModel.uiState.value.canSaveSettings)

        viewModel.onDailyGoalChange("not a number")
        assertNotNull(viewModel.uiState.value.dailyGoalError)
        assertFalse(viewModel.uiState.value.canSaveSettings)

        viewModel.onDailyGoalChange("80")
        assertNull(viewModel.uiState.value.dailyGoalError)
        assertTrue(viewModel.uiState.value.canSaveSettings)
    }

    @Test
    fun `signing out clears the session even if the request fails`() {
        enqueueInitialLoad()
        harness.accessTokenHolder.set("access-1")
        val viewModel = createViewModel()
        viewModel.awaitLoaded()

        harness.server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody(Fixtures.error(ApiErrorCodes.SERVER_INTERNAL)),
        )
        viewModel.signOut()
        runBlocking {
            awaitCondition(describe = { "sign-out to finish" }) {
                !viewModel.uiState.value.signingOut
            }
        }

        assertNull(harness.accessTokenHolder.get())
        assertNull(harness.refreshTokenStorage.read())
    }
}
