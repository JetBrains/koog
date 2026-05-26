@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken

/**
 * Represents a storage key used for [AIAgentStorage].
 * Equality is based on the [name].
 *
 * @param name String key.
 * @param typeToken Type of the value stored under this key.
 */
public data class AIAgentStorageKey<T : Any>(
    public val name: String,
    public val typeToken: TypeToken,
) {
    override fun toString(): String = "${super.toString()}(name=$name)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AIAgentStorageKey<*>) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

/**
 * Creates a [AIAgentStorageKey].
 *
 * @param name String key.
 * @param typeToken Type of the value stored under this key.
 */
public fun <T : Any> createStorageKey(name: String, typeToken: TypeToken): AIAgentStorageKey<T> =
    AIAgentStorageKey(name, typeToken)

/**
 * Creates a [AIAgentStorageKey].
 *
 * @param name String key.
 * @param T Type of the value stored under this key.
 */
public inline fun <reified T : Any> createStorageKey(name: String): AIAgentStorageKey<T> =
    AIAgentStorageKey(name, typeToken<T>())

/**
 * Concurrent-safe key-value storage for an agent.
 * You can create typed keys for your data using the [createStorageKey] function and
 * set and retrieve data using it by calling [set] and [get].
 */
public expect class AIAgentStorage internal constructor(
    delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI {

    /**
     * Creates an instance of [AIAgentStorage].
     *
     * @param serializer The [JSONSerializer] used to handle serialization and deserialization of stored values.
     */
    public constructor(
        serializer: JSONSerializer,
    )

    internal val delegate: AIAgentStorageImpl

    override suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T)
    override suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T?
    override suspend fun <T : Any> getValue(key: AIAgentStorageKey<T>): T
    override suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T?
    override suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>)
    override suspend fun putAll(other: AIAgentStorage)
    override suspend fun clear()
    override suspend fun copy(): AIAgentStorage
    override suspend fun toSerializedMap(): Map<String, JSONElement>
    override suspend fun putAllSerialized(map: Map<String, JSONElement>)
}
