package com.elenglish.studymentor.testing

import com.elenglish.studymentor.core.network.AccessTokenInterceptor
import com.elenglish.studymentor.core.network.ClientPlatformInterceptor
import com.elenglish.studymentor.core.network.RefreshTokenAuthenticator
import com.elenglish.studymentor.core.session.SessionScopedStore
import com.elenglish.studymentor.core.session.SessionStateHolder
import com.elenglish.studymentor.data.remote.AccountApi
import com.elenglish.studymentor.data.remote.AuthApi
import com.elenglish.studymentor.data.remote.CatalogApi
import com.elenglish.studymentor.data.remote.FlashcardApi
import com.elenglish.studymentor.data.remote.LearningApi
import com.elenglish.studymentor.data.remote.QuizApi
import com.elenglish.studymentor.data.remote.TutorApi
import com.elenglish.studymentor.data.session.AccessTokenHolder
import com.elenglish.studymentor.data.session.RefreshTokenStorage
import com.elenglish.studymentor.data.session.SessionRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import javax.inject.Provider

/** In-memory stand-in for the Keystore-backed store. */
class FakeRefreshTokenStorage(initial: String? = null) : RefreshTokenStorage {
    var stored: String? = initial
        private set
    var writeCount: Int = 0
        private set

    override fun read(): String? = stored

    override fun write(token: String) {
        stored = token
        writeCount++
    }

    override fun clear() {
        stored = null
    }
}

/**
 * Wires the real interceptor/authenticator stack against a [MockWebServer], so
 * header rules, refresh coordination and retry limits are exercised end to end
 * rather than mocked away.
 */
