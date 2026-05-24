package ai.koog.prompt.message

import ai.koog.agents.annotations.JavaAPI
import ai.koog.utils.time.toKotlinInstant
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.Instant as JavaInstant

/**
 * Builder for creating [RequestMetaInfo] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * RequestMetaInfo metaInfo = RequestMetaInfo.builder()
 *     .timestamp(Instant.now())
 *     .metadata(jsonObject)
 *     .build();
 * ```
 */
@JavaAPI
public class RequestMetaInfoBuilder {
    private var timestamp: Instant? = null
    private var metadata: JsonObject? = null

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): RequestMetaInfoBuilder = apply {
        this.timestamp = timestamp.toKotlinInstant()
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): RequestMetaInfoBuilder = apply {
        this.timestamp = timestamp
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): RequestMetaInfoBuilder = apply {
        this.metadata = metadata
    }

    /**
     * Builds a new [RequestMetaInfo] instance.
     * If no timestamp is set, the current system time is used.
     */
    public fun build(): RequestMetaInfo = RequestMetaInfo(
        timestamp = timestamp ?: Clock.System.now(),
        metadata = metadata
    )
}

/**
 * Creates a new [RequestMetaInfoBuilder].
 */
@JavaAPI
public fun RequestMetaInfo.Companion.builder(): RequestMetaInfoBuilder = RequestMetaInfoBuilder()

/**
 * Creates a [RequestMetaInfo] with a [java.time.Instant] timestamp.
 */
@JavaAPI
public fun RequestMetaInfo.Companion.fromJavaInstant(
    timestamp: JavaInstant,
    metadata: JsonObject? = null
): RequestMetaInfo = RequestMetaInfo(
    timestamp = timestamp.toKotlinInstant(),
    metadata = metadata
)
