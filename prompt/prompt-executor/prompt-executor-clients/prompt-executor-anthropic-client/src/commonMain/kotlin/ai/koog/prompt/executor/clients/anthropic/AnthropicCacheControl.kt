package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.message.CacheControl
import kotlinx.serialization.Serializable

/**
 * Represents caching strategies specific to Anthropic's LLM provider.
 */
@Serializable
public sealed interface AnthropicCacheControl : CacheControl {
    /** Cache with the default TTL (no explicit TTL sent to Anthropic, caches for 5 minutes by default). */
    @Serializable
    public data object Default : AnthropicCacheControl

    /** Cache for 1 hour. */
    @Serializable
    public data object OneHour : AnthropicCacheControl
}
