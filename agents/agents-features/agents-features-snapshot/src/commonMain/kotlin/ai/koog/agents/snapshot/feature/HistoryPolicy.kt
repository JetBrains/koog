package ai.koog.agents.snapshot.feature

import ai.koog.prompt.message.Message

/**
 * Interface for defining policies to trim message history in checkpoints.
 * 
 * This interface allows implementing custom strategies to limit the size of
 * message history stored in checkpoints, helping to:
 * - Reduce checkpoint storage size
 * - Maintain consistent memory usage across versions
 * - Remove outdated or irrelevant messages
 */
public interface HistoryPolicy {
    /**
     * Trims the message history according to the policy's strategy.
     * 
     * @param history The original message history to trim
     * @return The trimmed message history
     */
    public fun trim(history: List<Message>): List<Message>
}

/**
 * A history policy that limits the number of messages in the history.
 * 
 * This policy keeps the most recent messages up to the specified limit,
 * discarding older messages.
 * 
 * @param maxMessages The maximum number of messages to keep
 */
public class MessageCountHistoryPolicy(private val maxMessages: Int) : HistoryPolicy {
    init {
        require(maxMessages > 0) { "maxMessages must be positive, got: $maxMessages" }
    }

    override fun trim(history: List<Message>): List<Message> {
        return if (history.size <= maxMessages) {
            history
        } else {
            history.takeLast(maxMessages)
        }
    }
}

/**
 * A no-op history policy that preserves all messages.
 * 
 * This is the default policy when no explicit history policy is configured.
 */
public object NoTrimHistoryPolicy : HistoryPolicy {
    override fun trim(history: List<Message>): List<Message> = history
}