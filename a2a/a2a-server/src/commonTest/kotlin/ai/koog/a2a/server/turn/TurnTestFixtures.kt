package ai.koog.a2a.server.turn

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import kotlin.time.Instant

internal object FixedMessageIdGenerator : IdGenerator {
    const val MESSAGE_ID = "generated-message-id"

    override suspend fun generateContextId(message: Message): String = error("Not expected")
    override suspend fun generateTaskId(message: Message): String = error("Not expected")
    override suspend fun generateMessageId(taskId: String, contextId: String): String = MESSAGE_ID
}

/** The status of a task whose turn has failed: the message is generic, it doesn't expose the exception. */
internal fun failedStatus(taskId: String, contextId: String, timestamp: Instant?) = TaskStatus(
    state = TaskState.TASK_STATE_FAILED,
    message = Message(
        messageId = FixedMessageIdGenerator.MESSAGE_ID,
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("Task failed due to an internal error")),
        contextId = contextId,
        taskId = taskId,
    ),
    timestamp = timestamp,
)
