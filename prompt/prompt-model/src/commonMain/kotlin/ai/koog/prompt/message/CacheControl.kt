package ai.koog.prompt.message

import kotlinx.serialization.Serializable

/**
 * Requires this [CacheControl] to be of the specified type [T], or throws an [IllegalStateException].
 */
public inline fun <reified T : CacheControl> CacheControl.require(): T =
    this as? T ?: error("Expected ${T::class.simpleName}, got: $this")

/**
 * Cache control configuration for prompt caching.
 * Indicates that the LLM provider should cache content up to and including the element this is attached to.
 *
 * Each LLM provider defines its own supported cache control options as nested sealed interfaces.
 */
@Serializable
public sealed interface CacheControl {

    /**
     * Bedrock-specific cache control options.
     * Bedrock supports only two TTL values: 5 minutes and 1 hour.
     */
    @Serializable
    public sealed interface Bedrock : CacheControl {
        /** Cache with the default TTL (no explicit TTL sent to Bedrock). */
        @Serializable
        public data object Default : Bedrock

        /** Cache for 5 minutes. */
        @Serializable
        public data object FiveMinutes : Bedrock

        /** Cache for 1 hour. */
        @Serializable
        public data object OneHour : Bedrock
    }
}
