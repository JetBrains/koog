package ai.koog.spring

import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Configuration properties for the Koog library used for LLM clients retry configuration.
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
