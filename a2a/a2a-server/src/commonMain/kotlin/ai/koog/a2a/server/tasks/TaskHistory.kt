package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task

/**
 * Returns this history with [message] appended, unless a message with the same [Message.messageId] is already in it.
 *
 * The single rule by which the server adds messages to [Task.history], so an agent that put the user message into the
 * task itself, or repeated a message in several status updates, doesn't produce duplicates.
 */
internal fun List<Message>?.plusIfAbsent(message: Message): List<Message> {
    val current = this.orEmpty()
    return if (current.any { it.messageId == message.messageId }) current else current + message
}