class ApiTestHarness(
    refreshToken: String? = null,
    private val sessionScopedStores: Set<SessionScopedStore> = emptySet(),
) {
    val server = MockWebServer()

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }

    val accessTokenHolder = AccessTokenHolder()
    val refreshTokenStorage = FakeRefreshTokenStorage(refreshToken)
    val sessionStateHolder = SessionStateHolder()

    lateinit var sessionRepository: SessionRepository
        private set

    val authApi: AuthApi
    val accountApi: AccountApi
    val catalogApi: CatalogApi
    val learningApi: LearningApi
    val quizApi: QuizApi
    val tutorApi: TutorApi
    val flashcardApi: FlashcardApi

    init {
        server.start()

        val authenticator = RefreshTokenAuthenticator(
            sessionRepositoryProvider = Provider { sessionRepository },
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(ClientPlatformInterceptor())
            .addInterceptor(AccessTokenInterceptor(accessTokenHolder))
            .authenticator(authenticator)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApi = retrofit.create(AuthApi::class.java)
        accountApi = retrofit.create(AccountApi::class.java)
        catalogApi = retrofit.create(CatalogApi::class.java)
        learningApi = retrofit.create(LearningApi::class.java)
        quizApi = retrofit.create(QuizApi::class.java)
        tutorApi = retrofit.create(TutorApi::class.java)
        flashcardApi = retrofit.create(FlashcardApi::class.java)

        sessionRepository = SessionRepository(
            authApiProvider = { authApi },
            accessTokenHolder = accessTokenHolder,
            refreshTokenStorage = refreshTokenStorage,
            sessionStateHolder = sessionStateHolder,
            json = json,
            sessionScopedStores = sessionScopedStores,
        )
    }

    /**
     * Serves responses by request path instead of FIFO arrival order.
     *
     * A ViewModel that fires two requests concurrently (e.g. a list plus its
     * parent's name) reaches the server in a non-deterministic order on an
     * unconfined dispatcher, so the default queue dispatcher would hand the
     * wrong body to the wrong call. [routes] maps a path substring to its body.
     */
    fun respondByPath(routes: Map<String, String>) {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = routes.entries.firstOrNull { path.contains(it.key) }?.value
                return if (body != null) {
                    MockResponse().setResponseCode(200).setBody(body)
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private var stopped = false

    /**
     * Makes the backend genuinely unreachable, the way a device without a
     * network is. `SocketPolicy.DISCONNECT_AT_START` is not equivalent: a
     * truncated response surfaces as a decode failure rather than a connection
     * failure, which is a different error class entirely.
     */
    fun goOffline() = shutdown()

    fun shutdown() {
        if (!stopped) {
            stopped = true
            server.shutdown()
        }
    }
}

/** Canonical response fixtures matching `ai-study-mentor.v1.openapi.json`. */
object Fixtures {
    const val USER_ID = "0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f"
    const val REQUEST_ID = "0191f3a0-7d5c-7b3a-9f11-000000000001"

    fun session(
        accessToken: String = "access-1",
        refreshToken: String? = "refresh-1",
    ): String = """
        {
          "data": {
            "user": {
              "id": "$USER_ID",
              "displayName": "Mai",
              "email": "mai@example.com",
              "createdAt": "2026-07-20T08:00:00Z"
            },
            "accessToken": "$accessToken",
            "accessTokenExpiresAt": "2026-07-20T09:00:00Z"
            ${refreshToken?.let { ""","refreshToken": "$it",
            "refreshTokenExpiresAt": "2026-07-27T08:00:00Z"""" } ?: ""}
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun refreshedSession(
        accessToken: String = "access-2",
        refreshToken: String? = "refresh-2",
    ): String = """
        {
          "data": {
            "accessToken": "$accessToken",
            "accessTokenExpiresAt": "2026-07-20T10:00:00Z"
            ${refreshToken?.let { ""","refreshToken": "$it",
            "refreshTokenExpiresAt": "2026-07-27T08:00:00Z"""" } ?: ""}
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun user(): String = """
        {
          "data": {
            "id": "$USER_ID",
            "displayName": "Mai",
            "email": "mai@example.com",
            "createdAt": "2026-07-20T08:00:00Z"
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun profile(revision: String = "rev-1", educationLevel: String? = "intermediate"): String = """
        {
          "data": {
            "id": "$USER_ID",
            "displayName": "Mai",
            "email": "mai@example.com",
            "avatarKey": null,
            "educationLevel": ${educationLevel?.let { "\"$it\"" } ?: "null"},
            "updatedAt": "2026-07-20T08:00:00Z",
            "revision": "$revision"
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun settings(revision: String = "rev-1", locale: String = "en", dailyGoal: Int = 50): String = """
        {
          "data": {
            "locale": "$locale",
            "dailyGoalTargetXp": $dailyGoal,
            "updatedAt": "2026-07-20T08:00:00Z",
            "revision": "$revision"
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun subjects(vararg rows: Triple<String, String, Int>): String {
        val items = rows.joinToString(",") { (id, name, displayOrder) ->
            """{"id":"$id","slug":"slug-$id","name":"$name","displayOrder":$displayOrder,"active":true}"""
        }
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    fun topics(subjectId: String, vararg rows: Pair<String, String>): String {
        val items = rows.mapIndexed { index, (id, name) ->
            """{"id":"$id","subjectId":"$subjectId","slug":"slug-$id","name":"$name","displayOrder":$index,"active":true}"""
        }.joinToString(",")
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    fun lessons(topicId: String, vararg rows: Pair<String, String>): String {
        val items = rows.mapIndexed { index, (id, title) ->
            """{"id":"$id","topicId":"$topicId","slug":"slug-$id","title":"$title","description":"About $title","estimatedMinutes":10,"difficulty":"beginner","displayOrder":$index,"active":true}"""
        }.joinToString(",")
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    fun lesson(id: String, title: String, difficulty: String = "beginner"): String = """
        {
          "data": {
            "id": "$id",
            "topicId": "topic-1",
            "slug": "slug-$id",
            "title": "$title",
            "description": "About $title",
            "estimatedMinutes": 12,
            "difficulty": "$difficulty",
            "displayOrder": 0,
            "active": true
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun progress(
        completedLessons: Int = 1,
        totalLessons: Int = 3,
        totalXp: Int = 10,
        learningTimeSeconds: Int = 600,
        completionPercentage: Double = 33.33,
    ): String = """
        {
          "completedLessons": $completedLessons,
          "totalLessons": $totalLessons,
          "completedTopics": 0,
          "totalTopics": 2,
          "completedSubjects": 0,
          "totalSubjects": 1,
          "totalXp": $totalXp,
          "learningTimeSeconds": $learningTimeSeconds,
          "completionPercentage": $completionPercentage
        }
    """.trimIndent()

    fun progressEnvelope(totalXp: Int = 10): String =
        """{"data":${progress(totalXp = totalXp)},"meta":{"requestId":"$REQUEST_ID"}}"""

    /** [rows] are lessonId to completedAt. Empty by default: no completions yet. */
    fun lessonCompletions(vararg rows: Pair<String, String>): String {
        val items = rows.joinToString(",") { (lessonId, completedAt) ->
            """{"lessonId":"$lessonId","completedAt":"$completedAt"}"""
        }
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    fun learningEventSubmission(
        eventId: String = "0191f3a0-7d5c-7b3a-9f11-000000000abc",
        lessonId: String = "0191f3a0-7d5c-7b3a-9f11-000000000def",
        xpEarned: Int = 10,
        totalXp: Int = 10,
    ): String = """
        {
          "data": {
            "event": {
              "id": "$eventId",
              "userId": "$USER_ID",
              "lessonId": "$lessonId",
              "occurredAt": "2026-07-23T08:00:00Z",
              "xpEarned": $xpEarned,
              "durationSeconds": 600,
              "eventType": "lesson.completed",
              "acceptedAt": "2026-07-23T08:00:01Z"
            },
            "progress": ${progress(totalXp = totalXp)}
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun quizzes(lessonId: String, vararg rows: Pair<String, String>): String {
        val items = rows.mapIndexed { index, (id, title) ->
            """{"id":"$id","lessonId":"$lessonId","title":"$title","description":null,"questionCount":2,"displayOrder":$index,"active":true}"""
        }.joinToString(",")
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    /**
     * A quiz detail. Deliberately carries no answer key — the contract does not
     * expose one, which is what stops a client scoring locally.
     */
    fun quizDetail(quizId: String = "quiz-1", lessonId: String = "lesson-1"): String = """
        {
          "data": {
            "id": "$quizId",
            "lessonId": "$lessonId",
            "title": "Greetings check",
            "description": null,
            "questionCount": 2,
            "displayOrder": 0,
            "active": true,
            "questions": [
              {
                "id": "q1",
                "prompt": "Which greeting suits the morning?",
                "type": "single-choice",
                "displayOrder": 0,
                "options": [
                  { "id": "q1o1", "text": "Good night", "displayOrder": 0 },
                  { "id": "q1o2", "text": "Good morning", "displayOrder": 1 }
                ]
              },
              {
                "id": "q2",
                "prompt": "How do you say goodbye politely?",
                "type": "single-choice",
                "displayOrder": 1,
                "options": [
                  { "id": "q2o1", "text": "See you later", "displayOrder": 0 },
                  { "id": "q2o2", "text": "Go away", "displayOrder": 1 }
                ]
              }
            ]
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun quizAttemptResult(
        correctAnswers: Int = 1,
        totalQuestions: Int = 2,
        scorePercentage: Double = 50.0,
    ): String = """
        {
          "data": {
            "attemptId": "0191f3a0-7d5c-7b3a-9f11-0000000a11ce",
            "quizId": "quiz-1",
            "submittedAt": "2026-07-23T08:00:00Z",
            "totalQuestions": $totalQuestions,
            "correctAnswers": $correctAnswers,
            "scorePercentage": $scorePercentage,
            "questionResults": [
              {
                "questionId": "q1",
                "selectedOptionId": "q1o2",
                "correct": true,
                "correctOptionId": "q1o2"
              },
              {
                "questionId": "q2",
                "selectedOptionId": "q2o2",
                "correct": false,
                "correctOptionId": "q2o1"
              }
            ]
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun tutorResponse(
        answer: String = "Use 'good evening' after about 6pm.",
        status: String = "completed",
        responseId: String = "0191f3a0-7d5c-7b3a-9f11-00000000ab1e",
    ): String = """
        {
          "data": {
            "responseId": "$responseId",
            "lessonId": "lesson-1",
            "answer": "$answer",
            "createdAt": "2026-07-23T08:00:00Z",
            "status": "$status"
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun flashcardDecks(lessonId: String, vararg rows: Pair<String, String>): String {
        val items = rows.mapIndexed { index, (id, name) ->
            """{"id":"$id","lessonId":"$lessonId","slug":"slug-$id","name":"$name","description":null,"cardCount":3,"displayOrder":$index,"active":true}"""
        }.joinToString(",")
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    fun flashcardQueue(deckId: String, vararg cards: Triple<String, String, Int>): String {
        val items = cards.joinToString(",") { (id, front, box) ->
            """{"card":{"id":"$id","deckId":"$deckId","front":"$front","back":"About $front","hint":null,"displayOrder":0,"active":true},"state":{"cardId":"$id","box":$box,"dueAt":"2026-07-23T08:00:00Z","lastReviewedAt":null,"totalReviews":0,"knownReviews":0,"algorithmVersion":"leitner-5box-v1"}}"""
        }
        return """{"data":[$items],"meta":{"requestId":"$REQUEST_ID"}}"""
    }

    fun flashcardReviewResult(
        cardId: String = "card-1",
        box: Int = 2,
        dueAt: String = "2026-07-25T08:00:00Z",
    ): String = """
        {
          "data": {
            "reviewId": "0191f3a0-7d5c-7b3a-9f11-0000000fc1d1",
            "state": {
              "cardId": "$cardId",
              "box": $box,
              "dueAt": "$dueAt",
              "lastReviewedAt": "2026-07-23T08:00:00Z",
              "totalReviews": 1,
              "knownReviews": 1,
              "algorithmVersion": "leitner-5box-v1"
            },
            "acceptedAt": "2026-07-23T08:00:01Z"
          },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun flashcardResetResult(deckId: String = "deck-1", cardsReset: Int = 3): String = """
        {
          "data": { "deckId": "$deckId", "cardsReset": $cardsReset, "resetAt": "2026-07-23T08:00:00Z" },
          "meta": { "requestId": "$REQUEST_ID" }
        }
    """.trimIndent()

    fun error(code: String, message: String = "Failed."): String = """
        {
          "error": {
            "code": "$code",
            "message": "$message",
            "requestId": "$REQUEST_ID"
          }
        }
    """.trimIndent()
}
