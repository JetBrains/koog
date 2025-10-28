package ai.koog.prompt.executor.clients.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryConfigTest {

    @Test
    fun `should create config with default values`() {
        val config = RetryConfig()

        assertEquals(3, config.maxAttempts)
        assertEquals(1.seconds, config.initialDelay)
        assertEquals(30.seconds, config.maxDelay)
        assertEquals(2.0, config.backoffMultiplier)
        assertEquals(0.1, config.jitterFactor)
        assertNotNull(config.retryAfterExtractor)
        assertTrue(config.retryablePatterns.isNotEmpty())
    }

    @Test
    fun `should validate maxAttempts`() {
        assertFailsWith<IllegalArgumentException> {
            RetryConfig(maxAttempts = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            RetryConfig(maxAttempts = -1)
        }

        // Should not throw
        RetryConfig(maxAttempts = 1)
        RetryConfig(maxAttempts = 100)
    }

    @Test
    fun `should validate backoffMultiplier`() {
        assertFailsWith<IllegalArgumentException> {
            RetryConfig(backoffMultiplier = 0.5)
        }

        assertFailsWith<IllegalArgumentException> {
            RetryConfig(backoffMultiplier = -1.0)
        }

        // Should not throw
        RetryConfig(backoffMultiplier = 1.0)
        RetryConfig(backoffMultiplier = 10.0)
    }

    @Test
    fun `should validate jitterFactor`() {
        assertFailsWith<IllegalArgumentException> {
            RetryConfig(jitterFactor = -0.1)
        }

        assertFailsWith<IllegalArgumentException> {
            RetryConfig(jitterFactor = 1.1)
        }

        // Should not throw
        RetryConfig(jitterFactor = 0.0)
        RetryConfig(jitterFactor = 0.5)
        RetryConfig(jitterFactor = 1.0)
    }

    @Test
    fun `should validate initialDelay not greater than maxDelay`() {
        assertFailsWith<IllegalArgumentException> {
            RetryConfig(
                initialDelay = 60.seconds,
                maxDelay = 30.seconds
            )
        }

        // Should not throw
        RetryConfig(
            initialDelay = 30.seconds,
            maxDelay = 60.seconds
        )
        RetryConfig(
            initialDelay = 30.seconds,
            maxDelay = 30.seconds
        )
    }

    @Test
    fun `CONSERVATIVE config should have expected values`() {
        val config = RetryConfig.CONSERVATIVE

        assertEquals(3, config.maxAttempts)
        assertEquals(2.seconds, config.initialDelay)
        assertEquals(30.seconds, config.maxDelay)
    }

    @Test
    fun `AGGRESSIVE config should have expected values`() {
        val config = RetryConfig.AGGRESSIVE

        assertEquals(5, config.maxAttempts)
        assertEquals(500.milliseconds, config.initialDelay)
        assertEquals(20.seconds, config.maxDelay)
        assertEquals(1.5, config.backoffMultiplier)
    }

    @Test
    fun `DISABLED config should have maxAttempts of 1`() {
        val config = RetryConfig.DISABLED

        assertEquals(1, config.maxAttempts)
    }

    @Test
    fun `default patterns should include common error codes`() {
        val patterns = RetryConfig.DEFAULT_PATTERNS

        // Check for important status codes
        assertTrue(patterns.any { it is RetryablePattern.Status && it.code == 429 })
        assertTrue(patterns.any { it is RetryablePattern.Status && it.code == 500 })
        assertTrue(patterns.any { it is RetryablePattern.Status && it.code == 503 })

        // Check for important keywords
        assertTrue(patterns.any { it is RetryablePattern.Keyword && it.keyword == "rate limit" })
        assertTrue(patterns.any { it is RetryablePattern.Keyword && it.keyword == "request timeout" })
    }
}

class RetryablePatternTest {

    @Test
    fun `Status pattern should match various formats`() {
        val pattern = RetryablePattern.Status(429)

        // Should match
        assertTrue(pattern.matches("Error 429"))
        assertTrue(pattern.matches("status: 429"))
        assertTrue(pattern.matches("status:429"))
        assertTrue(pattern.matches("HTTP 429 Too Many Requests"))
        assertTrue(pattern.matches("Error from API: 429"))

        // Should not match
        assertFalse(pattern.matches("Error 430"))
        assertFalse(pattern.matches("Error 42"))
        assertFalse(pattern.matches("429a"))
        assertFalse(pattern.matches("No status code here"))
    }

    @Test
    fun `Keyword pattern should be case insensitive`() {
        val pattern = RetryablePattern.Keyword("Rate Limit")

        // Should match
        assertTrue(pattern.matches("rate limit exceeded"))
        assertTrue(pattern.matches("RATE LIMIT"))
        assertTrue(pattern.matches("Rate Limit"))
        assertTrue(pattern.matches("You've hit the rate limit"))

        // Should not match
        assertFalse(pattern.matches("ratelimit"))
        assertFalse(pattern.matches("rate-limit"))
        assertFalse(pattern.matches("No matching text here"))
    }

    @Test
    fun `Regex pattern should match custom patterns`() {
        val pattern = RetryablePattern.Regex(Regex("CUSTOM_ERROR_\\d{3}"))

        // Should match
        assertTrue(pattern.matches("CUSTOM_ERROR_123"))
        assertTrue(pattern.matches("Got CUSTOM_ERROR_456 from service"))

        // Should not match (regex requires exactly 3 digits)
        assertFalse(pattern.matches("CUSTOM_ERROR_12"))
        assertFalse(pattern.matches("CUSTOM_ERROR_A123"))
        assertFalse(pattern.matches("CUSTOM_ERROR"))
    }

    @Test
    fun `Custom pattern should use provided matcher`() {
        val pattern = RetryablePattern.Custom { message ->
            message.contains("special") && message.length > 10
        }

        // Should match
        assertTrue(pattern.matches("This is a special error"))

        // Should not match
        assertFalse(pattern.matches("special")) // Too short
        assertFalse(pattern.matches("This is a normal error")) // No "special"
    }
}

class RetryAfterExtractorTest {

    @Test
    fun `should extract retry after in seconds`() {
        val extractor = DefaultRetryAfterExtractor

        assertEquals(5.seconds, extractor.extract("Retry after 5 seconds"))
        assertEquals(10.seconds, extractor.extract("retry-after: 10"))
        assertEquals(30.seconds, extractor.extract("Please retry after 30 seconds"))
        assertEquals(60.seconds, extractor.extract("Wait 60 seconds"))
        assertEquals(7.seconds, extractor.extract("try again in 7.934s"))
        assertEquals(7.seconds, extractor.extract("try again in 7.93s"))
        assertEquals(7.seconds, extractor.extract("try again in 7.9s"))
        assertEquals(7.seconds, extractor.extract("try again in 7s"))
    }

    @Test
    fun `should handle various formats`() {
        val extractor = DefaultRetryAfterExtractor

        assertEquals(5.seconds, extractor.extract("RETRY AFTER 5 SECONDS"))
        assertEquals(10.seconds, extractor.extract("Retry-After: 10"))
        assertEquals(15.seconds, extractor.extract("retry after 15 seconds"))
    }

    @Test
    fun `should return null when no retry-after found`() {
        val extractor = DefaultRetryAfterExtractor

        assertNull(extractor.extract("No retry information here"))
        assertNull(extractor.extract("Error 429 Too Many Requests"))
        assertNull(extractor.extract(""))
    }

    @Test
    fun `should extract first match when multiple present`() {
        val extractor = DefaultRetryAfterExtractor

        assertEquals(5.seconds, extractor.extract("Retry after 5 seconds, or wait 10 seconds"))
    }
}
