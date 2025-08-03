package ai.koog.agents.core.tools.ratelimit

import kotlin.time.Duration

/**
 * Result of a rate limit check.
 */
public sealed interface RateLimitResult {
    /**
     * The request is allowed under the rate limit.
     */
    public data object Allowed : RateLimitResult

    /**
     * The request is rate limited.
     *
     * @property limit The rate limit that was exceeded
     * @property resetIn Time until the rate limit resets
     */
    public data class Exceeded(
        val limit: Int,
        val resetIn: Duration
    ) : RateLimitResult
}
