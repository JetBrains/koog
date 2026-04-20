@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Represents a storage key used for identifying and accessing data associated with an AI agent.
 *
 * The generic type parameter [T] specifies the type of data associated with this key, ensuring
 * type safety when storing and retrieving data in the context of an AI agent.
 *
 * Equality of [AIAgentStorageKey] instances is based on referential identity (default implementation):
 * two different instances created with the same [name] are not equal and will refer to distinct
 * storage entries. The [name] is used only for the string representation of the key.
 *
 * @param name The human-readable name of the storage key, used only for its string representation.
 */
public open class AIAgentStorageKey<T : Any>(public val name: String) {
    override fun toString(): String = "${super.toString()}(name=$name)"
}

/**
 * Creates a unique storage key for a specific type, allowing identification and retrieval of values associated with it.
 *
 * @param name The name of the storage key used only for its string representation.
 * @return A new instance of [AIAgentStorageKey] for the specified type.
 */
public fun <T : Any> createStorageKey(name: String): AIAgentStorageKey<T> = AIAgentStorageKey(name)

/**
 * A storage key that carries a [KSerializer] for its associated value type [T].
 *
 * When storage is serialized via [AIAgentStorageAPI.serializeToJson], only entries whose key is
 * a [SerializableStorageKey] are included; all other entries are silently skipped.
 *
 * @param name The human-readable name of the storage key, used only for its string representation.
 * @param serializer The serializer used to encode and decode values associated with this key.
 */
public class SerializableStorageKey<T : Any>(
    name: String,
    public val serializer: KSerializer<T>,
) : AIAgentStorageKey<T>(name) {

    @Suppress("UNCHECKED_CAST")
    internal fun encodeValue(json: Json, value: Any): JsonElement =
        json.encodeToJsonElement(serializer, value as T)

    internal fun decodeValue(json: Json, element: JsonElement): T =
        json.decodeFromJsonElement(serializer, element)
}

/**
 * Creates a [SerializableStorageKey] with an explicit serializer.
 *
 * @param name The human-readable name of the storage key, used only for its string representation.
 * @param serializer The [KSerializer] for the value type [T].
 */
public fun <T : Any> createSerializableStorageKey(
    name: String,
    serializer: KSerializer<T>,
): SerializableStorageKey<T> = SerializableStorageKey(name, serializer)

/**
 * Creates a [SerializableStorageKey] using the reified serializer for [T].
 * [T] must be annotated with [@Serializable][kotlinx.serialization.Serializable].
 *
 * @param name The human-readable name of the storage key, used only for its string representation.
 */
public inline fun <reified T : Any> createSerializableStorageKey(
    name: String,
): SerializableStorageKey<T> = SerializableStorageKey(name, serializer())

/**
 * Concurrent-safe key-value storage for an agent.
 * You can create typed keys for your data using the [createStorageKey] function and
 * set and retrieve data using it by calling [set] and [get].
 *
 * To persist only selected entries, create keys with [createSerializableStorageKey] and use
 * [serializeToJson] / [restoreFromJson].
 */
public expect class AIAgentStorage internal constructor(
    delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI {
    public constructor()

    internal val delegate: AIAgentStorageImpl

    /**
     * Creates a copy of this storage.
     *
     * The key-to-value mapping is copied, but the stored values themselves are not deep-copied:
     * both the original and the copy share the same value instances.
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
    override suspend fun serializeToJson(json: Json): JsonObject
    override suspend fun restoreFromJson(jsonObject: JsonObject, keys: Collection<SerializableStorageKey<*>>, json: Json)
}
