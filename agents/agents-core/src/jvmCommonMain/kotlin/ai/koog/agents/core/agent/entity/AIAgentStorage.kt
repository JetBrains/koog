package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant

@Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(InternalKoogUtils::class)
public actual class AIAgentStorage internal actual constructor(
    internal actual val delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI by delegate {

    public actual constructor(
        serializer: JSONSerializer,
    ) : this(
        delegate = AIAgentStorageImpl(serializer)
    )

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
         * @param typeToken Type of the value stored under this key.
         */
        @JavaAPI
        @JvmStatic
        public fun <T : Any> createStorageKey(name: String, typeToken: TypeToken): AIAgentStorageKey<T> =
            AIAgentStorageKey(name, typeToken)
    }
}
