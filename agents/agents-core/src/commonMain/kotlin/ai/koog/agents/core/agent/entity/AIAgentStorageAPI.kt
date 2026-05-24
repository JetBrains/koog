package ai.koog.agents.core.agent.entity

import ai.koog.serialization.JSONElement
import kotlin.jvm.JvmSynthetic

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
    @JvmSynthetic
    public suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T)

    /**
     * Retrieves the value associated with the given key from the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, cast to type [T], or null if the key does not exist.
     */
    @JvmSynthetic
    public suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T?

    /**
     * Retrieves the non-null value associated with the given key from the storage.
     * If the key does not exist in the storage, a [NoSuchElementException] is thrown.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, of type [T].
     * @throws NoSuchElementException if the key does not exist in the storage.
     */
    @JvmSynthetic
    public suspend fun <T : Any> getValue(key: AIAgentStorageKey<T>): T

    /**
     * Removes the value associated with the given key from the storage.
     *
     * @param key The key of type [AIAgentStorageKey] used to identify the value in the storage.
     * @return The value associated with the key, cast to type [T], or null if the key does not exist.
     */
    @JvmSynthetic
    public suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T?

    /**
     * Adds all key-value pairs from the given map to the storage.
     *
     * @param map A map containing keys of type [AIAgentStorageKey] and their associated values of type [Any].
     * The keys and values in the provided map will be added to the storage.
     */
    @JvmSynthetic
    public suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>)

    /**
     * Puts the contents of the [other] storage instance into the current storage.
     * This method combines key-value pairs from the specified storage with the current one.
     * If a key exists in both storages, the value from the given storage will overwrite the existing value.
     *
     * @param other The [AIAgentStorage] instance whose key-value pairs should be merged into the current storage.
     */
    @JvmSynthetic
    public suspend fun putAll(other: AIAgentStorage)

    /**
     * Creates a copy of the current storage.
     */
    @JvmSynthetic
    public suspend fun copy(): AIAgentStorage

    /**
     * Clears all data from the storage.
     */
    @JvmSynthetic
    public suspend fun clear()

    /**
     * Returns serialized storage entries keyed by [AIAgentStorageKey.name].
     *
     * Entries whose values cannot be serialized are omitted.
     */
    @JvmSynthetic
    public suspend fun toSerializedMap(): Map<String, JSONElement>

    /**
     * Adds serialized storage entries from the given map.
     *
     * Each map key is treated as an [AIAgentStorageKey.name]. Later calls to methods like [get] will match entries
     * by that key name.
     *
     * @param map serialized values keyed by storage key name.
     */
    @JvmSynthetic
    public suspend fun putAllSerialized(map: Map<String, JSONElement>)
}
