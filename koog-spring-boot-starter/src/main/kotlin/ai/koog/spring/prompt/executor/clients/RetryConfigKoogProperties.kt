package ai.koog.spring

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
)
