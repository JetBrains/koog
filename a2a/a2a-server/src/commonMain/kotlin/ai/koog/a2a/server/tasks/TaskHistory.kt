package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task

/**
 * Returns this history with [message] appended, unless a message with the same [Message.messageId] is already in it.
 *
 * This is the single rule by which the server adds messages to [Task.history]: a message that is already part of the
 * history is never added again, so e.g. an agent that put the user message into the task itself, or repeated a message
 * in several status updates, doesn't produce duplicates.
 */
internal fun List<Message>?.plusIfAbsent(message: Message): List<Message> {
    val current = this.orEmpty()
    return if (current.any { it.messageId == message.messageId }) current else current + message
}
