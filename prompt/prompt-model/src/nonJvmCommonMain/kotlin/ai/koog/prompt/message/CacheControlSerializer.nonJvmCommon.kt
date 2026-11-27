package ai.koog.prompt.message

import kotlinx.serialization.KSerializer

/**
 * Non-JVM targets cannot resolve a class from a stored name.
 * [CacheControlSerializer] falls back to serializers already seen in this process.
 */
internal actual fun loadCacheControlSerializer(qualifiedName: String): KSerializer<CacheControl>? = null
