package ai.koog.prompt.executor.clients.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.time.Duration as JavaDuration

class RetryConfigBuilderTest {

    @Test
    fun testBuilderWithDefaults() {
        val config = RetryConfigBuilder().build()
        val defaults = RetryConfig()

        assertEquals(defaults.maxAttempts, config.maxAttempts)
        assertEquals(defaults.initialDelay, config.initialDelay)
        assertEquals(defaults.maxDelay, config.maxDelay)
        assertEquals(defaults.backoffMultiplier, config.backoffMultiplier)
        assertEquals(defaults.jitterFactor, config.jitterFactor)
        assertEquals(defaults.retryablePatterns, config.retryablePatterns)
        assertEquals(defaults.retryAfterExtractor, config.retryAfterExtractor)
    }

    @Test
    fun testBuilderWithAllKotlinDurations() {
        val config = RetryConfigBuilder()
            .maxAttempts(5)
            .initialDelay(500.milliseconds)
            .maxDelay(60.seconds)
            .backoffMultiplier(1.5)
            .jitterFactor(0.2)
            .build()

        assertEquals(5, config.maxAttempts)
        assertEquals(500.milliseconds, config.initialDelay)
        assertEquals(60.seconds, config.maxDelay)
        assertEquals(1.5, config.backoffMultiplier)
        assertEquals(0.2, config.jitterFactor)
    }

    @Test
    fun testBuilderWithJavaDurations() {
        val config = RetryConfigBuilder()
            .maxAttempts(4)
            .initialDelay(JavaDuration.ofSeconds(2))
            .maxDelay(JavaDuration.ofMinutes(1))
            .build()

        assertEquals(4, config.maxAttempts)
        assertEquals(2.seconds, config.initialDelay)
        assertEquals(60.seconds, config.maxDelay)
    }

    @Test
    fun testBuilderWithMixedDurations() {
        val config = RetryConfigBuilder()
            .initialDelay(JavaDuration.ofMillis(500))
            .maxDelay(30.seconds)
            .build()

        assertEquals(500.milliseconds, config.initialDelay)
        assertEquals(30.seconds, config.maxDelay)
    }

    @Test
    fun testBuilderWithRetryablePatterns() {
        val patterns = listOf(
            RetryablePattern.Status(429),
            RetryablePattern.Keyword("rate limit")
        )
        val config = RetryConfigBuilder()
            .retryablePatterns(patterns)
            .build()

        assertEquals(patterns, config.retryablePatterns)
    }

    @Test
    fun testBuilderWithNullRetryAfterExtractor() {
        val config = RetryConfigBuilder()
            .retryAfterExtractor(null)
            .build()

        assertNull(config.retryAfterExtractor)
    }

    @Test
    fun testBuilderWithCustomRetryAfterExtractor() {
        val extractor = RetryAfterExtractor { null }
        val config = RetryConfigBuilder()
            .retryAfterExtractor(extractor)
            .build()

        assertEquals(extractor, config.retryAfterExtractor)
    }

    @Test
    fun testCompanionBuilderExtension() {
        val config = RetryConfig.builder()
            .maxAttempts(2)
            .initialDelay(JavaDuration.ofSeconds(1))
            .maxDelay(JavaDuration.ofSeconds(10))
            .build()

        assertEquals(2, config.maxAttempts)
        assertEquals(1.seconds, config.initialDelay)
        assertEquals(10.seconds, config.maxDelay)
    }
}
