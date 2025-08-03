package ai.koog.agents.features.redis.providers

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.support.ConnectionPoolSupport
import kotlinx.coroutines.flow.toList
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import java.io.Closeable

/**
 * A connection-pooled Redis-based implementation of [RedisPersistencyStorageProvider] for high-concurrency scenarios.
 *
 * This provider extends the basic [JVMRedisPersistencyStorageProvider] to use a connection pool for better performance
 * under high concurrency. It's recommended for production environments with multiple concurrent agents or
 * high-frequency checkpoint operations.
 *
 * ## When to Use This Provider:
 * - High-concurrency applications with many concurrent checkpoint operations
 * - Production environments with multiple agent instances
 * - Applications that require better resource utilization and connection management
 *
 * ## Pool Configuration:
 * - Default pool size: 8 connections (min: 2, max: 20)
 * - Connection validation on borrow/return
 * - Automatic connection replacement on failure
 * - Configurable idle timeout and eviction policies
 *
 * ## Architecture Notes:
 * - Uses Apache Commons Pool2 for connection pooling
 * - Each operation borrows a connection from the pool and returns it after use
 * - Pool management is handled automatically by Lettuce's ConnectionPoolSupport
 * - Thread-safe and suitable for high-concurrency scenarios
 *
 * @constructor Initializes the pooled provider with Redis connection details and pool configuration.
 * @param persistenceId Unique identifier for this agent's persistence data
 * @param redisUri The Redis URI for connection configuration
 * @param keyPrefix Optional prefix for all Redis keys (default: "agent:checkpoint")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 * @param poolConfig Optional pool configuration (uses sensible defaults if not provided)
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
public class PooledJVMRedisPersistencyStorageProvider(
    persistenceId: String,
    redisUri: RedisURI,
    keyPrefix: String = "agent:checkpoint",
    ttlSeconds: Long? = null,
    private val poolConfig: PoolConfig = PoolConfig.default()
) : JVMRedisPersistencyStorageProvider(
    persistenceId = persistenceId,
    redisUri = redisUri,
    keyPrefix = keyPrefix,
    ttlSeconds = ttlSeconds
), Closeable {
    
    /**
     * Configuration for the Redis connection pool.
     *
     * @property minIdle Minimum number of idle connections to maintain in the pool
     * @property maxIdle Maximum number of idle connections to maintain in the pool
     * @property maxTotal Maximum total number of connections in the pool
     * @property testOnBorrow Whether to validate connections when borrowing from the pool
     * @property testOnReturn Whether to validate connections when returning to the pool
     */
    public data class PoolConfig(
        val minIdle: Int = 2,
        val maxIdle: Int = 8,
        val maxTotal: Int = 20,
        val testOnBorrow: Boolean = true,
        val testOnReturn: Boolean = true
    ) {
        public companion object {
            public fun default(): PoolConfig = PoolConfig()
        }
    }
    
    private val connectionPool: GenericObjectPool<StatefulRedisConnection<String, String>> by lazy {
        val poolConfig = GenericObjectPoolConfig<StatefulRedisConnection<String, String>>().apply {
            minIdle = this@PooledJVMRedisPersistencyStorageProvider.poolConfig.minIdle
            maxIdle = this@PooledJVMRedisPersistencyStorageProvider.poolConfig.maxIdle
            maxTotal = this@PooledJVMRedisPersistencyStorageProvider.poolConfig.maxTotal
            testOnBorrow = this@PooledJVMRedisPersistencyStorageProvider.poolConfig.testOnBorrow
            testOnReturn = this@PooledJVMRedisPersistencyStorageProvider.poolConfig.testOnReturn
        }
        
        ConnectionPoolSupport.createGenericObjectPool(
            { redisClient.connect() },
            poolConfig
        )
    }
    
    
    /**
     * Overrides the command execution to use pooled connections.
     * Each operation borrows a connection from the pool and returns it after use.
     */
    override suspend fun <T> executeCommand(operation: suspend (RedisCoroutinesCommands<String, String>) -> T): T {
        val connection = connectionPool.borrowObject()
        try {
            return operation(connection.coroutines())
        } finally {
            connectionPool.returnObject(connection)
        }
    }
    
    /**
     * Closes the connection pool and Redis client. Should be called when the provider is no longer needed.
     * This will close all connections in the pool and shut down the client.
     */
    override fun close() {
        runCatching {
            connectionPool.close()
        }
        super.close()
    }
    
    /**
     * Returns pool statistics for monitoring and debugging.
     * Useful for understanding pool utilization and performance.
     */
    public fun getPoolStats(): PoolStats = PoolStats(
        numActive = connectionPool.numActive,
        numIdle = connectionPool.numIdle,
        maxTotal = connectionPool.maxTotal,
        maxIdle = connectionPool.maxIdle,
        minIdle = connectionPool.minIdle
    )
    
    /**
     * Pool statistics for monitoring connection pool health and utilization.
     */
    public data class PoolStats(
        val numActive: Int,
        val numIdle: Int,
        val maxTotal: Int,
        val maxIdle: Int,
        val minIdle: Int
    ) {
        /**
         * Returns the percentage of pool utilization.
         */
        public val utilizationPercent: Double = (numActive.toDouble() / maxTotal) * 100
        
        /**
         * Returns true if the pool is under high utilization (>80%).
         */
        public val isHighUtilization: Boolean = utilizationPercent > 80.0
    }
}