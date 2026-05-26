package ai.koog.prompt.message

import ai.koog.agents.annotations.JavaAPI
import ai.koog.utils.time.toKotlinInstant
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.Instant as JavaInstant

/**
 * Builder for creating [ResponseMetaInfo] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * ResponseMetaInfo metaInfo = ResponseMetaInfo.builder()
 *     .timestamp(Instant.now())
 *     .totalTokensCount(100)
 *     .inputTokensCount(40)
 *     .outputTokensCount(60)
 *     .build();
 * ```
 */
@JavaAPI
public class ResponseMetaInfoBuilder {
    private var timestamp: Instant? = null
    private var totalTokensCount: Int? = null
    private var inputTokensCount: Int? = null
    private var outputTokensCount: Int? = null
    private var metadata: JsonObject? = null

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): ResponseMetaInfoBuilder = apply {
        this.timestamp = timestamp.toKotlinInstant()
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): ResponseMetaInfoBuilder = apply {
        this.timestamp = timestamp
    }

    /**
     * Sets the total token count.
     */
    public fun totalTokensCount(count: Int): ResponseMetaInfoBuilder = apply {
        this.totalTokensCount = count
    }

    /**
     * Sets the input token count.
     */
    public fun inputTokensCount(count: Int): ResponseMetaInfoBuilder = apply {
        this.inputTokensCount = count
    }

    /**
     * Sets the output token count.
     */
    public fun outputTokensCount(count: Int): ResponseMetaInfoBuilder = apply {
        this.outputTokensCount = count
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): ResponseMetaInfoBuilder = apply {
        this.metadata = metadata
    }

    /**
     * Builds a new [ResponseMetaInfo] instance.
     * If no timestamp is set, the current system time is used.
     */
    public fun build(): ResponseMetaInfo = ResponseMetaInfo(
        timestamp = timestamp ?: Clock.System.now(),
        totalTokensCount = totalTokensCount,
        inputTokensCount = inputTokensCount,
        outputTokensCount = outputTokensCount,
        metadata = metadata
    )
}

/**
 * Creates a new [ResponseMetaInfoBuilder].
 */
@JavaAPI
public fun ResponseMetaInfo.Companion.builder(): ResponseMetaInfoBuilder = ResponseMetaInfoBuilder()

/**
 * Creates a [ResponseMetaInfo] with a [java.time.Instant] timestamp.
 */
@JavaAPI
public fun ResponseMetaInfo.Companion.fromJavaInstant(
    timestamp: JavaInstant,
    totalTokensCount: Int? = null,
    inputTokensCount: Int? = null,
    outputTokensCount: Int? = null,
    metadata: JsonObject? = null
): ResponseMetaInfo = ResponseMetaInfo(
    timestamp = timestamp.toKotlinInstant(),
    totalTokensCount = totalTokensCount,
    inputTokensCount = inputTokensCount,
    outputTokensCount = outputTokensCount,
    metadata = metadata
)
