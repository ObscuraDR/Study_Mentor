package com.elenglish.studymentor.core.uuid

import com.elenglish.studymentor.core.time.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The contract pins every public ID to lowercase UUIDv7. `UUID.randomUUID()` is
 * version 4 and would be rejected outright, so this generator is load-bearing.
 */
class UuidV7GeneratorTest {

    private var currentMillis = 1_700_000_000_000L
    private val timeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = currentMillis
    }
    private val generator = UuidV7Generator(timeSource)

    @Test
    fun `matches the contract's uuid v7 pattern`() {
        repeat(500) {
            currentMillis += 1
            val value = generator.newUuidV7()
            assertTrue("not a contract UUIDv7: $value", UUID_V7_REGEX.matches(value))
        }
    }

    @Test
    fun `a version 4 uuid would not satisfy the contract`() {
        // Guards against anyone 'simplifying' this to UUID.randomUUID().
        val v4 = UUID.randomUUID().toString()
        assertTrue(v4.isNotBlank())
        assertTrue(!UUID_V7_REGEX.matches(v4))
    }

    @Test
    fun `encodes the generation time in the leading 48 bits`() {
        currentMillis = 0x0192_3456_789AL

        val value = generator.newUuidV7()

        val timestampHex = value.substring(0, 8) + value.substring(9, 13)
        assertEquals(currentMillis, timestampHex.toLong(16))
    }

    @Test
    fun `later keys sort after earlier ones`() {
        currentMillis = 1_700_000_000_000L
        val earlier = generator.newUuidV7()
        currentMillis += 1_000
        val later = generator.newUuidV7()

        // UUIDv7 is time-ordered, which keeps idempotency keys sortable.
        assertTrue("$earlier should sort before $later", earlier < later)
    }

    @Test
    fun `two keys generated in the same millisecond still differ`() {
        val first = generator.newUuidV7()
        val second = generator.newUuidV7()

        assertNotEquals(first, second)
    }

    @Test
    fun `is parseable by java UUID and reports version 7`() {
        val parsed = UUID.fromString(generator.newUuidV7())

        assertEquals(7, parsed.version())
        assertEquals(2, parsed.variant())
    }

    @Test
    fun `is lowercase, as the contract requires`() {
        val value = generator.newUuidV7()
        assertEquals(value.lowercase(), value)
    }
}
