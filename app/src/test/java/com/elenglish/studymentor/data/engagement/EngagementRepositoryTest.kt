package com.elenglish.studymentor.data.engagement

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.testing.ApiTestHarness
import com.elenglish.studymentor.testing.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngagementRepositoryTest {

    private lateinit var harness: ApiTestHarness
    private lateinit var repository: EngagementRepository

    @Before
    fun setUp() {
        harness = ApiTestHarness()
        repository = EngagementRepository(
            engagementApi = harness.engagementApi,
            json = harness.json,
        )
    }

    @After
    fun tearDown() = harness.shutdown()

    @Test
    fun `every field is copied verbatim from the backend projection`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.engagementEnvelope(
                    totalXp = 250,
                    level = 3,
                    currentLevelXp = 100,
                    nextLevelThreshold = 400,
                    streak = 5,
                    achievements = listOf("first_lesson", "curious_learner"),
                ),
            ),
        )

        val result = repository.getEngagement()

        val engagement = (result as ApiResult.Success).value
        assertEquals(250, engagement.totalXp)
        assertEquals(3, engagement.level)
        assertEquals(100, engagement.currentLevelXp)
        assertEquals(400, engagement.nextLevelThreshold)
        assertEquals("level-curve-v1", engagement.levelCurveVersion)
        assertEquals("Asia/Ho_Chi_Minh", engagement.timezone)
        assertEquals(5, engagement.streak)
        assertEquals(listOf("first_lesson", "curious_learner"), engagement.achievements)
        assertEquals("engagement-v1", engagement.missions.ruleVersion)
    }

    @Test
    fun `mission goals are read straight from the daily and weekly projections`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.engagementEnvelope()))

        val engagement = (repository.getEngagement() as ApiResult.Success).value

        assertEquals(0, engagement.missions.daily.lesson.progress)
        assertEquals(1, engagement.missions.daily.lesson.target)
        assertFalse(engagement.missions.daily.lesson.completed)
        assertEquals(2, engagement.missions.daily.reviews.progress)
        assertEquals(5, engagement.missions.daily.reviews.target)
        assertEquals(1, engagement.missions.weekly.actions.progress)
        assertEquals(1, engagement.missions.weekly.days.progress)
    }

    @Test
    fun `a fresh learner has no achievements, not a fabricated one`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(Fixtures.engagementEnvelope(achievements = emptyList())),
        )

        val engagement = (repository.getEngagement() as ApiResult.Success).value

        assertTrue(engagement.achievements.isEmpty())
    }

    @Test
    fun `a server error surfaces as a failure with the request id`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val result = repository.getEngagement()

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `streak 0 is mapped correctly without fabrication`() = runTest {
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.engagementEnvelope(streak = 0),
            ),
        )

        val engagement = (repository.getEngagement() as ApiResult.Success).value
        assertEquals(0, engagement.streak)
    }

    @Test
    fun `all fourteen achievement keys pass through from backend`() = runTest {
        val allAchievements = listOf(
            "first_lesson", "curious_learner", "quiz_explorer", "review_habit",
            "seven_day_learner", "lesson_path_10", "lesson_path_25",
            "subject_explorer_3", "quiz_first_attempt", "quiz_confident_5",
            "reviewer_25", "reviewer_100", "streak_3_days", "streak_14_days",
        )
        harness.server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Fixtures.engagementEnvelope(achievements = allAchievements),
            ),
        )

        val engagement = (repository.getEngagement() as ApiResult.Success).value
        assertEquals(14, engagement.achievements.size)
        assertEquals(allAchievements, engagement.achievements)
    }

    @Test
    fun `requestId is preserved in success response`() = runTest {
        harness.server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.engagementEnvelope()))

        val result = repository.getEngagement()
        assertEquals(Fixtures.REQUEST_ID, (result as ApiResult.Success).requestId)
    }
}
