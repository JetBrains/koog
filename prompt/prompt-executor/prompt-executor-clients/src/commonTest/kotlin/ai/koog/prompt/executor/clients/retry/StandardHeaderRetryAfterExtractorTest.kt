package ai.koog.prompt.executor.clients.retry

import ai.koog.http.client.KoogHttpClientException
import ai.koog.utils.time.KoogClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        // Zero means "no usable hint", not "retry immediately"; no other hints -> null.
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
    fun testIgnoresHugeRetryAfterThatSaturatesToInfinite() {
        // Long.MAX_VALUE seconds saturates to Duration.INFINITE; an infinite "hint" must be
        // discarded instead of stalling the retry loop forever.
        assertNull(extractor.extract(error(mapOf("retry-after" to listOf("9223372036854775807")))))
    }

    @Test
    fun testExtractsHttpDateRetryAfter() {
        // Reference "now" = 2026-01-01T00:00:00Z; server says retry at 2026-01-01T00:00:42Z.
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val fixedExtractor = StandardHeaderRetryAfterExtractor(clock = KoogClock { now })

        val e = error(mapOf("retry-after" to listOf("Thu, 01 Jan 2026 00:00:42 GMT")))
        assertEquals(42.seconds, fixedExtractor.extract(e))
    }

    @Test
    fun testHttpDateRetryAfterInPastIsIgnored() {
        val now = Instant.parse("2026-01-01T00:01:00Z")
        val fixedExtractor = StandardHeaderRetryAfterExtractor(clock = KoogClock { now })

        val e = error(mapOf("retry-after" to listOf("Thu, 01 Jan 2026 00:00:00 GMT")))
        // Past date -> negative delta -> filtered out as non-positive -> null.
        assertNull(fixedExtractor.extract(e))
    }

    @Test
    fun testConsidersAllValuesOfRepeatedHeader() {
        // A merged/repeated header may carry an unusable first value; later usable values
        // must still be consulted instead of being masked by the first.
        assertEquals(5.seconds, extractor.extract(error(mapOf("retry-after" to listOf("", "5")))))
        assertEquals(30.seconds, extractor.extract(error(mapOf("retry-after" to listOf("0", "30")))))
    }

    @Test
    fun testParsesCommaFoldedRetryAfter() {
        // Some proxies fold repeated headers into one comma-separated value.
        assertEquals(5.seconds, extractor.extract(error(mapOf("retry-after" to listOf("5, 10")))))
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
        // Values that are not a well-formed duration must reject as a whole.
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("6minutes")))))
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("1s foo")))))
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("")))))
    }

    @Test
    fun testRejectsNonFiniteResetDuration() {
        // Duration.parseOrNull accepts "Infinity"; the extractor must not surface it as a hint.
        assertNull(extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("Infinity")))))
        assertNull(
            extractor.extract(error(mapOf("x-ratelimit-reset-tokens" to listOf("99999999999999999999999999s"))))
        )
    }

    @Test
    fun testRetryAfterPreferredOverResetDurations() {
        // retry-after is authoritative (RFC 9110): even a much smaller informational bucket
        // reset must not undercut it and trigger an immediate re-throttled retry.
        val e = error(
            mapOf(
                "retry-after" to listOf("10"),
                "x-ratelimit-reset-requests" to listOf("500ms"),
                "x-ratelimit-reset-tokens" to listOf("6m0s")
            )
        )
        assertEquals(10.seconds, extractor.extract(e))
    }

    @Test
    fun testZeroResetDurationDoesNotClobberRetryAfter() {
        val e = error(
            mapOf(
                "retry-after" to listOf("10"),
                "x-ratelimit-reset-requests" to listOf("0s")
            )
        )
        assertEquals(10.seconds, extractor.extract(e))
    }

    @Test
    fun testSmallestResetDurationWinsWhenRetryAfterUnusable() {
        // With retry-after absent or unusable, the smallest positive bucket reset is the
        // earliest moment a retry can succeed.
        val withoutRetryAfter = error(
            mapOf(
                "x-ratelimit-reset-requests" to listOf("500ms"),
                "x-ratelimit-reset-tokens" to listOf("6m0s")
            )
        )
        assertEquals(500.milliseconds, extractor.extract(withoutRetryAfter))

        val withZeroRetryAfter = error(
            mapOf(
                "retry-after" to listOf("0"),
                "x-ratelimit-reset-tokens" to listOf("2s")
            )
        )
        assertEquals(2.seconds, extractor.extract(withZeroRetryAfter))
    }

    @Test
    fun testNormalizesUppercaseKeysFromCaller() {
        // KoogHttpClientException lowercases header keys in its constructor, so even an
        // exception built directly with native-cased keys works with the extractor.
        val e = error(mapOf("Retry-After" to listOf("5")))
        assertEquals(5.seconds, extractor.extract(e))
    }

    @Test
    fun testCustomHeaderNames() {
        // Header names are configurable per provider and lowercased on construction.
        val custom = StandardHeaderRetryAfterExtractor(
            retryAfterHeaders = listOf("X-Custom-Retry"),
            resetDurationHeaders = listOf("x-custom-reset")
        )

        assertEquals(7.seconds, custom.extract(error(mapOf("x-custom-retry" to listOf("7")))))
        assertEquals(3.seconds, custom.extract(error(mapOf("x-custom-reset" to listOf("3s")))))
        // The defaults are replaced, not extended.
        assertNull(custom.extract(error(mapOf("retry-after" to listOf("5")))))
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
}
