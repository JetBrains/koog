package ai.koog.a2a.server

import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.ServerCallContext
import kotlin.time.Instant

// Builders of the events and requests shared by the server tests. Ids and the timestamp default to the constants of
// the fixtures, message ids are derived from the text, so equal arguments give equal objects.

internal val ctx = ServerCallContext()

internal fun userMessage(
    text: String,
    taskId: String? = null,
    contextId: String? = CONTEXT_ID,
    messageId: String = "user-$text",
) = Message(
    messageId = messageId,
    role = Role.ROLE_USER,
    parts = listOf(TextPart(text)),
    contextId = contextId,
    taskId = taskId,
)

internal fun agentMessage(
    text: String,
    taskId: String? = TASK_ID,
    contextId: String? = CONTEXT_ID,
    messageId: String = "agent-$text",
) = Message(
    messageId = messageId,
    role = Role.ROLE_AGENT,
    parts = listOf(TextPart(text)),
    contextId = contextId,
    taskId = taskId,
)

internal fun request(
    text: String,
    taskId: String? = null,
    contextId: String? = CONTEXT_ID,
    returnImmediately: Boolean? = null,
    historyLength: Int? = null,
) = SendMessageRequest(
    message = userMessage(text, taskId, contextId),
    configuration = if (returnImmediately != null || historyLength != null) {
        SendMessageConfiguration(returnImmediately = returnImmediately, historyLength = historyLength)
    } else {
        null
    },
)

/** A task without a status message; its history is the user message [userText], if given. */
internal fun task(
    state: TaskState = TaskState.TASK_STATE_SUBMITTED,
    userText: String? = null,
    taskId: String = TASK_ID,
    contextId: String = CONTEXT_ID,
) = Task(
    id = taskId,
    contextId = contextId,
    status = TaskStatus(state = state, timestamp = TIMESTAMP),
    history = userText?.let { listOf(userMessage(it, contextId = contextId)) },
)

/** A status update with the agent message [text] (none for `null`). */
internal fun statusUpdate(
    state: TaskState,
    text: String? = state.name,
    taskId: String = TASK_ID,
    contextId: String = CONTEXT_ID,
    timestamp: Instant? = TIMESTAMP,
) = TaskStatusUpdateEvent(
    taskId = taskId,
    contextId = contextId,
    status = TaskStatus(
        state = state,
        message = text?.let { agentMessage(it, taskId, contextId) },
        timestamp = timestamp,
    ),
)

/** An artifact chunk of [artifactId] to append to. */
internal fun artifactChunk(
    text: String,
    artifactId: String = "artifact-1",
    taskId: String = TASK_ID,
    contextId: String = CONTEXT_ID,
) = TaskArtifactUpdateEvent(
    taskId = taskId,
    contextId = contextId,
    artifact = Artifact(artifactId = artifactId, parts = listOf(TextPart(text))),
    append = true,
)
