package ai.jetbrains.code.prompt.cache.redis

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.json.JSON
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.create
import ai.jetbrains.code.prompt.cache.model.CodePromptCache
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Redis-based implementation of [CodePromptCache].
 * This implementation stores cache entries in a Redis database.
 *
 * @param client The Redis client to use for connecting to Redis
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisCodePromptCache(
    private val client: RedisClient,
    private val prefix: String,
    private val ttl: Duration,
) : CodePromptCache {

    companion object : CodePromptCache.Factory.Named("redis") {
        private val logger = LoggerFactory.create(RedisCodePromptCache::class)

        private const val DEFAULT_URI = "redis://localhost:6379"
        private const val CACHE_KEY_PREFIX = "code-prompt-cache:"

        override fun create(config: String): CodePromptCache {
            val parts = elements(config)
            require(parts[0] == "redis") { "Invalid cache type: ${parts[0]}. Expected 'redis'." }
            val uri = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: DEFAULT_URI
            val client = RedisClient.create(uri)
            val prefix = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: CACHE_KEY_PREFIX
            val ttlInSeconds = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.toLongOrNull()?.seconds ?: 1.days
            return RedisCodePromptCache(client, prefix, ttlInSeconds)
        }
    }

    private val connection: StatefulRedisConnection<String, String> by lazy {
        client.connect()
    }

    private val commands: RedisCoroutinesCommands<String, String> by lazy {
        connection.coroutines()
    }

    @Serializable
    private data class CachedElement(val response: List<Message.Response>, val request: Request)

    @Serializable
    private data class Request(val prompt: Prompt, val tools: List<JsonObject> = emptyList()) {
        val id: String
            get() = JSON.Pretty.string(this).hashCode().absoluteValue.toString(36)
    }

    /**
     * Generate a cache key for a prompt with tools.
     */
    private fun cacheKey(request: Request): String {
        return this@RedisCodePromptCache.prefix + request.id
    }

    override suspend fun get(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response>? {
        val request = Request(prompt, tools.map { toolToJsonObject(it) })
        return getOrNull(request)
    }

    override suspend fun put(prompt: Prompt, tools: List<ToolDescriptor>, response: List<Message.Response>) {
        val request = Request(prompt, tools.map { toolToJsonObject(it) })
        put(request, response)
    }

    /**
     * Convert a ToolDescriptor to a JsonObject representation.
     * This is a simplified version that just captures the tool name and description for caching purposes.
     */
    private fun toolToJsonObject(tool: ToolDescriptor): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(tool.name))
        put("description", JsonPrimitive(tool.description))
    }

    private suspend fun getOrNull(request: Request): List<Message.Response>? {
        try {
            val key = cacheKey(request)
            val value = commands.get(key) ?: run {
                logger.info { "Get key '${key}' from Redis cache miss" }
                return null
            }
            logger.info { "Get key '${key}' from Redis cache hit" }

            // Update access time by setting the key with the same value but updated TTL
            commands.set(key, value)

            return JSON.Default.parse<CachedElement>(value).response
        } catch (e: Exception) {
            // Log the error but don't fail the operation
            println("Error retrieving from Redis cache: ${e.message}")
            return null
        }
    }

    private suspend fun put(request: Request, response: List<Message.Response>) {
        try {
            val key = cacheKey(request)
            val value = JSON.Pretty.string(CachedElement(response, request))

            // Store the value
            commands.setex(key, seconds = ttl.inWholeSeconds, value)

            logger.info { "Set key '${key}' to Redis cache" }
        } catch (e: Exception) {
            throw RedisCacheException("Error storing in Redis cache", e)
        }
    }

    /**
     * Closes the Redis connection.
     * This method should be called when the cache is no longer needed.
     */
    fun close() {
        connection.close()
        client.shutdown()
    }
}

/**
 * Exception thrown when there is an error with Redis cache operations.
 */
class RedisCacheException(message: String, cause: Throwable? = null) : Exception(message, cause)