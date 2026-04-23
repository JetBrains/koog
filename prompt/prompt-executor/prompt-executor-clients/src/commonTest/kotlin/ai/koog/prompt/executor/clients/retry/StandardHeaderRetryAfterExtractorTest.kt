package ai.koog.prompt.executor.clients.retry

import ai.koog.http.client.KoogHttpClientException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StandardHeaderRetryAfterExtractorTest {

    private val extractor = StandardHeaderRetryAfterExtractor()

    private fun error(headers: Map<String, List<String>>): KoogHttpClientException =
        KoogHttpClientException(
            clientName = "TestClient",
            statusCode = 429,
            errorBody = null,
            message = null,
            cause = null,
            headers = headers
        )

    @Test
    fun testReturnsNullWhenNoHeaders() {
        assertNull(extractor.extract(error(emptyMap())))
    }

    @Test
    fun testReturnsNullWhenNoRelevantHeaders() {
        val e = error(mapOf("x-request-id" to listOf("abc-123"), "content-type" to listOf("application/json")))
        assertNull(extractor.extract(e))
    }

    @Test
    fun testExtractsNumericRetryAfter() {
        assertEquals(5.seconds, extractor.extract(error(mapOf("retry-after" to listOf("5")))))
        assertEquals(120.seconds, extractor.extract(error(mapOf("retry-after" to listOf("120")))))
    }

    @Test
    fun testIgnoresRetryAfterZeroAsNonCandidate() {
        // Zero is not a "nonzero" candidate per the extractor contract; no other hints → null.
        assertNull(extractor.extract(error(mapOf("retry-after" to listOf("0")))))
    }

    @Test
    fun testIgnoresNegativeRetryAfter() {
        assertNull(extractor.extract(error(mapOf("retry-after" to listOf("-3")))))
    }

    @Test
    fun testIgnoresUnparseableRetryAfter() {
        assertNull(extractor.extract(error(mapOf("retry-after" to listOf("soon")))))
        assertNull(extractor.extract(error(mapOf("retry-after" to listOf("")))))
    }

    @Test
    fun testExtractsHttpDateRetryAfter() {
        // Reference "now" = 2026-01-01T00:00:00Z; server says retry at 2026-01-01T00:00:42Z.
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val fixedExtractor = StandardHeaderRetryAfterExtractor(clock = fixedClockAt(now))

        val e = error(mapOf("retry-after" to listOf("Thu, 01 Jan 2026 00:00:42 GMT")))
        assertEquals(42.seconds, fixedExtractor.extract(e))
    }

    @Test
    fun testHttpDateRetryAfterInPastClampsToZeroAndIsIgnored() {
        val now = Instant.parse("2026-01-01T00:01:00Z")
        val fixedExtractor = StandardHeaderRetryAfterExtractor(clock = fixedClockAt(now))

        val e = error(mapOf("retry-after" to listOf("Thu, 01 Jan 2026 00:00:00 GMT")))
        // Past date → Duration.ZERO → filtered out as non-positive → null.
        assertNull(fixedExtractor.extract(e))
    }

    @Test
    fun testExtractsOpenAIStyleResetRequests() {
        assertEquals(1.seconds, extractor.extract(error(mapOf("x-ratelimit-reset-requests" to listOf("1s")))))
        assertEquals(100.milliseconds, extractor.extract(error(mapOf("x-ratelimit-reset-requests" to listOf("100ms")))))
    }

    @Test
    fun testExtractsOpenAIStyleResetTokens() {
        assertEquals(
            (6 * 60).seconds,
            extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("6m0s"))))
        )
    }

    @Test
    fun testParsesCompoundOpenAIDuration() {
        val e = error(mapOf("x-ratelimit-reset-tokens" to listOf("1h2m3s")))
        assertEquals((1 * 3600 + 2 * 60 + 3).seconds, extractor.extract(e))
    }

    @Test
    fun testRejectsMalformedOpenAIDuration() {
        // Extra characters that don't match any token should reject the whole value.
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("6minutes")))))
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("1s foo")))))
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("")))))
    }

    @Test
    fun testZeroHintSkippedWhenAnotherHintWins() {
        // A zero-duration hint must be filtered as "no hint" rather than chosen as the
        // minimum, so the next-smallest positive value wins. Otherwise a server returning
        // `x-ratelimit-reset-requests: 0s` would clobber a usable retry-after.
        val e = error(
            mapOf(
                "retry-after" to listOf("10"),
                "x-ratelimit-reset-requests" to listOf("0s")
            )
        )
        assertEquals(10.seconds, extractor.extract(e))
    }

    @Test
    fun testReturnsSmallestNonzeroWhenMultipleHeadersPresent() {
        val e = error(
            mapOf(
                "retry-after" to listOf("10"),
                "x-ratelimit-reset-requests" to listOf("500ms"),
                "x-ratelimit-reset-tokens" to listOf("6m0s")
            )
        )
        assertEquals(500.milliseconds, extractor.extract(e))
    }

    @Test
    fun testIgnoresHeadersWithUppercaseKeysFromCaller() {
        // Extractor looks headers up by lowercase key. KoogHttpClientException.headers is
        // contractually lowercase; if a caller builds an exception with uppercase keys by
        // mistake, the extractor simply finds nothing.
        val e = error(mapOf("Retry-After" to listOf("5")))
        assertNull(extractor.extract(e))
    }

    @Test
    fun testExtractOnMessageAlwaysReturnsNull() {
        // StandardHeaderRetryAfterExtractor intentionally does not parse free-form messages;
        // compose with DefaultRetryAfterExtractor for that.
        assertNull(extractor.extract("Retry after 5 seconds"))
    }

    @Test
    fun testCompositeExtractorPrefersHeadersThenMessage() {
        val composite = CompositeRetryAfterExtractor(
            StandardHeaderRetryAfterExtractor.DEFAULT,
            DefaultRetryAfterExtractor
        )

        val withHeader = KoogHttpClientException(
            clientName = "TestClient",
            statusCode = 429,
            errorBody = "Retry after 99 seconds",
            message = "Retry after 99 seconds",
            headers = mapOf("retry-after" to listOf("3"))
        )
        assertEquals(3.seconds, composite.extract(withHeader))

        val withoutHeader = KoogHttpClientException(
            clientName = "TestClient",
            statusCode = 429,
            errorBody = "Retry after 99 seconds",
            message = "Retry after 99 seconds",
            headers = emptyMap()
        )
        assertEquals(99.seconds, composite.extract(withoutHeader))
    }

    private fun fixedClockAt(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }
}
