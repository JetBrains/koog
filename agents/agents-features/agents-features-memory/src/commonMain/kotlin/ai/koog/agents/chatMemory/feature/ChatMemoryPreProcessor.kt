package ai.koog.agents.chatMemory.feature

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart

/**
 * An interface for pre-processing messages before they are stored or loaded in the chat memory.
 *
 * Preprocessors are applied in order when messages are loaded from or stored to the history provider.
 * They allow transforming the message list at each stage, enabling use cases such as
 * sliding window truncation, message filtering, summarization, etc.
 *
 * Note that the order of preprocessors matters. For example:
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
 * @see WindowSizePreProcessor
 * @see FilterMessagesPreProcessor
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
 * Tool results whose tool call was cut off by the window are dropped as well, so the window never
 * starts with a result the LLM has no matching call for (providers reject such histories).
 * As a consequence, the result may contain fewer than [windowSize] messages.
 *
 * @param windowSize The maximum number of recent messages to keep.
 */
public class WindowSizePreProcessor(private val windowSize: Int) : ChatMemoryPreProcessor {
    override fun preprocess(messages: List<Message>): List<Message> {
        val toolCalls = mutableSetOf<String>()
        return messages.takeLast(windowSize).mapNotNull { message ->
            message.parts.filterIsInstance<MessagePart.Tool.Call>().forEach { toolCalls += it.pairingKey }
            if (message !is Message.User) return@mapNotNull message

            val parts = message.parts.filterNot { it is MessagePart.Tool.Result && it.pairingKey !in toolCalls }
            when {
                parts.size == message.parts.size -> message
                parts.isEmpty() -> null
                else -> message.copy(parts = parts)
            }
        }
    }

    private val MessagePart.Tool.pairingKey: String
        get() = when (this) {
            is MessagePart.Tool.Call -> id
            is MessagePart.Tool.Result -> id
        } ?: tool
}

/**
 * A predicate for filtering messages in [FilterMessagesPreProcessor].
 *
 * This is a functional interface so it can be used as a SAM type from Java:
 * ```java
 * config.filterMessages(message -> message.getContent().length() <= 100);
 * ```
 */
public fun interface MessageFilter {
    /**
     * Tests whether the given message should be kept.
     *
     * @param message The message to test.
     * @return `true` to keep the message, `false` to discard it.
     */
    public fun test(message: Message): Boolean
}

/**
 * A [ChatMemoryPreProcessor] that filters messages using a [MessageFilter] predicate.
 *
 * Only messages for which [filter] returns `true` are kept.
 *
 * Example usage:
 * ```kotlin
 * installChatMemory {
 *     filterMessages { it.content.length <= 100 }
 * }
 * ```
 *
 * @param filter The predicate that decides which messages to keep.
 * @see MessageFilter
 */
public class FilterMessagesPreProcessor(private val filter: MessageFilter) : ChatMemoryPreProcessor {
    override fun preprocess(messages: List<Message>): List<Message> {
        return messages.filter { filter.test(it) }
    }
}
