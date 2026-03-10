@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

/**
 * Represents a storage key used for identifying and accessing data associated with an AI agent.
 *
 * The generic type parameter [T] specifies the type of data associated with this key, ensuring
 * type safety when storing and retrieving data in the context of an AI agent.
 *
 * @param name The string identifier that uniquely represents the storage key.
 */
public class AIAgentStorageKey<T : Any>(public val name: String) {
    override fun toString(): String = "${super.toString()}(name=$name)"
}

/**
 * Creates a storage key for a specific type, allowing identification and retrieval of values associated with it.
 *
 * @param name The name of the storage key, used to uniquely identify it.
 * @return A new instance of [AIAgentStorageKey] for the specified type.
 */
public fun <T : Any> createStorageKey(name: String): AIAgentStorageKey<T> = AIAgentStorageKey(name)

/**
 * Concurrent-safe key-value storage for an agent.
 * You can create typed keys for your data using the [createStorageKey] function and
 * set and retrieve data using it by calling [set] and [get].
 */
public expect class AIAgentStorage internal constructor(
    delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI {
    public constructor()

    internal val delegate: AIAgentStorageImpl

    /**
     * Creates a deep copy of this storage.
     *
     * @return A new instance of [AIAgentStorage] with the same content as this one.
     */
    internal suspend fun copy(): AIAgentStorage

    override suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T)
    override suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T?
    override suspend fun <T : Any> getValue(key: AIAgentStorageKey<T>): T
    override suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T?
    override suspend fun toMap(): Map<AIAgentStorageKey<*>, Any>
    override suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>)
    override suspend fun clear()
}
