package ai.koog.prompt.message

import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * Creates a RequestMetaInfo instance with the current system time.
 * This is a convenience method for Java interoperability that doesn't require passing a Clock instance.
 *
 * @return A new RequestMetaInfo instance with the current system timestamp.
 */
@JvmName("now")
public fun RequestMetaInfo.Companion.now(): RequestMetaInfo = RequestMetaInfo(Clock.System.now())

/**
 * Creates a ResponseMetaInfo instance with the current system time.
 * This is a convenience method for Java interoperability that doesn't require passing a Clock instance.
 *
 * @param totalTokensCount The total number of tokens involved in the response, including both input and output tokens.
 * @param inputTokensCount The number of tokens used in the input.
 * @param outputTokensCount The number of tokens generated in the output.
 * @param additionalInfo Deprecated: use [metadata] instead. Additional metadata as a map of string keys to string values.
 * @param metadata Additional metadata as a JSON object.
 * @return A new ResponseMetaInfo instance with the current system timestamp.
 */
@JvmOverloads
@JvmName("now")
public fun ResponseMetaInfo.Companion.now(
    totalTokensCount: Int? = null,
    inputTokensCount: Int? = null,
    outputTokensCount: Int? = null,
    additionalInfo: Map<String, String> = emptyMap(),
    metadata: JsonObject? = null,
): ResponseMetaInfo = ResponseMetaInfo(
    Clock.System.now(),
    totalTokensCount,
    inputTokensCount,
    outputTokensCount,
    additionalInfo,
    metadata
)
