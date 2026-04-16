package ai.koog.agents.core.agent.entity

/**
 * API for [AIAgentStorage]
 */
public interface AIAgentStorageAPI {
    /**
     * Sets the value associated with the given key in the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @param value The value to be associated with the key.
     */
    public suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T)

    /**
     * Retrieves the value associated with the given key from the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, cast to type [T], or null if the key does not exist.
     */
    public suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T?

    /**
     * Retrieves the non-null value associated with the given key from the storage.
     * If the key does not exist in the storage, a [NoSuchElementException] is thrown.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, of type [T].
     * @throws NoSuchElementException if the key does not exist in the storage.
     */
    public suspend fun <T : Any> getValue(key: AIAgentStorageKey<T>): T

    /**
     * Removes the value associated with the given key from the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, cast to type [T], or null if the key does not exist.
     */
    public suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T?

    /**
     * Converts the storage to a map representation.
     *
     * @return A map containing all key-value pairs currently stored in the system, where keys are of type [AIAgentStorageKey]
     * and values are of type [Any].
     */
    public suspend fun toMap(): Map<AIAgentStorageKey<*>, Any>

    /**
     * Adds all key-value pairs from the given map to the storage.
     *
     * @param map A map containing keys of type [AIAgentStorageKey] and their associated values of type [Any].
     * The keys and values in the provided map will be added to the storage.
     */
    public suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>)

    /**
     * Clears all data from the storage.
     */
    public suspend fun clear()
}
