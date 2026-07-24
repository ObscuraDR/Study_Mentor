package com.elenglish.studymentor.data.tutor

import com.elenglish.studymentor.data.remote.dto.TutorResponseRequestDto
import com.elenglish.studymentor.data.remote.dto.TutorResponseDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A learner's question and the tutor's answer are personal content: they reveal
 * what someone was struggling with. Neither may be reachable through
 * `toString()`, which is what a stray log line or crash report would capture.
 */
class TutorPrivacyTest {

    private val prompt = "I keep failing my exam, can you help with past tense"
    private val answer = "Here is a detailed explanation of the past tense"

    @Test
    fun `a tutor request never prints the learner's message`() {
        val rendered = TutorResponseRequestDto(lessonId = "lesson-1", message = prompt).toString()

        assertFalse("prompt leaked in: $rendered", rendered.contains(prompt))
        assertFalse(rendered.contains("failing"))
        // The lesson id is not sensitive and stays, so a log line is still useful.
        assertTrue(rendered.contains("lesson-1"))
    }

    @Test
    fun `a tutor response never prints the answer`() {
        val rendered = TutorResponseDto(
            responseId = "response-1",
            lessonId = "lesson-1",
            answer = answer,
            createdAt = "2026-07-23T08:00:00Z",
            status = "completed",
        ).toString()

        assertFalse("answer leaked in: $rendered", rendered.contains(answer))
        // Status and id remain, so diagnostics still work.
        assertTrue(rendered.contains("completed"))
        assertTrue(rendered.contains("response-1"))
    }

    @Test
    fun `the redacted request still reports the message length for diagnostics`() {
        val rendered = TutorResponseRequestDto("lesson-1", "12345").toString()

        assertTrue(rendered.contains("5 chars"))
    }
}
