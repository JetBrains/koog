@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package ai.koog.prompt.cache.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.mockk.coEvery
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory implementation of RedisClient for testing purposes.
 * This can be used in tests to avoid the need for a real Redis server.
 *
 * Uses mockk to avoid implementing unnecessary methods.
 */
class MockRedisClient(
    private val mockConnection: StatefulRedisConnection<String, String>,
    private val mockCommands: RedisCoroutinesCommands<String, String>
) : RedisClient() {
    private data class Data(val value: String, var timestamp: Long, val ttl: Long? = null)

    // Store the data in the client so it's accessible across connections
    private val dataStore = ConcurrentHashMap<String, Data>()

    init {
        // Setup the mock commands to implement our in-memory Redis functionality
        coEvery { mockCommands.get(any()) } answers {
            val key = firstArg<String>()
            val now = System.currentTimeMillis()
            dataStore[key]?.let { data ->
                if (data.ttl != null && now > data.timestamp + data.ttl) {
                    null
                } else {
                    data.timestamp = now
                    data.value
                }
            }
        }

        coEvery { mockCommands.set(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            val now = System.currentTimeMillis()

            // Preserve TTL when updating an existing key
            dataStore[key] = Data(value, now, dataStore[key]?.ttl)
            "OK"
        }

        coEvery { mockCommands.setex(any(), any(), any()) } answers {
            val key = firstArg<String>()
            val ttl = secondArg<Long>()
            val value = thirdArg<String>()
            val now = System.currentTimeMillis()

            dataStore[key] = Data(value, now, ttl * 1000) // Convert seconds to milliseconds
            "OK"
        }
    }

    override fun connect(): StatefulRedisConnection<String, String> {
        return mockConnection
    }

    override fun shutdown() {
        // No-op for mock
    }
}
