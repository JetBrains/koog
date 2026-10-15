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

private suspend fun sayHello(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendMessage(
        Message(
            messageId = Uuid.random().toString(),
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart("Hello World")),
            contextId = context.contextId,
            taskId = context.taskId
        )
    )
}

private suspend fun doTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = TaskState.TASK_STATE_SUBMITTED,
            timestamp = Clock.System.now()
        )
    )

    // Send initial task event
    eventProcessor.sendTaskEvent(task)

    // Send task working status update
    eventProcessor.sendTaskEvent(
        TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = context.taskId,
            status = TaskStatus(
                state = TaskState.TASK_STATE_WORKING,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Working on task")),
                    contextId = context.contextId,
                    taskId = context.taskId
                ),
                timestamp = Clock.System.now()
            )
        )
    )

    // Send task completion status update
    eventProcessor.sendTaskEvent(
        TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = context.taskId,
            status = TaskStatus(
                state = TaskState.TASK_STATE_COMPLETED,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Task completed")),
                    contextId = context.contextId,
                    taskId = context.taskId
                ),
                timestamp = Clock.System.now()
            )
        )
    )
}

private suspend fun doCancelableTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = TaskState.TASK_STATE_SUBMITTED,
            timestamp = Clock.System.now()
        )
    )

    eventProcessor.sendTaskEvent(task)
}

private suspend fun doLongRunningTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = TaskState.TASK_STATE_SUBMITTED,
            timestamp = Clock.System.now()
        )
    )

    eventProcessor.sendTaskEvent(task)

    // Simulate long-running task
    repeat(4) {
        delay(200)

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_WORKING,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Still working $it")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    ),
                    timestamp = Clock.System.now()
                )
            )
        )
    }

    // Complete the task, same as the Python test server does
    eventProcessor.sendTaskEvent(
        TaskStatusUpdateEvent(
            taskId = context.taskId,
            contextId = context.contextId,
            status = TaskStatus(
                state = TaskState.TASK_STATE_COMPLETED,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Task completed")),
                    contextId = context.contextId,
                    taskId = context.taskId
                ),
                timestamp = Clock.System.now()
            )
        )
    )
}

private fun agentStatus(
    context: RequestContext<SendMessageRequest>,
    state: TaskState,
    text: String,
) = TaskStatusUpdateEvent(
    taskId = context.taskId,
    contextId = context.contextId,
    status = TaskStatus(
        state = state,
        message = Message(
            messageId = Uuid.random().toString(),
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart(text)),
            contextId = context.contextId,
            taskId = context.taskId
        ),
        timestamp = Clock.System.now()
    )
)

/**
 * Creates a task and leaves it waiting for input; the turn ends.
 */
private suspend fun doInputRequiredTask(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendTaskEvent(
        Task(
            id = context.taskId,
            contextId = context.contextId,
            status = TaskStatus(
                state = TaskState.TASK_STATE_SUBMITTED,
                timestamp = Clock.System.now()
            )
        )
    )

    eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_INPUT_REQUIRED, "Please provide input"))
}

/**
 * Follow-up to a task waiting for input: completes it.
 */
private suspend fun provideInput(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor,
) {
    eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_COMPLETED, "Input received"))
}

class TestAgentExecutor : AgentExecutor {
    override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: TurnEventProcessor) {
        val userMessage = context.params.message
        val userInput = userMessage.parts.filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }
            .lowercase()

        // Test scenarios to test various aspects of A2A
        when (userInput) {
            "hello world" -> {
                sayHello(context, eventProcessor)
            }

            "do task" -> {
                doTask(context, eventProcessor)
            }

            "do cancelable task" -> {
                doCancelableTask(context, eventProcessor)
            }

            "do long-running task" -> {
                doLongRunningTask(context, eventProcessor)
            }

            "do input-required task" -> {
                doInputRequiredTask(context, eventProcessor)
            }

            "do invalid task" -> {
                // A status update without a Task first is rejected by the server.
                eventProcessor.sendTaskEvent(agentStatus(context, TaskState.TASK_STATE_WORKING, "Working"))
            }

            "provide input" -> {
                provideInput(context, eventProcessor)
            }

            else -> {
                eventProcessor.sendMessage(
                    Message(
                        messageId = Uuid.random().toString(),
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Sorry, I don't understand you")),
                        contextId = context.contextId
                    )
                )
            }
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
                contextId = context.contextId,
                taskId = context.taskId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_CANCELED,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Task canceled")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    ),
                    timestamp = Clock.System.now()
                )
            )
        )
    }
}
