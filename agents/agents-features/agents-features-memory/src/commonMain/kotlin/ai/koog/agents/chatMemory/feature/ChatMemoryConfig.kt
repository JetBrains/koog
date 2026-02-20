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
     * Maximum number of messages to keep in the conversation window.
     *
     * When set, only the most recent [windowSize] messages are loaded into the prompt
     * and stored after each run. This prevents unbounded prompt growth in long conversations.
     *
     * A value of `null` means no limit — all messages are kept.
     */
    public var windowSize: Int? = null
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
