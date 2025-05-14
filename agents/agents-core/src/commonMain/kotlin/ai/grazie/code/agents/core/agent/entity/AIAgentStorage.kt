package ai.grazie.code.agents.core.agent.entity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AIAgentStorageKey<T : Any>(val name: String)

inline fun <reified T : Any> createStorageKey(name: String) = AIAgentStorageKey<T>(name)

/**
 * Concurrent-safe key-value storage for an agent
 */
class AIAgentStorage internal constructor() {
    private val mutex = Mutex()
    private val storage = mutableMapOf<AIAgentStorageKey<*>, Any>()

    suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T) = mutex.withLock {
        storage[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    suspend fun toMap(): Map<AIAgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>) = mutex.withLock {
        storage.putAll(map)
    }

    suspend fun clear() = mutex.withLock {
        storage.clear()
    }
}
