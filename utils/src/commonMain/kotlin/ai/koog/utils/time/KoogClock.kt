package ai.koog.utils.time

import kotlin.time.Instant

/**
 * Time source used across Koog for message timestamps, event timestamps,
 * and any other "what time is it now" call.
 *
 * Implement this interface (or use [KoogClock.System]) anywhere a clock is required.
 * Being a functional interface, simple test doubles can be written as lambdas:
 * ```
 * val fixed = KoogClock { Instant.fromEpochSeconds(1_700_000_000) }
 * ```
 */
public fun interface KoogClock {
    /**
     * Returns the current instant as observed by this clock.
     */
    public fun now(): Instant

    public companion object {
        /**
         * Default [KoogClock] implementation backed by [kotlin.time.Clock.System].
         */
        public val System: KoogClock = KoogClock { kotlin.time.Clock.System.now() }
    }
}
