package ai.koog.agents.features.redis.providers

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.toList
import java.io.Closeable

/**
 * A JVM-specific implementation of [RedisPersistencyStorageProvider] for managing agent checkpoints
 * in Redis using Lettuce with Kotlin coroutine support.
 *
 * This class utilizes Lettuce's coroutine API for non-blocking Redis operations.
 * It manages connections and provides proper resource cleanup through the [Closeable] interface.
 *
 * ## Architecture Notes:
 * - Uses a single Redis connection per provider instance
 * - Operations are not transactional due to known issues with Lettuce coroutines transactions (issue #2371)
 * - The cleanup logic in getCheckpoints() handles consistency for orphaned entries
 * - For high-concurrency production use, use the PooledJVMRedisPersistencyStorageProvider variant
 *
 * ## Production Considerations:
 * - Each provider instance maintains one Redis connection
 * - Multiple agent instances should use separate provider instances for isolation
 * - Consider Redis clustering for high availability in production environments
 *
 * @constructor Initializes the [JVMRedisPersistencyStorageProvider] with Redis connection details.
 * @param persistenceId Unique identifier for this agent's persistence data
 * @param redisUri The Redis URI for connection configuration
 * @param keyPrefix Optional prefix for all Redis keys (default: "agent:checkpoint")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
public open class JVMRedisPersistencyStorageProvider(
    persistenceId: String,
    private val redisUri: RedisURI,
    keyPrefix: String = "agent:checkpoint",
    ttlSeconds: Long? = null
) : RedisPersistencyStorageProvider(
    persistenceId = persistenceId,
    keyPrefix = keyPrefix,
    ttlSeconds = ttlSeconds
), Closeable {
    
    protected val redisClient: RedisClient by lazy {
        redisClientField ?: RedisClient.create(redisUri)
    }
    
    private val connection: StatefulRedisConnection<String, String> by lazy {
        redisClient.connect()
    }
    
    private val commands: RedisCoroutinesCommands<String, String> by lazy {
        connection.coroutines()
    }
    
    private var redisClientField: RedisClient? = null
    
    /**
     * Alternative constructor using RedisClient directly
     */
    public constructor(
        persistenceId: String,
        redisClient: RedisClient,
        keyPrefix: String = "agent:checkpoint",
        ttlSeconds: Long? = null
    ) : this(
        persistenceId = persistenceId,
        redisUri = RedisURI.create("redis://localhost"), // Dummy URI, not used when client is provided
        keyPrefix = keyPrefix,
        ttlSeconds = ttlSeconds
    ) {
        // Set the client field before lazy properties are accessed
        this.redisClientField = redisClient
    }
    
    // Redis command implementations
    // Note: Lettuce coroutine commands return nullable types, but we convert nulls to sensible defaults
    // to match Redis semantics (e.g., operations on non-existent keys return 0, not null)
    // 
    // Thread Safety: Lettuce connections are thread-safe and can be used concurrently from multiple coroutines
    
    override suspend fun zrange(key: String, start: Long, stop: Long): List<String> =
        executeCommand { it.zrange(key, start, stop).toList() }
    
    protected open suspend fun <T> executeCommand(operation: suspend (RedisCoroutinesCommands<String, String>) -> T): T =
        operation(commands)
    
    override suspend fun get(key: String): String? =
        executeCommand { it.get(key) }
    
    override suspend fun set(key: String, value: String): String? =
        executeCommand { it.set(key, value) }
    
    override suspend fun zadd(key: String, score: Double, member: String): Long =
        executeCommand { it.zadd(key, score, member) ?: 0L }
    
    override suspend fun expire(key: String, seconds: Long): Boolean =
        executeCommand { it.expire(key, seconds) ?: false }
    
    override suspend fun keys(pattern: String): List<String> =
        executeCommand { it.keys(pattern).toList() }
    
    override suspend fun zrem(key: String, member: String): Long =
        executeCommand { it.zrem(key, member) ?: 0L }
    
    override suspend fun del(vararg keys: String): Long =
        executeCommand { it.del(*keys) ?: 0L }
    
    override suspend fun ttl(key: String): Long =
        executeCommand { it.ttl(key) ?: -2L }
    
    /**
     * Closes the Redis connection. Should be called when the provider is no longer needed.
     * This implementation satisfies the [Closeable] interface for proper resource management.
     */
    override fun close() {
        runCatching {
            connection.close()
        }
        runCatching {
            redisClient.shutdown()
        }
    }
}