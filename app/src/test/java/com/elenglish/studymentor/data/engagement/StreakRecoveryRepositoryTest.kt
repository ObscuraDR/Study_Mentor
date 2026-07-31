package com.elenglish.studymentor.data.engagement

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreakRecoveryRepositoryTest {
    private lateinit var harness: ApiTestHarness
    private lateinit var repository: StreakRecoveryRepository

    @Before fun setUp() {
        harness = ApiTestHarness()
        repository = StreakRecoveryRepository(harness.streakRecoveryApi, FixedUuidGenerator(), harness.json)
    }

    @After fun tearDown() = harness.shutdown()

    @Test fun `eligibility maps server fields without client calculations`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.streakRecoveryEligibilityEnvelope()))
        val result = repository.getEligibility() as ApiResult.Success
        assertTrue(result.value.eligible)
        assertEquals("2026-03-09", result.value.missedLocalDate)
        assertEquals(4, result.value.streak)
    }

    @Test fun `claim sends empty body and generated UUIDv7 header`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(201).setBody(Fixtures.streakRecoveryClaimEnvelope()))
        val pending = repository.prepareClaim()
        val result = repository.claim(pending) as ApiResult.Success
        val request = harness.server.takeRequest()
        assertEquals("019f7e39-4000-7000-8000-000000000001", pending.idempotencyKey)
        assertEquals(pending.idempotencyKey, request.getHeader("Idempotency-Key"))
        assertEquals("", request.body.readUtf8())
        assertEquals("2026-03-09", result.value.missedLocalDate)
    }

    @Test fun `unknown claim outcome can be retried with the same key`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(503).setBody(Fixtures.error("server.internal")))
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.streakRecoveryClaimEnvelope()))
        val pending = repository.prepareClaim()
        assertTrue(repository.claim(pending) is ApiResult.Failure)
        val replay = repository.claim(pending) as ApiResult.Success
        assertTrue(replay.value.wasReplay)
        assertEquals(pending.idempotencyKey, harness.server.takeRequest().getHeader("Idempotency-Key"))
        assertEquals(pending.idempotencyKey, harness.server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test fun `ineligible eligibility maps server fields without acting on them`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.streakRecoveryEligibilityEnvelope(eligible = false, reasonCode = "prior_streak_too_short"),
            ),
        )
        val result = repository.getEligibility() as ApiResult.Success
        assertEquals(false, result.value.eligible)
        assertEquals("prior_streak_too_short", result.value.reasonCode)
        assertEquals(null, result.value.missedLocalDate)
        assertEquals(4, result.value.streak)
    }

    @Test fun `network error on eligibility surfaces as failure`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        assertTrue(repository.getEligibility() is ApiResult.Failure)
    }

    private class FixedUuidGenerator : UuidGenerator {
        override fun newUuidV7() = "019f7e39-4000-7000-8000-000000000001"
    }
}
