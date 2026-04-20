package ai.koog.agents.core.agent.entity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal class AIAgentStorageImpl : AIAgentStorageAPI {
    private val mutex = Mutex()
    private val storage = mutableMapOf<AIAgentStorageKey<*>, Any>()

    internal suspend fun copy(): AIAgentStorageImpl {
        val newStorage = AIAgentStorageImpl()
        newStorage.putAll(this.toMap())
        return newStorage
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> set(key: AIAgentStorageKey<T>, value: T): Unit = mutex.withLock {
        storage[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    override suspend fun <T : Any> getValue(key: AIAgentStorageKey<T>): T {
        return get(key) ?: throw NoSuchElementException("Key $key not found in storage")
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> remove(key: AIAgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    override suspend fun toMap(): Map<AIAgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    override suspend fun putAll(map: Map<AIAgentStorageKey<*>, Any>): Unit = mutex.withLock {
        storage.putAll(map)
    }

    override suspend fun clear(): Unit = mutex.withLock {
        storage.clear()
    }

    override suspend fun serializeToJson(json: Json): JsonObject {
        val snapshot = mutex.withLock { storage.toList() }
        return buildJsonObject {
            for ((key, value) in snapshot) {
                if (key is SerializableStorageKey<*>) {
                    put(key.name, key.encodeValue(json, value))
                }
            }
        }
    }

    override suspend fun restoreFromJson(
        jsonObject: JsonObject,
        keys: Collection<SerializableStorageKey<*>>,
        json: Json,
    ) {
        val keysByName = keys.associateBy { it.name }
        val decoded = buildMap {
            for ((name, element) in jsonObject) {
                val key = keysByName[name] ?: continue
                put(key, key.decodeValue(json, element))
            }
        }
        mutex.withLock { storage.putAll(decoded) }
    }
}
