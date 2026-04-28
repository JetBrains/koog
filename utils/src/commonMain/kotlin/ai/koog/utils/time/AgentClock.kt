package ai.koog.utils.time

import kotlin.time.Instant

/**
 * Time source used across Koog for message timestamps, event timestamps,
 * and any other "what time is it now" call.
 *
 * Implement this interface (or use [AgentClock.System]) anywhere a clock is required.
 * Being a functional interface, simple test doubles can be written as lambdas:
 * ```
 * val fixed = AgentClock { Instant.fromEpochSeconds(1_700_000_000) }
 * ```
 */
public fun interface AgentClock {
    /**
     * Returns the current instant as observed by this clock.
     */
    public fun now(): Instant

    public companion object {
        /**
         * Default [AgentClock] implementation backed by [kotlin.time.Clock.System].
         */
        public val System: AgentClock = AgentClock { kotlin.time.Clock.System.now() }
    }
}
