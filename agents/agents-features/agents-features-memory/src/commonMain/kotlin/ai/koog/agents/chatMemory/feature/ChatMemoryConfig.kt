package ai.koog.agents.chatMemory.feature

import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.prompt.message.Message

/**
 * Configuration for the [ChatMemory] feature.
 *
 * Allows configuring how conversation history is stored and loaded
 * for agent-user interactions.
 */
public class ChatMemoryConfig : FeatureConfig() {

    /**
     * A provider responsible for persisting and retrieving conversation history.
     *
     * Defaults to [InMemoryChatHistoryProvider].
     */
    public var chatHistoryProvider: ChatHistoryProvider = InMemoryChatHistoryProvider()

    /**
     * Ordered list of preprocessors applied to messages when loading from and storing to history.
     *
     * Preprocessors are applied sequentially in the order they were added.
     *
     * @see ChatMemoryPreProcessor
     * @see addPreProcessor
     * @see windowSize
     */
    public val preprocessors: MutableList<ChatMemoryPreProcessor> = mutableListOf()

    /**
     * Sets the [ChatHistoryProvider] and returns this config for fluent chaining.
     *
     * This method is an alternative to the [chatHistoryProvider] property setter,
     * designed for Java callers who want to chain configuration calls:
     * ```java
     * config.chatHistoryProvider(myProvider).windowSize(20)
     * ```
     *
     * @param provider The provider responsible for persisting and retrieving conversation history.
     * @return This [ChatMemoryConfig] instance for fluent chaining.
     */
    public fun chatHistoryProvider(provider: ChatHistoryProvider): ChatMemoryConfig {
        chatHistoryProvider = provider
        return this
    }

    /**
     * Adds a [ChatMemoryPreProcessor] to the preprocessing chain.
     *
     * Preprocessors are applied in the order they are added, both when loading
     * messages from history and when storing them.
     *
     * Example:
     * ```kotlin
     * installChatMemory {
     *     addPreProcessor(myCustomPreProcessor)
     * }
     * ```
     *
     * @param preProcessor The preprocessor to add.
     * @return This [ChatMemoryConfig] instance for fluent chaining.
     */
    public fun addPreProcessor(preProcessor: ChatMemoryPreProcessor): ChatMemoryConfig {
        preprocessors.add(preProcessor)
        return this
    }

    /**
     * Adds a [WindowSizePreProcessor] that limits messages to the most recent [size] entries.
     *
     * This prevents unbounded prompt growth in long conversations by keeping only a
     * sliding window of messages.
     *
     * Example:
     * ```kotlin
     * installChatMemory {
     *     chatHistoryProvider = MyChatHistoryProvider()
     *     windowSize(20)
     * }
     * ```
     *
     * @param size The maximum number of recent messages to keep.
     * @return This [ChatMemoryConfig] instance for fluent chaining.
     */
    public fun windowSize(size: Int): ChatMemoryConfig {
        addPreProcessor(WindowSizePreProcessor(size))
        return this
    }

    /**
     * Adds a [FilterMessagesPreProcessor] that keeps only messages matching the given [predicate].
     *
     * The order in which `filterMessages` is called relative to other preprocessors matters:
     * ```kotlin
     * // Keeps at most 10 messages, then filters short ones from those 10
     * windowSize(10)
     * filterMessages { it.content.length <= 100 }
     *
     * // Filters short messages first, then keeps the last 10 of those
     * filterMessages { it.content.length <= 100 }
     * windowSize(10)
     * ```
     *
     * @param predicate A [MessageFilter] that returns `true` for messages to keep.
     * @return This [ChatMemoryConfig] instance for fluent chaining.
     */
    public fun filterMessages(predicate: MessageFilter): ChatMemoryConfig {
        addPreProcessor(FilterMessagesPreProcessor(predicate))
        return this
    }
}

/**
 * Provider interface for storing and loading conversation history.
 */
public interface ChatHistoryProvider {

    /**
     * Store a list of messages as conversation history.
     *
     * @param conversationId Unique identifier for the conversation.
     * @param messages The messages to store.
     */
    public suspend fun store(conversationId: String, messages: List<Message>)

    /**
     * Load previously stored conversation history.
     *
     * @param conversationId Unique identifier for the conversation.
     * @return The stored messages, or an empty list if no history exists.
     */
    public suspend fun load(conversationId: String): List<Message>
}
