@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.coroutines.withSuspend

/**
 * Represents a storage key used for identifying and accessing data associated with an AI agent.
 *
 * The generic type parameter [T] specifies the type of data associated with this key, ensuring
 * type safety when storing and retrieving data in the context of an AI agent.
 */
@OptIn(InternalKoogUtils::class)
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
    @OptIn(InternalAgentsApi::class)
    public fun <T : Any> setBlocking(key: AIAgentStorageKey<T>, value: T): Unit = withSuspend {
        set(key, value)
    }

    @JavaAPI
    @JvmName("get")
    @OptIn(InternalAgentsApi::class)
    public fun <T : Any> getBlocking(key: AIAgentStorageKey<T>): T? = withSuspend {
        get(key)
    }

    @JavaAPI
    @JvmName("getValue")
    @OptIn(InternalAgentsApi::class)
    public fun <T : Any> getValueBlocking(key: AIAgentStorageKey<T>): T = withSuspend {
        getValue(key)
    }

    @JavaAPI
    @JvmName("remove")
    @OptIn(InternalAgentsApi::class)
    public fun <T : Any> removeBlocking(key: AIAgentStorageKey<T>): T? = withSuspend {
        remove(key)
    }

    @JavaAPI
    @JvmName("toMap")
    @OptIn(InternalAgentsApi::class)
    public fun toMapBlocking(): Map<AIAgentStorageKey<*>, Any> = withSuspend {
        toMap()
    }

    @JavaAPI
    @JvmName("putAll")
    @OptIn(InternalAgentsApi::class)
    public fun putAllBlocking(map: Map<AIAgentStorageKey<*>, Any>): Unit = withSuspend {
        putAll(map)
    }

    @JavaAPI
    @JvmName("clear")
    @OptIn(InternalAgentsApi::class)
    public fun clearBlocking(): Unit = withSuspend {
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
