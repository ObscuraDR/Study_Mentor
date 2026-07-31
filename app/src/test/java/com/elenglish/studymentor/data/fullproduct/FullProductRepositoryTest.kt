package com.elenglish.studymentor.data.fullproduct

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.domain.model.BossAnswer
import com.elenglish.studymentor.testing.ApiTestHarness
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FullProductRepositoryTest {
    private lateinit var harness: ApiTestHarness
    private lateinit var repository: FullProductRepository

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        repository = FullProductRepository(
            harness.fullProductApi,
            UuidV7Generator(object : TimeSource {
                override fun nowEpochMillis() = 1_774_000_000_000L
            }),
            harness.json,
        )
    }

    @After
    fun tearDown() = harness.shutdown()

    @Test
    fun `boss challenge exposes no answer key and is scored by server`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":"boss-1","zoneId":"zone-1","title":"Reef Guardian","description":"Review the zone","questionCount":1,"passingPercentage":80.0,"rewardShells":25,"available":true,"questions":[{"id":"q1","prompt":"Choose a greeting","displayOrder":0,"options":[{"id":"o1","text":"Hello","displayOrder":0}]}]},"meta":{"requestId":"req-1"}}""",
            ),
        )
        val challenge = (repository.getActiveBossChallenge() as ApiResult.Success).value
        assertEquals("Hello", challenge.questions.single().options.single().text)
        assertTrue(challenge.available)
        assertEquals("/api/v1/me/boss-challenge", harness.server.takeRequest().path)

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"data":{"attemptId":"attempt-1","challengeId":"boss-1","submittedAt":"2026-07-30T08:00:00Z","totalQuestions":1,"correctAnswers":1,"scorePercentage":100.0,"passed":true,"rewardShells":25,"walletBalance":40},"meta":{"requestId":"req-2"}}""",
            ),
        )
        val pending = repository.prepareBossAttempt(
            challenge.id, listOf(BossAnswer("q1", "o1")),
        )
        val result = (repository.submitBossAttempt(pending) as ApiResult.Success).value
        assertTrue(result.passed)
        assertEquals(25, result.rewardShells)
        assertFalse(result.wasReplay)
        assertEquals(
            pending.idempotencyKey,
            harness.server.takeRequest().getHeader("Idempotency-Key"),
        )
    }

    @Test
    fun `economy and purchase values are copied from server`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"currency":"shell","balance":40,"shopItems":[{"id":"coral-theme","name":"Coral Theme","description":"Cosmetic colors","priceShells":30,"available":true,"owned":false}],"inventory":[]},"meta":{"requestId":"req-1"}}""",
            ),
        )
        val economy = (repository.getEconomy() as ApiResult.Success).value
        assertEquals(40, economy.balance)
        assertEquals(30, economy.shopItems.single().priceShells)
        assertEquals("/api/v1/me/economy", harness.server.takeRequest().path)

        harness.server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"data":{"purchaseId":"purchase-1","itemId":"coral-theme","priceShells":30,"balance":10,"inventoryQuantity":1,"purchasedAt":"2026-07-30T08:00:00Z"},"meta":{"requestId":"req-2"}}""",
            ),
        )
        val pending = repository.preparePurchase("coral-theme")
        val purchase = (repository.purchase(pending) as ApiResult.Success).value
        assertEquals(10, purchase.balance)
        assertEquals(1, purchase.inventoryQuantity)
        assertEquals(
            pending.idempotencyKey,
            harness.server.takeRequest().getHeader("Idempotency-Key"),
        )
    }
}
