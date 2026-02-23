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
     */
    public fun addPreProcessor(preProcessor: ChatMemoryPreProcessor) {
        preprocessors.add(preProcessor)
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
     */
    public fun windowSize(size: Int) {
        addPreProcessor(WindowSizePreProcessor(size))
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
