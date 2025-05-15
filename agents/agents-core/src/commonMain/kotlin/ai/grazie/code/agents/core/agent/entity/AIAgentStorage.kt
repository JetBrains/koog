package ai.grazie.code.agents.core.agent.entity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class AIAgentStorageKey<T : Any>(val name: String)

public inline fun <reified T : Any> createStorageKey(name: String): AIAgentStorageKey<T> = AIAgentStorageKey<T>(name)

/**
 * Concurrent-safe key-value storage for an agent
 */
public class AIAgentStorage internal constructor() {
    private val mutex = Mutex()
    private val storage = mutableMapOf<AIAgentStorageKey<*>, Any>()

    public suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T): Unit = mutex.withLock {
        storage[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    public suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    @Suppress("UNCHECKED_CAST")
    public suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    public suspend fun toMap(): Map<AIAgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    public suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>): Unit = mutex.withLock {
        storage.putAll(map)
    }

    public suspend fun clear(): Unit = mutex.withLock {
        storage.clear()
    }
}
