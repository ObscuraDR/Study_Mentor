package com.elenglish.studymentor.data.campaign

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CampaignRepositoryTest {
    private lateinit var harness: ApiTestHarness
    private lateinit var repository: CampaignRepository

    @Before fun setUp() {
        harness = ApiTestHarness()
        repository = CampaignRepository(harness.campaignApi, harness.json)
    }

    @After fun tearDown() = harness.shutdown()

    @Test fun `maps backend ordering completion and recommendation without a campaign calculation`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.campaignEnvelope()))
        val result = repository.getCampaign() as ApiResult.Success
        assertEquals("core-learning-map", result.value.campaignKey)
        assertEquals("lesson-1", result.value.recommendedNodeId)
        assertEquals(listOf("lesson-0", "lesson-1"), result.value.zones.single().topics.single().lessons.map { it.lessonId })
        assertEquals(true, result.value.zones.single().topics.single().lessons.first().completed)
        assertEquals(50.0, result.value.completionPercentage, 0.0)
        assertTrue(result.value.zones.single().topics.single().lessons.none { it.state.name == "Unavailable" })
        assertEquals("/api/v1/me/campaign", harness.server.takeRequest().path)
    }

    @Test fun `campaign error surfaces as failure with the API path preserved`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(401).setBody(Fixtures.error("auth.session_expired")))
        assertTrue(repository.getCampaign() is ApiResult.Failure)
        assertEquals("/api/v1/me/campaign", harness.server.takeRequest().path)
    }

    @Test fun `empty zones list maps to campaign with zero progress`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"campaignKey":"core-learning-map","campaignVersion":"campaign-v1","catalogVersion":"catalog-v1","accessPolicy":"open-guided","recommendedNodeId":null,"completedLessons":0,"totalLessons":0,"completionPercentage":0.0,"zones":[]},"meta":{"requestId":"req-1"}}"""
            ),
        )
        val result = repository.getCampaign() as ApiResult.Success
        assertEquals(0, result.value.totalLessons)
        assertEquals(0, result.value.completedLessons)
        assertTrue(result.value.zones.isEmpty())
    }
}
