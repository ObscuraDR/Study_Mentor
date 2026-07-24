package com.elenglish.studymentor.data.account

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.data.remote.dto.PatchValue
import com.elenglish.studymentor.domain.model.AppLocale
import com.elenglish.studymentor.domain.model.EducationLevel
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountRepositoryTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        repository = AccountRepository(harness.accountApi, harness.json)
    }

    @After
    fun tearDown() {
        harness.shutdown()
    }

    @Test
    fun `profile dto maps onto the domain model`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.profile()))

        val result = repository.getProfile()

        val profile = (result as ApiResult.Success).value
        assertEquals(Fixtures.USER_ID, profile.id)
        assertEquals("Mai", profile.displayName)
        assertEquals("mai@example.com", profile.email)
        assertEquals(EducationLevel.Intermediate, profile.educationLevel)
        assertEquals("rev-1", profile.revision)
        assertNull(profile.avatarKey)
    }

    @Test
    fun `an unknown education level degrades to null rather than crashing`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.profile(educationLevel = "expert")),
        )

        val result = repository.getProfile()

        assertNull((result as ApiResult.Success).value.educationLevel)
    }

    @Test
    fun `updating a profile sends the read revision in If-Match`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.profile("rev-2")))

        repository.updateProfile(revision = "rev-1", displayName = PatchValue.Set("Mai Anh"))

        val request = harness.server.takeRequest()
        assertEquals("rev-1", request.getHeader("If-Match"))
        assertEquals("PATCH", request.method)
    }

    @Test
    fun `an unchanged field is omitted from the patch entirely`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.profile("rev-2")))

        repository.updateProfile(revision = "rev-1", displayName = PatchValue.Set("Mai Anh"))

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Mai Anh", body["displayName"]?.jsonPrimitive?.content)
        // Absent, not null: the server must not clear these.
        assertFalse(body.containsKey("educationLevel"))
        assertFalse(body.containsKey("avatarKey"))
    }

    @Test
    fun `clearing a field sends an explicit null, distinct from omitting it`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.profile("rev-2", educationLevel = null)),
        )

        repository.updateProfile(revision = "rev-1", educationLevel = PatchValue.Set(null))

        val body = Json.parseToJsonElement(harness.server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(body.containsKey("educationLevel"))
        assertEquals(JsonNull, body["educationLevel"])
        assertFalse(body.containsKey("displayName"))
    }

    @Test
    fun `an empty patch is rejected before it reaches the network`() = runTest {
        val error = runCatching { repository.updateProfile(revision = "rev-1") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, harness.server.requestCount)
    }

    @Test
    fun `a revision conflict is surfaced as a conflict, not silently retried`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody(Fixtures.error(ApiErrorCodes.CONFLICT_REVISION_MISMATCH)),
        )

        val result = repository.updateProfile(revision = "stale", displayName = PatchValue.Set("Mai"))

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).isRevisionConflict())
        assertEquals(
            Fixtures.REQUEST_ID,
            (result.error as ApiError.Backend).requestId,
        )
        // One attempt only: overwriting another device's change is never automatic.
        assertEquals(1, harness.server.requestCount)
    }

    @Test
    fun `settings map onto the domain model`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.settings()))

        val settings = (repository.getSettings() as ApiResult.Success).value

        assertEquals(AppLocale.English, settings.locale)
        assertEquals(50, settings.dailyGoalTargetXp)
        assertEquals("rev-1", settings.revision)
    }

    @Test
    fun `replacing settings sends both fields and the If-Match revision`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(Fixtures.settings("rev-2", "vi", 80)),
        )

        repository.replaceSettings(revision = "rev-1", locale = AppLocale.Vietnamese, dailyGoalTargetXp = 80)

        val request = harness.server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("rev-1", request.getHeader("If-Match"))

        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("vi", body["locale"]?.jsonPrimitive?.content)
        assertEquals("80", body["dailyGoalTargetXp"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a daily goal outside the contract range is rejected locally`() = runTest {
        val tooHigh = runCatching {
            repository.replaceSettings("rev-1", AppLocale.English, 10_001)
        }.exceptionOrNull()
        val tooLow = runCatching {
            repository.replaceSettings("rev-1", AppLocale.English, 0)
        }.exceptionOrNull()

        assertTrue(tooHigh is IllegalArgumentException)
        assertTrue(tooLow is IllegalArgumentException)
        assertEquals(0, harness.server.requestCount)
    }
}
