@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalKoogUtils::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant

/**
 * Represents a storage key used for identifying and accessing data associated with an AI agent.
 *
 * The generic type parameter [T] specifies the type of data associated with this key, ensuring
 * type safety when storing and retrieving data in the context of an AI agent.
 */
public actual class AIAgentStorage internal actual constructor(
    internal actual val delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI by delegate {
    public actual constructor() : this(
        delegate = AIAgentStorageImpl()
    )

    internal actual suspend fun copy(): AIAgentStorage {
        return AIAgentStorage(delegate = delegate.copy())
    }

    @JavaAPI
    @JvmName("set")
    public fun <T : Any> setBlocking(key: AIAgentStorageKey<T>, value: T): Unit = runBlockingReentrant {
        set(key, value)
    }

    @JavaAPI
    @JvmName("get")
    public fun <T : Any> getBlocking(key: AIAgentStorageKey<T>): T? = runBlockingReentrant {
        get(key)
    }

    @JavaAPI
    @JvmName("getValue")
    public fun <T : Any> getValueBlocking(key: AIAgentStorageKey<T>): T = runBlockingReentrant {
        getValue(key)
    }

    @JavaAPI
    @JvmName("remove")
    public fun <T : Any> removeBlocking(key: AIAgentStorageKey<T>): T? = runBlockingReentrant {
        remove(key)
    }

    @JavaAPI
    @JvmName("toMap")
    public fun toMapBlocking(): Map<AIAgentStorageKey<*>, Any> = runBlockingReentrant {
        toMap()
    }

    @JavaAPI
    @JvmName("putAll")
    public fun putAllBlocking(map: Map<AIAgentStorageKey<*>, Any>): Unit = runBlockingReentrant {
        putAll(map)
    }

    @JavaAPI
    @JvmName("clear")
    public fun clearBlocking(): Unit = runBlockingReentrant {
        clear()
    }

    public companion object {
        /**
         * Creates a storage key for a specific type, allowing identification and retrieval of values associated with it.
         *
         * @param name The name of the storage key, used to uniquely identify it.
         * @return A new instance of [AIAgentStorageKey] for the specified type.
         */
        @JavaAPI
        @JvmStatic
        public fun <T : Any> createStorageKey(name: String): AIAgentStorageKey<T> = AIAgentStorageKey(name)
    }
}
