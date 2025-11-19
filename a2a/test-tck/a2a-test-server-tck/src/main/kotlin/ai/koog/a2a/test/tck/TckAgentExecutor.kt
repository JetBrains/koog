package ai.koog.a2a.test.tck

import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TckAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message
        val userInput = userMessage.parts.filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }
            .lowercase()

        if (userInput.isBlank()) {
            eventProcessor.sendMessage(
                Message(
                    messageId = Uuid.random().toString(),
                    role = Role.Agent,
                    parts = listOf(TextPart("Hello! Please provide a message for me to respond to.")),
                    contextId = context.contextId
                )
            )
            return
        }

        val taskId = context.taskId

        try {
            if (context.task == null) {
                processNewTask(context, eventProcessor, userMessage, userInput)
            } else {
                processExistingTask(context, eventProcessor, userMessage, userInput)
            }
        } catch (e: CancellationException) {
            // Propagate cancellation exception
            throw e
        } catch (e: Exception) {
            // Handle errors by marking task as failed
            val errorMessage = "Error processing request: ${e.message}"
            val failedStatus = TaskStatusUpdateEvent(
                contextId = context.contextId,
                taskId = taskId,
                status = TaskStatus(
                    state = TaskState.Failed,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart(errorMessage)),
                        contextId = context.contextId,
                        taskId = taskId
                    ),
                    timestamp = Clock.System.now()
                ),
                final = true
            )
            eventProcessor.sendTaskEvent(failedStatus)
        }
    }

    override suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        val taskId = context.taskId
        // Cancel the coroutine job if provided
        agentJob?.cancelAndJoin()

        // Send cancellation event
        val canceledStatus = TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = taskId,
            status = TaskStatus(
                state = TaskState.Canceled,
                timestamp = Clock.System.now()
            ),
            final = true
        )
        eventProcessor.sendTaskEvent(canceledStatus)
    }

    private suspend fun processNewTask(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
        userMessage: Message,
        userInput: String,
    ) {
        val task = Task(
            id = context.taskId,
            contextId = context.contextId,
            status = TaskStatus(
                state = TaskState.Submitted,
                timestamp = Clock.System.now()
            ),
            history = listOf(userMessage)
        )

        // Send initial task event
        eventProcessor.sendTaskEvent(task)

        // Short delay to allow tests to see submitted state
        delay(200)

        // Update to working state
        val workingStatus = TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = task.id,
            status = TaskStatus(
                state = TaskState.Working,
                timestamp = Clock.System.now()
            ),
            final = false
        )
        eventProcessor.sendTaskEvent(workingStatus)

        // Short delay for working state
        delay(200)

        // Process the request
        val result = processUserInput(userInput)
        delay(200) // Brief processing delay

        // Create artifact with result
        val artifact = Artifact(
            artifactId = "response",
            parts = listOf(TextPart(result)),
            description = "Agent response to user message."
        )

        val artifactEvent = TaskArtifactUpdateEvent(
            taskId = task.id,
            contextId = context.contextId,
            artifact = artifact,
            append = false
        )
        eventProcessor.sendTaskEvent(artifactEvent)

        // Mark task as completed
        val completedStatus = TaskStatusUpdateEvent(
            contextId = context.contextId,
            taskId = task.id,
            status = TaskStatus(
                state = TaskState.InputRequired,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.Agent,
                    parts = listOf(TextPart(result)),
                    contextId = context.contextId,
                    taskId = task.id
                ),
                timestamp = Clock.System.now()
            ),
            final = true
        )
        eventProcessor.sendTaskEvent(completedStatus)
    }

    private suspend fun processExistingTask(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
        userMessage: Message,
        userInput: String,
    ) {
        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Working,
                    timestamp = Clock.System.now(),
                    message = userMessage
                ),
                final = false
            )
        )

        delay(100)

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Completed,
                    timestamp = Clock.System.now(),
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Task completed successfully!")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = true
            )
        )
    }

    private fun processUserInput(input: String): String {
        return when {
            "hello" in input || "hi" in input -> "Hello World! Nice to meet you!"
            "how are you" in input -> "I'm doing great! Thanks for asking. How can I help you today?"
            "goodbye" in input || "bye" in input -> "Goodbye! Have a wonderful day!"
            else -> "Hello World! You said: '$input'. Thanks for your message!"
        }
    }
}
