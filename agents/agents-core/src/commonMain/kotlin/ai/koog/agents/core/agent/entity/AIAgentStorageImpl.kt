package ai.koog.agents.core.agent.entity

import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AIAgentStorageImpl(
    private val serializer: JSONSerializer,
    private val storageMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf(),
    private val serializedStorageMap: MutableMap<String, JSONElement> = mutableMapOf(),
) : AIAgentStorageAPI {
    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T): Unit = mutex.withLock {
        storageMap[key] = value
    }

    override suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        doGet(key)
    }

    override suspend fun <T : Any> getValue(key: AIAgentStorageKey<T>): T {
        return doGet(key) ?: throw NoSuchElementException("Key $key not found in storage")
    }

    override suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        doGet(key)?.also { storageMap -= key }
    }

    override suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>): Unit = mutex.withLock {
        storageMap.putAll(map)
    }

    override suspend fun putAll(other: AIAgentStorage): Unit = mutex.withLock {
        storageMap.putAll(other.delegate.storageMap)
        serializedStorageMap.putAll(other.delegate.serializedStorageMap)
    }

    override suspend fun copy(): AIAgentStorage = mutex.withLock {
        AIAgentStorage(AIAgentStorageImpl(serializer, storageMap.toMutableMap(), serializedStorageMap.toMutableMap()))
    }

    override suspend fun clear(): Unit = mutex.withLock {
        storageMap.clear()
        serializedStorageMap.clear()
    }

    override suspend fun putAllSerialized(map: Map<String, JSONElement>) = mutex.withLock {
        serializedStorageMap.putAll(map)
    }

    override suspend fun toSerializedMap(): Map<String, JSONElement> = mutex.withLock {
        val newSerializedStorageMap = storageMap
            .mapNotNull { (key, value) ->
                runCatching { serializer.encodeToJSONElement(value, key.typeToken) }
                    .getOrNull()
                    ?.let { key.name to it }
            }
            .toMap()

        serializedStorageMap.toMap() + newSerializedStorageMap
    }

    /**
     * Common get implementation without mutex
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> doGet(key: AIAgentStorageKey<T>): T? {
        val value = storageMap[key] as T?
        if (value != null) return value

        val deserializedValue = serializedStorageMap[key.name]
            ?.let { serializer.decodeFromJSONElement(it, key.typeToken) as T? }

        return deserializedValue?.also {
            storageMap[key] = it
            serializedStorageMap -= key.name
        }
    }
}
