package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.message.CacheControl
import kotlinx.serialization.Serializable

/**
 * Bedrock-specific cache control options.
 * Bedrock supports only two TTL values: 5 minutes and 1 hour.
 */
@Serializable
public sealed interface BedrockCacheControl : CacheControl {
    /** Cache with the default TTL (no explicit TTL sent to Bedrock). */
    @Serializable
    public data object Default : BedrockCacheControl

    /** Cache for 5 minutes. */
    @Serializable
    public data object FiveMinutes : BedrockCacheControl

    /** Cache for 1 hour. */
    @Serializable
    public data object OneHour : BedrockCacheControl
}
