package ai.koog.utils.time

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration
import java.time.Duration as JavaDuration
import java.time.Instant as JavaInstant

/**
 * Utility class providing conversions between Java and Kotlin time types.
 */
public object TimeUtils {

    /**
     * Converts a [java.time.Instant] to a [kotlin.time.Instant].
     */
    @JvmStatic
    public fun fromJavaInstant(instant: JavaInstant): Instant {
        return Instant.fromEpochSeconds(instant.epochSecond, instant.nano)
    }

    /**
     * Converts a [kotlin.time.Instant] to a [java.time.Instant].
     */
    @JvmStatic
    public fun toJavaInstant(instant: Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong())
    }

    /**
     * Converts a [java.time.Duration] to a [kotlin.time.Duration].
     */
    @JvmStatic
    public fun fromJavaDuration(duration: JavaDuration): Duration {
        return duration.seconds.toDuration(DurationUnit.SECONDS) +
            duration.nano.toDuration(DurationUnit.NANOSECONDS)
    }

    /**
     * Converts a [kotlin.time.Duration] to a [java.time.Duration].
     */
    @JvmStatic
    public fun toJavaDuration(duration: Duration): JavaDuration {
        return JavaDuration.ofNanos(duration.inWholeNanoseconds)
    }
}

/**
 * Converts this [java.time.Instant] to a [kotlin.time.Instant].
 */
public fun JavaInstant.toKotlinInstant(): Instant = TimeUtils.fromJavaInstant(this)

/**
 * Converts this [kotlin.time.Instant] to a [java.time.Instant].
 */
public fun Instant.toJavaInstant(): JavaInstant = TimeUtils.toJavaInstant(this)

/**
 * Converts this [java.time.Duration] to a [kotlin.time.Duration].
 */
public fun JavaDuration.toKotlinDuration(): Duration = TimeUtils.fromJavaDuration(this)

/**
 * Converts this [kotlin.time.Duration] to a [java.time.Duration].
 */
public fun Duration.toJavaDuration(): JavaDuration = TimeUtils.toJavaDuration(this)
