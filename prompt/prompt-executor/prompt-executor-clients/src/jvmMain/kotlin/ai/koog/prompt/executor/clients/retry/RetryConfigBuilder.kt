package ai.koog.prompt.executor.clients.retry

import ai.koog.agents.annotations.JavaAPI
import ai.koog.utils.time.toKotlinDuration
import kotlin.time.Duration
import java.time.Duration as JavaDuration

/**
 * A builder class for constructing [RetryConfig] instances from Java code.
 *
 * Accepts both [java.time.Duration] and [kotlin.time.Duration] for delay parameters.
 *
 * Usage from Java:
 * ```java
 * RetryConfig config = RetryConfig.builder()
 *     .maxAttempts(5)
 *     .initialDelay(Duration.ofSeconds(2))
 *     .maxDelay(Duration.ofSeconds(60))
 *     .backoffMultiplier(2.0)
 *     .jitterFactor(0.15)
 *     .build();
 * ```
 */
/**
 * Creates a new [RetryConfigBuilder] for constructing [RetryConfig] instances.
 */
@JavaAPI
public fun RetryConfig.Companion.builder(): RetryConfigBuilder = RetryConfigBuilder()

/**
 * A builder class for constructing [RetryConfig] instances from Java code.
 *
 * Accepts both [java.time.Duration] and [kotlin.time.Duration] for delay parameters.
 *
 * Usage from Java:
 * ```java
 * RetryConfig config = RetryConfig.builder()
 *     .maxAttempts(5)
 *     .initialDelay(Duration.ofSeconds(2))
 *     .maxDelay(Duration.ofSeconds(60))
 *     .backoffMultiplier(2.0)
 *     .jitterFactor(0.15)
 *     .build();
 * ```
 */
/**
 * Creates a new [RetryConfigBuilder] for constructing [RetryConfig] instances.
 */
@JavaAPI
public class RetryConfigBuilder {
    private var maxAttempts: Int? = null
    private var initialDelay: Duration? = null
    private var maxDelay: Duration? = null
    private var backoffMultiplier: Double? = null
    private var jitterFactor: Double? = null
    private var retryablePatterns: List<RetryablePattern>? = null
    private var retryAfterExtractor: RetryAfterExtractor? = null
    private var retryAfterExtractorSet: Boolean = false

    /**
     * Sets the maximum number of attempts (including initial).
     *
     * @param maxAttempts the maximum number of attempts.
     * @return the updated builder instance.
     */
    public fun maxAttempts(maxAttempts: Int): RetryConfigBuilder = apply { this.maxAttempts = maxAttempts }

    /**
     * Sets the initial delay before the first retry attempt using [java.time.Duration].
     *
     * @param initialDelay the initial delay duration.
     * @return the updated builder instance.
     */
    public fun initialDelay(initialDelay: JavaDuration): RetryConfigBuilder = apply {
        this.initialDelay = initialDelay.toKotlinDuration()
    }

    /**
     * Sets the initial delay before the first retry attempt using [kotlin.time.Duration].
     *
     * @param initialDelay the initial delay duration.
     * @return the updated builder instance.
     */
    public fun initialDelay(initialDelay: Duration): RetryConfigBuilder = apply { this.initialDelay = initialDelay }

    /**
     * Sets the maximum delay allowed between retry attempts using [java.time.Duration].
     *
     * @param maxDelay the maximum delay duration.
     * @return the updated builder instance.
     */
    public fun maxDelay(maxDelay: JavaDuration): RetryConfigBuilder = apply {
        this.maxDelay = maxDelay.toKotlinDuration()
    }

    /**
     * Sets the maximum delay allowed between retry attempts using [kotlin.time.Duration].
     *
     * @param maxDelay the maximum delay duration.
     * @return the updated builder instance.
     */
    public fun maxDelay(maxDelay: Duration): RetryConfigBuilder = apply { this.maxDelay = maxDelay }

    /**
     * Sets the backoff multiplier for subsequent retry attempts.
     *
     * @param backoffMultiplier the multiplier to apply to the delay for each subsequent retry.
     * @return the updated builder instance.
     */
    public fun backoffMultiplier(backoffMultiplier: Double): RetryConfigBuilder = apply {
        this.backoffMultiplier = backoffMultiplier
    }

    /**
     * Sets the jitter factor to introduce randomness in delay calculations.
     *
     * @param jitterFactor the factor for randomness in delays (0.0 to 1.0).
     * @return the updated builder instance.
     */
    public fun jitterFactor(jitterFactor: Double): RetryConfigBuilder = apply { this.jitterFactor = jitterFactor }

    /**
     * Sets the retryable patterns for identifying retryable errors.
     *
     * @param retryablePatterns the list of patterns.
     * @return the updated builder instance.
     */
    public fun retryablePatterns(retryablePatterns: List<RetryablePattern>): RetryConfigBuilder = apply {
        this.retryablePatterns = retryablePatterns
    }

    /**
     * Sets the retry-after extractor for extracting retry-after hints from error messages.
     *
     * @param retryAfterExtractor the extractor, or null to disable.
     * @return the updated builder instance.
     */
    public fun retryAfterExtractor(retryAfterExtractor: RetryAfterExtractor?): RetryConfigBuilder = apply {
        this.retryAfterExtractor = retryAfterExtractor
        this.retryAfterExtractorSet = true
    }

    /**
     * Builds a new [RetryConfig] instance with the configured values.
     * Any unset values will use [RetryConfig] defaults.
     *
     * @return a new [RetryConfig] instance.
     */
    public fun build(): RetryConfig {
        val defaults = RetryConfig()
        return RetryConfig(
            maxAttempts = maxAttempts ?: defaults.maxAttempts,
            initialDelay = initialDelay ?: defaults.initialDelay,
            maxDelay = maxDelay ?: defaults.maxDelay,
            backoffMultiplier = backoffMultiplier ?: defaults.backoffMultiplier,
            jitterFactor = jitterFactor ?: defaults.jitterFactor,
            retryablePatterns = retryablePatterns ?: defaults.retryablePatterns,
            retryAfterExtractor = if (retryAfterExtractorSet) retryAfterExtractor else defaults.retryAfterExtractor
        )
    }
}
