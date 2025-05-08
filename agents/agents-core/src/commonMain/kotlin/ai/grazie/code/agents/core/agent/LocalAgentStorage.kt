package ai.grazie.code.agents.core.agent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LocalAgentStorageKey<T : Any>(val name: String)

inline fun <reified T : Any> createStorageKey(name: String) = LocalAgentStorageKey<T>(name)

/**
 * Concurrent-safe key-value storage for an agent
 */
class LocalAgentStorage internal constructor() {
    private val mutex = Mutex()
    private val storage = mutableMapOf<LocalAgentStorageKey<*>, Any>()

    suspend fun <T : Any> set(key: LocalAgentStorageKey<T>, value: T) = mutex.withLock {
        storage[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> get(key: LocalAgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> remove(key: LocalAgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    suspend fun toMap(): Map<LocalAgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    suspend fun putAll(map: Map<LocalAgentStorageKey<*>, Any>) = mutex.withLock {
        storage.putAll(map)
    }

    suspend fun clear() = mutex.withLock {
        storage.clear()
    }
}
