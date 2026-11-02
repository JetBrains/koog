package ai.koog.a2a.test.tck

import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FileBytesPart
import ai.koog.a2a.model.FileUrlPart
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.server.turn.TurnEventProcessor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Agent of the TCK system under test. The TCK selects the behavior by the prefix of the message id
 * (`tck-<scenario>-<session>`), see [Scenario].
 *
 * For a new task the first event is the [Task]; for an existing task only status and artifact updates are sent.
 * Every scenario reaches a terminal or an interrupted state, so that no stream stays open.
 */
@OptIn(ExperimentalUuidApi::class)
class TckAgentExecutor : AgentExecutor {
    private enum class Scenario(val prefix: String) {
        COMPLETE_TASK("tck-complete-task"),
        INPUT_REQUIRED("tck-input-required"),
        ARTIFACT_TEXT("tck-artifact-text"),
        ARTIFACT_FILE("tck-artifact-file"),
        ARTIFACT_FILE_URL("tck-artifact-file-url"),
        ARTIFACT_DATA("tck-artifact-data"),
        MESSAGE_RESPONSE("tck-message-response"),
        REJECT_TASK("tck-reject-task"),
        STREAM_COMPLETE("tck-stream-002"),
        STREAM_001("tck-stream-001"),
        STREAM_003("tck-stream-003"),
        STREAM_ORDERING("tck-stream-ordering-001"),
        STREAM_ARTIFACT_TEXT("tck-stream-artifact-text"),
        STREAM_ARTIFACT_FILE("tck-stream-artifact-file"),
        STREAM_ARTIFACT_CHUNKED("tck-stream-artifact-chunked"),
        RESUBSCRIBE("test-resubscribe-message-id"),
    }

    // Longest prefix first, so that `tck-artifact-file-url` wins over `tck-artifact-file`.
    private val scenarios = Scenario.entries.sortedByDescending { it.prefix.length }

    override suspend fun execute(
        context: RequestContext<SendMessageRequest>,
        eventProcessor: TurnEventProcessor
    ) {
        val messageId = context.params.message.messageId
        val scenario = scenarios.firstOrNull { messageId.startsWith(it.prefix) }

        if (scenario == Scenario.MESSAGE_RESPONSE) {
            eventProcessor.sendMessage(
                Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Direct message response")),
                    contextId = context.contextId,
                )
            )
            return
        }

        val events = Events(context, eventProcessor)

        if (context.task == null) {
            events.sendTask(TaskState.TASK_STATE_SUBMITTED)
        }

        when (scenario) {
            Scenario.INPUT_REQUIRED -> events.sendStatus(TaskState.TASK_STATE_INPUT_REQUIRED)

            Scenario.ARTIFACT_TEXT -> {
                events.sendArtifact(TextPart("Generated text content"))
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.ARTIFACT_FILE -> {
                events.sendArtifact(FileBytesPart(raw = "tck".encodeToByteArray(), filename = "output.txt", mediaType = "text/plain"))
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.ARTIFACT_FILE_URL -> {
                events.sendArtifact(
                    FileUrlPart(url = "https://example.com/output.txt", filename = "output.txt", mediaType = "text/plain")
                )
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.ARTIFACT_DATA -> {
                events.sendArtifact(
                    DataPart(
                        buildJsonObject {
                            put("key", "value")
                            put("count", 42)
                        }
                    )
                )
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.REJECT_TASK -> events.sendStatus(TaskState.TASK_STATE_REJECTED)

            Scenario.STREAM_COMPLETE -> events.sendStatus(TaskState.TASK_STATE_COMPLETED)

            Scenario.STREAM_001,
            Scenario.STREAM_003,
            Scenario.STREAM_ORDERING,
            Scenario.STREAM_ARTIFACT_TEXT,
            Scenario.STREAM_ARTIFACT_FILE -> {
                events.sendStatus(TaskState.TASK_STATE_WORKING)
                events.sendArtifact(TextPart("Generated text content"))
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.STREAM_ARTIFACT_CHUNKED -> {
                events.sendStatus(TaskState.TASK_STATE_WORKING)
                events.sendArtifact(TextPart("chunk-1 "), append = false, lastChunk = false)
                events.sendArtifact(TextPart("chunk-2"), append = true, lastChunk = true)
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.RESUBSCRIBE -> {
                events.sendStatus(TaskState.TASK_STATE_WORKING)
                // Two times the TCK streaming timeout, so that a client can resubscribe to the running task.
                delay(4.seconds)
                events.sendStatus(TaskState.TASK_STATE_COMPLETED)
            }

            Scenario.COMPLETE_TASK -> events.sendStatus(TaskState.TASK_STATE_COMPLETED, "Hello from TCK")

            // Unknown prefixes complete the task as well, so that the TCK never waits on an open stream.
            else -> events.sendStatus(TaskState.TASK_STATE_COMPLETED, "Unhandled messageId prefix: $messageId")
        }
    }

    override suspend fun cancel(
        context: RequestContext<CancelTaskRequest>,
        eventProcessor: TurnEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        agentJob?.cancelAndJoin()
        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_CANCELED,
                    message = agentMessage(context.contextId, context.taskId, "Task canceled"),
                    timestamp = Clock.System.now(),
                ),
            )
        )
    }

    private fun agentMessage(contextId: String, taskId: String, text: String) = Message(
        messageId = Uuid.random().toString(),
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart(text)),
        taskId = taskId,
        contextId = contextId,
    )

    private inner class Events(
        private val context: RequestContext<SendMessageRequest>,
        private val eventProcessor: TurnEventProcessor,
    ) {
        suspend fun sendTask(state: TaskState) = eventProcessor.sendTaskEvent(
            Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(state = state, timestamp = Clock.System.now()),
            )
        )

        suspend fun sendStatus(state: TaskState, text: String? = null) = eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = state,
                    message = text?.let { agentMessage(context.contextId, context.taskId, it) },
                    timestamp = Clock.System.now(),
                ),
            )
        )

        suspend fun sendArtifact(part: Part, append: Boolean? = null, lastChunk: Boolean? = null) =
            eventProcessor.sendTaskEvent(
                TaskArtifactUpdateEvent(
                    taskId = context.taskId,
                    contextId = context.contextId,
                    artifact = Artifact(artifactId = "tck-artifact", name = "tck-artifact", parts = listOf(part)),
                    append = append,
                    lastChunk = lastChunk,
                )
            )
    }
}
