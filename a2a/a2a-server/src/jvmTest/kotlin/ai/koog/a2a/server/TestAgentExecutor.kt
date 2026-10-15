@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.a2a.server

import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
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
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun agentMessage(contextId: String, taskId: String?, text: String) = Message(
    messageId = Uuid.random().toString(),
    role = Role.ROLE_AGENT,
    parts = listOf(TextPart(text)),
    contextId = contextId,
    taskId = taskId
)

private fun agentStatus(taskId: String, contextId: String, state: TaskState, text: String) = TaskStatusUpdateEvent(
    taskId = taskId,
    contextId = contextId,
    status = TaskStatus(
        state = state,
        message = agentMessage(contextId, taskId, text),
        timestamp = Clock.System.now()
    )
)

private fun agentStatus(context: RequestContext<*>, state: TaskState, text: String) =
    agentStatus(context.taskId, context.contextId, state, text)

private fun submittedTask(context: RequestContext<SendMessageRequest>) = Task(
    id = context.taskId,
    contextId = context.contextId,
    status = TaskStatus(
        state = TaskState.TASK_STATE_SUBMITTED,
        timestamp = Clock.System.now()
    )
)

private suspend fun sayHello(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendMessage(agentMessage(context.contextId, context.taskId, "Hello World"))
}

private suspend fun doTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendTaskEvent(submittedTask(context))
    eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_WORKING, "Working on task"))
    eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_COMPLETED, "Task completed"))
}

private suspend fun doCancelableTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendTaskEvent(submittedTask(context))
}

private suspend fun doLongRunningTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendTaskEvent(submittedTask(context))

    // Simulate long-running task
    repeat(4) {
        delay(200)

        eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_WORKING, "Still working $it"))
    }

    // Complete the task, same as the Python test server does
    eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_COMPLETED, "Task completed"))
}

/**
 * Creates a task and leaves it waiting for input; the turn ends.
 */
private suspend fun doInputRequiredTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendTaskEvent(submittedTask(context))
    eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_INPUT_REQUIRED, "Please provide input"))
}

class TestAgentExecutor : AgentExecutor {
    override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: TurnEventProcessor) {
        val userMessage = context.params.message
        val userInput = userMessage.parts.filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }
            .lowercase()

        // Test scenarios to test various aspects of A2A
        when (userInput) {
            "hello world" -> sayHello(context, eventProcessor)

            "do task" -> doTask(context, eventProcessor)

            "do cancelable task" -> doCancelableTask(context, eventProcessor)

            "do long-running task" -> doLongRunningTask(context, eventProcessor)

            "do input-required task" -> doInputRequiredTask(context, eventProcessor)

            // A status update without a Task first is rejected by the server.
            "do invalid task" -> eventProcessor.sendTaskEvent(
                agentStatus(context, TaskState.TASK_STATE_WORKING, "Working")
            )

            // Follow-up to a task waiting for input: completes it.
            "provide input" -> eventProcessor.sendTaskEvent(
                agentStatus(context, TaskState.TASK_STATE_COMPLETED, "Input received")
            )

            else -> eventProcessor.sendMessage(
                agentMessage(context.contextId, taskId = null, text = "Sorry, I don't understand you")
            )
        }
    }

    override suspend fun cancel(
        context: RequestContext<CancelTaskRequest>,
        eventProcessor: TurnEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        agentJob?.cancelAndJoin()

        eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_CANCELED, "Task canceled"))
    }
}
