package ai.grazie.code.agents.core.agent.entity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class LocalAgentStorageKey<T : Any>(val name: String)

public inline fun <reified T : Any> createStorageKey(name: String): LocalAgentStorageKey<T> = LocalAgentStorageKey<T>(name)

/**
 * Concurrent-safe key-value storage for an agent
 */
public class LocalAgentStorage internal constructor() {
    private val mutex = Mutex()
    private val storage = mutableMapOf<LocalAgentStorageKey<*>, Any>()

    public suspend fun <T : Any> set(key: LocalAgentStorageKey<T>, value: T): Unit = mutex.withLock {
        storage[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    public suspend fun <T : Any> get(key: LocalAgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    @Suppress("UNCHECKED_CAST")
    public suspend fun <T : Any> remove(key: LocalAgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    public suspend fun toMap(): Map<LocalAgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    public suspend fun putAll(map: Map<LocalAgentStorageKey<*>, Any>): Unit = mutex.withLock {
        storage.putAll(map)
    }

    public suspend fun clear(): Unit = mutex.withLock {
        storage.clear()
    }
}
