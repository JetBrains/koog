@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.a2a.server

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private suspend fun sayHello(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
) {
    eventProcessor.sendMessage(
        Message(
            messageId = Uuid.random().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Hello World")),
            contextId = context.contextId,
            taskId = context.taskId
        )
    )
}

private suspend fun doTask(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = TaskState.Submitted,
            timestamp = Clock.System.now()
        ),
        history = listOf(context.params.message)
    )

    // Send initial task event
    eventProcessor.sendTaskEvent(task)

    // Send task working status update
    eventProcessor.sendTaskEvent(
        TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = context.taskId,
            status = TaskStatus(
                state = TaskState.Working,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.Agent,
                    parts = listOf(TextPart("Working on task")),
                    contextId = context.contextId,
                    taskId = context.taskId
                ),
                timestamp = Clock.System.now()
            ),
            final = false
        )
    )

    // Send task completion status update
    eventProcessor.sendTaskEvent(
        TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = context.taskId,
            status = TaskStatus(
                state = TaskState.Completed,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.Agent,
                    parts = listOf(TextPart("Task completed")),
                    contextId = context.contextId,
                    taskId = context.taskId
                ),
                timestamp = Clock.System.now()
            ),
            final = true
        )
    )
}

private suspend fun doCancelableTask(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = TaskState.Submitted,
            timestamp = Clock.System.now()
        ),
        history = listOf(context.params.message)
    )

    eventProcessor.sendTaskEvent(task)
}

private suspend fun doLongRunningTask(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = TaskState.Submitted,
            timestamp = Clock.System.now()
        ),
        history = listOf(context.params.message)
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
                    state = TaskState.Working,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Still working $it")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    ),
                    timestamp = Clock.System.now()
                ),
                final = false
            )
        )
    }
}

class TestAgentExecutor : AgentExecutor {
    override suspend fun execute(context: RequestContext<MessageSendParams>, eventProcessor: SessionEventProcessor) {
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

            else -> {
                eventProcessor.sendMessage(
                    Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Sorry, I don't understand you")),
                        contextId = context.contextId
                    )
                )
            }
        }
    }

    override suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        agentJob?.cancelAndJoin()

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                contextId = context.contextId,
                taskId = context.taskId,
                status = TaskStatus(
                    state = TaskState.Canceled,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Task canceled")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    ),
                    timestamp = Clock.System.now()
                ),
                final = true
            )
        )
    }
}
