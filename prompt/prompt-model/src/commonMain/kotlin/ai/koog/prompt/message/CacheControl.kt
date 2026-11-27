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
 * Implementations that are stored on a serialized [Message] or used as part of a prompt cache key
 * must be `@Serializable`.
 */
@Serializable(with = CacheControlSerializer::class)
public interface CacheControl
