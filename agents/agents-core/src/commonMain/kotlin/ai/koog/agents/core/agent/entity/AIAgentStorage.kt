package ai.koog.agents.core.agent.entity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class AIAgentStorageKey<T : Any>(val name: String)

public inline fun <reified T : Any> createStorageKey(name: String): AIAgentStorageKey<T> = AIAgentStorageKey<T>(name)

/**
 * Concurrent-safe key-value storage for an agent.
 * You can create typed keys for your data using the [createStorageKey] function and
 * set and retrieve data using it by calling [set] and [get].
 *
 */
public class AIAgentStorage internal constructor() {
    private val mutex = Mutex()
    private val storage = mutableMapOf<AIAgentStorageKey<*>, Any>()

    /**
     * Sets the value associated with the given key in the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @param value The value to be associated with the key.
     */
    public suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T): Unit = mutex.withLock {
        storage[key] = value
    }

    /**
     * Retrieves the value associated with the given key from the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, cast to type [T], or null if the key does not exist.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    /**
     * Removes the value associated with the given key from the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, cast to type [T], or null if the key does not exist.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    /**
     * Converts the storage to a map representation.
     *
     * @return A map containing all key-value pairs currently stored in the system, where keys are of type [AIAgentStorageKey]
     * and values are of type [Any].
     */
    public suspend fun toMap(): Map<AIAgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    /**
     * Adds all key-value pairs from the given map to the storage.
     *
     * @param map A map containing keys of type [AIAgentStorageKey] and their associated values of type [Any].
     * The keys and values in the provided map will be added to the storage.
     */
    public suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>): Unit = mutex.withLock {
        storage.putAll(map)
    }

    /**
     * Clears all data from the storage.
     */
    public suspend fun clear(): Unit = mutex.withLock {
        storage.clear()
    }
}
