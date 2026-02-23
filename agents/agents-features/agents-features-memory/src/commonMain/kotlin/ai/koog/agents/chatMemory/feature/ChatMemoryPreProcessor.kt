package ai.koog.agents.chatMemory.feature

import ai.koog.prompt.message.Message

/**
 * An interface for pre-processing messages before they are stored or loaded in the chat memory.
 *
 * Preprocessors are applied in order when messages are loaded from or stored to the history provider.
 * They allow transforming the message list at each stage, enabling use cases such as
 * sliding window truncation, message filtering, summarization, etc.
 *
 * @see WindowSizePreProcessor
 */
public interface ChatMemoryPreProcessor {
    public fun preprocess(messages: List<Message>): List<Message>
}

/**
 * A [ChatMemoryPreProcessor] that limits the number of messages to a sliding window
 * of the most recent [windowSize] messages.
 *
 * Example usage:
 * ```kotlin
 * installChatMemory {
 *     chatHistoryProvider = MyChatHistoryProvider()
 *     addPreProcessor(WindowSizePreProcessor(20))
 * }
 * ```
 *
 * @param windowSize The maximum number of recent messages to keep.
 */
public class WindowSizePreProcessor(private val windowSize: Int) : ChatMemoryPreProcessor {
    override fun preprocess(messages: List<Message>): List<Message> {
        return messages.takeLast(windowSize)
    }
}
