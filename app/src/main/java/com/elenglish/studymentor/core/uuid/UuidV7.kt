package com.elenglish.studymentor.core.uuid

import com.elenglish.studymentor.core.time.TimeSource
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates lowercase UUIDv7 identifiers, as every public ID and idempotency key
 * in OpenAPI v1 must be.
 *
 * `UUID.randomUUID()` is **version 4** and would be rejected by the contract's
 * pattern, which pins the version nibble to `7`.
 *
 * Layout (RFC 9562):
 * ```
 * unix_ts_ms : 48 bits   millisecond timestamp, so keys sort by creation time
 * ver        :  4 bits   always 0b0111
 * rand_a     : 12 bits   random
 * var        :  2 bits   always 0b10
 * rand_b     : 62 bits   random
 * ```
 */
interface UuidGenerator {
    fun newUuidV7(): String
}

@Singleton
class UuidV7Generator @Inject constructor(
    private val timeSource: TimeSource,
) : UuidGenerator {

    private val random = SecureRandom()

    override fun newUuidV7(): String {
        val timestampMillis = timeSource.nowEpochMillis()
        val randomBytes = ByteArray(RANDOM_BYTE_COUNT).also(random::nextBytes)

        // 48-bit big-endian timestamp.
        val timeHigh = (timestampMillis ushr 16) and 0xFFFF_FFFFL
        val timeLow = timestampMillis and 0xFFFFL

        // 12 bits of rand_a, with the version nibble prepended.
        val randA = ((randomBytes[0].toInt() and 0x0F) shl 8) or (randomBytes[1].toInt() and 0xFF)
        val versionAndRandA = (VERSION shl 12) or randA

        // 62 bits of rand_b, with the two variant bits at the top.
        val randBHigh = ((randomBytes[2].toInt() and 0x3F) shl 8) or (randomBytes[3].toInt() and 0xFF)
        val variantAndRandBHigh = (VARIANT shl 14) or randBHigh

        val randBLow = randomBytes.copyOfRange(4, RANDOM_BYTE_COUNT)
            .fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }

        return String.format(
            Locale.ROOT,
            "%08x-%04x-%04x-%04x-%012x",
            timeHigh,
            timeLow,
            versionAndRandA,
            variantAndRandBHigh,
            randBLow,
        )
    }

    private companion object {
        const val VERSION = 0x7
        const val VARIANT = 0b10
        const val RANDOM_BYTE_COUNT = 10
    }
}

/** The contract's UUIDv7 pattern, reused for validation and tests. */
val UUID_V7_REGEX =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
