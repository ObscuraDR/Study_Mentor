package com.elenglish.studymentor.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the canonical OpenAPI v1 envelope shape. Fixtures mirror
 * `contracts/openapi/ai-study-mentor.v1.openapi.json`.
 */
class ApiEnvelopeTest {

    @Serializable
    private data class SubjectFixture(
        val id: String,
        val slug: String,
        val name: String,
        val displayOrder: Int,
        val active: Boolean,
    )

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }

    @Test
    fun `parses a success envelope and retains the request id`() {
        val payload = """
            {
              "data": [
                {
                  "id": "0191f3a0-7d5c-7b3a-9f11-5b8a0c2d4e6f",
                  "slug": "english-grammar",
                  "name": "English Grammar",
                  "displayOrder": 0,
                  "active": true
                }
              ],
              "meta": { "requestId": "0191f3a0-7d5c-7b3a-9f11-000000000001" }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(SubjectFixture.serializer())),
            payload,
        )

        assertEquals(1, envelope.data.size)
        assertEquals("english-grammar", envelope.data.first().slug)
        assertEquals(0, envelope.data.first().displayOrder)
        assertEquals("0191f3a0-7d5c-7b3a-9f11-000000000001", envelope.meta?.requestId)
    }

    @Test
    fun `parses an error envelope including details`() {
        val payload = """
            {
              "error": {
                "code": "validation.invalid_field",
                "message": "displayName must not be empty",
                "details": [ { "field": "displayName", "issue": "required" } ],
                "requestId": "0191f3a0-7d5c-7b3a-9f11-000000000002"
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ApiErrorEnvelope.serializer(), payload)

        assertEquals("validation.invalid_field", envelope.error.code)
        assertEquals("displayName", envelope.error.details?.single()?.field)
        assertEquals("0191f3a0-7d5c-7b3a-9f11-000000000002", envelope.error.requestId)
    }

    @Test
    fun `tolerates unknown fields so an additive backend change does not break the client`() {
        val payload = """
            {
              "error": {
                "code": "server.internal",
                "message": "Unexpected error",
                "requestId": "0191f3a0-7d5c-7b3a-9f11-000000000003",
                "futureField": "ignored"
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ApiErrorEnvelope.serializer(), payload)

        assertEquals("server.internal", envelope.error.code)
        assertNull(envelope.error.details)
    }
}
