package ai.koog.prompt.cache.redis

import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Redis-based implementation of [PromptCache].
 * This implementation stores cache entries in a Redis database.
 *
 * @param client The Redis client to use for connecting to Redis
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
public class RedisPromptCache(
    private val client: RedisClient,
    private val prefix: String,
    private val ttl: Duration,
) : PromptCache {

    /**
     * Companion object for the RedisPromptCache class, functioning as a factory for creating
     * Redis-backed implementations of the PromptCache interface.
     *
     * This factory is identified by the name "redis" and is responsible for parsing a configuration
     * string to initialize a RedisPromptCache instance with the specified properties such as
     * Redis URI, cache key prefix, and cache expiration time-to-live (TTL).
     *
     * The companion object extends the PromptCache.Factory.Named class to associate the factory
     * with the specific name "redis" and implements the create method to generate instances
     * of RedisPromptCache.
     *
     * Constants:
     * - DEFAULT_URI: Default URI for connecting to Redis, used if no URI is provided in the configuration.
     * - CACHE_KEY_PREFIX: Default prefix for cache keys, used if no prefix is specified in the configuration.
     *
     * Properties:
     * - logger: Logger instance for logging operations related to the RedisPromptCache.
     *
     * Methods:
     * - create(config: String): Parses the provided configuration string to extract Redis URI, cache key prefix,
     *   and TTL, and returns a new RedisPromptCache instance initialized with these properties. Throws an
     *   exception if the cache type is not "redis".
     */
    public companion object : PromptCache.Factory.Named("redis") {
        private val logger = KotlinLogging.logger { }

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            allowStructuredMapKeys = true
        }

        private val prettyJson = Json {
            ignoreUnknownKeys = true
            allowStructuredMapKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private const val DEFAULT_URI = "redis://localhost:6379"
        private const val CACHE_KEY_PREFIX = "code-prompt-cache:"

        override fun create(config: String): PromptCache {
            val parts = elements(config)
            require(parts[0] == "redis") { "Invalid cache type: ${parts[0]}. Expected 'redis'." }
            val uri = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: DEFAULT_URI
            val client = RedisClient.create(uri)
            val prefix = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: CACHE_KEY_PREFIX
            val ttlInSeconds = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.toLongOrNull()?.seconds ?: 1.days
            return RedisPromptCache(client, prefix, ttlInSeconds)
        }
    }

    private val connection: StatefulRedisConnection<String, String> by lazy {
        client.connect()
    }

    private val commands: RedisCoroutinesCommands<String, String> by lazy {
        connection.coroutines()
    }

    @Serializable
    private data class CachedElement(val response: List<Message.Response>, val request: PromptCache.Request)

    override suspend fun get(request: PromptCache.Request): List<Message.Response>? {
        return getOrNull(request)
    }

    override suspend fun put(request: PromptCache.Request, response: List<Message.Response>) {
        try {
            val key = prefix + request.asCacheKey
            val value = prettyJson.encodeToString(CachedElement(response, request))

            // Store the value
            commands.setex(key, seconds = ttl.inWholeSeconds, value)

            logger.info { "Set key '$key' to Redis cache" }
        } catch (e: Exception) {
            throw RedisCacheException("Error storing in Redis cache", e)
        }
    }

    private suspend fun getOrNull(request: PromptCache.Request): List<Message.Response>? {
        try {
            val key = prefix + request.asCacheKey
            val value = commands.get(key) ?: run {
                logger.info { "Get key '$key' from Redis cache miss" }
                return null
            }
            logger.info { "Get key '$key' from Redis cache hit" }

            // Update access time by setting the key with the same value but updated TTL
            commands.set(key, value)

            return defaultJson.decodeFromString<CachedElement>(value).response
        } catch (e: Exception) {
            // Log the error but don't fail the operation
            logger.error { "Error retrieving from Redis cache: ${e.message}" }
            return null
        }
    }

    /**
     * Closes the Redis connection.
     * This method should be called when the cache is no longer needed.
     */
    public fun close() {
        connection.close()
        client.shutdown()
    }
}

/**
 * Exception thrown when there is an error with Redis cache operations.
 */
public class RedisCacheException(message: String, cause: Throwable? = null) : Exception(message, cause)
