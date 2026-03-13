package ai.koog.spring

import ai.koog.agents.annotations.JavaAPI
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Represents configuration properties for retry mechanisms associated with clients.
 *
 * This class is used to define retry behavior, including specifications such as the number of retry
 * attempts, delay between attempts, and mechanisms to control backoff strategy and randomness in delays.
 *
 * @property enabled Indicates whether retries are enabled.
 * @property maxAttempts Specifies the maximum number of retry attempts.
 * @property initialDelay Specifies the initial delay before the first retry attempt, in seconds.
 * @property maxDelay Specifies the maximum delay allowed between retry attempts, in seconds.
 * @property backoffMultiplier Defines the multiplier to apply to the delay for each subsequent retry attempt.
 * @property jitterFactor Specifies the factor to introduce randomness in the delay calculations to avoid symmetric loads.
 */
public class RetryConfigKoogProperties(
    public val enabled: Boolean = false,
    public val maxAttempts: Int? = null,
    @param:DurationUnit(ChronoUnit.SECONDS)
    public val initialDelay: Duration? = null,
    @param:DurationUnit(ChronoUnit.SECONDS)
    public val maxDelay: Duration? = null,
    public val backoffMultiplier: Double? = null,
    public val jitterFactor: Double? = null
) {
    /**
     * Companion object for [RetryConfigKoogProperties], providing a builder for constructing instances.
     */
    public companion object {
        /**
         * Creates a new [RetryConfigKoogPropertiesBuilder] for constructing [RetryConfigKoogProperties] instances.
         *
         * @return a new builder instance.
         */
        @JavaAPI
        @JvmStatic
        public fun builder(): RetryConfigKoogPropertiesBuilder = RetryConfigKoogPropertiesBuilder()
    }

    /**
     * A builder class for constructing [RetryConfigKoogProperties] instances from Java code.
     */
    @JavaAPI
    public class RetryConfigKoogPropertiesBuilder {
        private var enabled: Boolean = false
        private var maxAttempts: Int? = null
        private var initialDelay: Duration? = null
        private var maxDelay: Duration? = null
        private var backoffMultiplier: Double? = null
        private var jitterFactor: Double? = null

        /**
         * Sets whether retries are enabled.
         *
         * @param enabled true to enable retries, false to disable.
         * @return the updated builder instance.
         */
        public fun enabled(enabled: Boolean): RetryConfigKoogPropertiesBuilder = apply { this.enabled = enabled }

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxAttempts the maximum number of retry attempts.
         * @return the updated builder instance.
         */
        public fun maxAttempts(maxAttempts: Int): RetryConfigKoogPropertiesBuilder = apply { this.maxAttempts = maxAttempts }

        /**
         * Sets the initial delay before the first retry attempt.
         *
         * @param initialDelay the initial delay duration.
         * @return the updated builder instance.
         */
        public fun initialDelay(initialDelay: Duration): RetryConfigKoogPropertiesBuilder = apply { this.initialDelay = initialDelay }

        /**
         * Sets the maximum delay allowed between retry attempts.
         *
         * @param maxDelay the maximum delay duration.
         * @return the updated builder instance.
         */
        public fun maxDelay(maxDelay: Duration): RetryConfigKoogPropertiesBuilder = apply { this.maxDelay = maxDelay }

        /**
         * Sets the backoff multiplier for subsequent retry attempts.
         *
         * @param backoffMultiplier the multiplier to apply to the delay for each subsequent retry.
         * @return the updated builder instance.
         */
        public fun backoffMultiplier(backoffMultiplier: Double): RetryConfigKoogPropertiesBuilder = apply { this.backoffMultiplier = backoffMultiplier }

        /**
         * Sets the jitter factor to introduce randomness in delay calculations.
         *
         * @param jitterFactor the factor for randomness in delays.
         * @return the updated builder instance.
         */
        public fun jitterFactor(jitterFactor: Double): RetryConfigKoogPropertiesBuilder = apply { this.jitterFactor = jitterFactor }

        /**
         * Builds a new [RetryConfigKoogProperties] instance with the configured values.
         *
         * @return a new [RetryConfigKoogProperties] instance.
         */
        public fun build(): RetryConfigKoogProperties = RetryConfigKoogProperties(
            enabled = enabled,
            maxAttempts = maxAttempts,
            initialDelay = initialDelay,
            maxDelay = maxDelay,
            backoffMultiplier = backoffMultiplier,
            jitterFactor = jitterFactor
        )
    }
}
