package ai.grazie.code.agents.core.agent.entity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AgentStorageKey<T : Any>(val name: String)

inline fun <reified T : Any> createStorageKey(name: String) = AgentStorageKey<T>(name)

/**
 * Concurrent-safe key-value storage for an agent
 */
class AgentStorage internal constructor() {
    private val mutex = Mutex()
    private val storage = mutableMapOf<AgentStorageKey<*>, Any>()

    suspend fun <T : Any> set(key: AgentStorageKey<T>, value: T) = mutex.withLock {
        storage[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> get(key: AgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> remove(key: AgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    suspend fun toMap(): Map<AgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    suspend fun putAll(map: Map<AgentStorageKey<*>, Any>) = mutex.withLock {
        storage.putAll(map)
    }

    suspend fun clear() = mutex.withLock {
        storage.clear()
    }
}
