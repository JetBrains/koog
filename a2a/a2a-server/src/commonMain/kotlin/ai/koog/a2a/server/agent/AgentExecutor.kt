package ai.koog.a2a.server.agent

import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import kotlinx.coroutines.Deferred

/**
 * Implementations of this interface contain the core logic of the agent,
 * executing actions based on requests and publishing updates to an event processor.
 */
public interface AgentExecutor {
    /**
     * Execute the agent's logic for a given request context.
     *
     * The agent should read necessary information from the [context] and publish [TaskEvent] or [Message] events to
     * the [eventProcessor]. This method should return once the agent's execution for this request is complete or
     * yields control (e.g., enters a [TaskState.TASK_STATE_INPUT_REQUIRED] state). Returning from this method is the
     * only end-of-turn signal for non-terminal states, because there is no `final` flag on status update events.
     *
     * All events must have context id from [RequestContext.contextId] and for task events task id from [RequestContext.taskId].
     *
     * Can throw an exception if the input is invalid or the agent fails to execute the request. If the task exists and
     * is not in a terminal state when this method throws (other than on cancellation), the server marks the task
     * [TaskState.TASK_STATE_FAILED] and notifies all streams of the task.
     *
     * The server persists the user message ([RequestContext.params] message) into the task history itself, deduplicated by
     * message id, so the agent doesn't have to; the stored copy has the task id and context id of the task. For a new
     * task it is added to the initial [Task] the agent sends. For a follow-up it is added lazily, right before the
     * first status or artifact update the agent sends in this turn: the status message that was current before the
     * follow-up is moved into the history, followed by the user message, and [ai.koog.a2a.model.TaskStatus.message] is
     * cleared. If the agent sends no such event, the user message is not persisted. [RequestContext.task] is the full
     * stored task of a follow-up as of the start of the turn (history and artifacts), so it doesn't contain the user
     * message yet.
     *
     * A [Task] event for a task that already exists is ignored (neither stored nor delivered): for a follow-up, send
     * status and artifact updates only.
     *
     * Events sent to the [eventProcessor] are persisted when the send call returns, and are delivered to the requester
     * of this turn as well as to every client subscribed to the task (SubscribeToTask).
     *
     * Example implementation:
     * ```kotlin
     * val userMessage = context.params.message
     *
     * // Process the message and create a task
     * // Send task creation event
     * eventProcessor.sendTaskEvent(
     *     Task(
     *         id = context.taskId,
     *         contextId = context.contextId,
     *         status = TaskStatus(
     *             state = TaskState.TASK_STATE_WORKING,
     *             timestamp = Clock.System.now()
     *         ),
     *         // Optional: the server adds the user message to the task history if it is not there already
     *         history = listOf(userMessage.copy(taskId = context.taskId)),
     *     )
     * )
     *
     * // Simulate some work
     * delay(1000)
     *
     * // Mark task as completed
     * eventProcessor.sendTaskEvent(
     *     TaskStatusUpdateEvent(
     *         taskId = context.taskId,
     *         contextId = context.contextId,
     *         status = TaskStatus(
     *             state = TaskState.TASK_STATE_COMPLETED,
     *             message = Message(
     *                role = Role.ROLE_AGENT,
     *                contextId = context.contextId,
     *                taskId = context.taskId,
     *                parts = listOf(
     *                    TextPart("Task completed successfully!")
     *                )
     *            ),
     *            timestamp = Clock.System.now()
     *         ),
     *     )
     * )
     * ```
     *
     * @param context The context containing the necessary information and accessors for executing the agent.
     * @param eventProcessor The event processor to publish events to.
     * @throws Exception if something goes wrong during execution. Should prefer more specific exceptions when possible,
     * e.g., [A2AContentTypeNotSupportedException], [A2AUnsupportedOperationException], etc. See full list of available
     * A2A exceptions in [ai.koog.a2a.exceptions].
     */
    public suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: SessionEventProcessor)

    /**
     * Request to cancel a task.
     *
     * Must throw an exception if the cancellation fails or is impossible. The executor should attempt to stop the task
     * identified by the task id in the [context] or throw an exception if cancellation is not supported or not possible,
     * e.g., [A2ATaskNotCancelableException].
     *
     * Can also publish [TaskEvent]s to the [eventProcessor] to update the task state. The processor is bound to the
     * task's event stream: if a turn is running, it's the processor of that turn (which stays open until this method
     * returns, even after [agentJob] is cancelled), otherwise a new one; either way the events reach every stream of
     * the task. Should move the task to [TaskState.TASK_STATE_CANCELED]; if it returns normally while the task is
     * still not in a terminal state, the server cancels [agentJob] (if any) and sets [TaskState.TASK_STATE_CANCELED]
     * itself, so the task never stays non-terminal after a successful cancellation. If the task ends up in another
     * terminal state, the requester gets [A2ATaskNotCancelableException].
     *
     * **IMPORTANT**: This should execute quickly as it runs synchronously with the request.
     *
     * Default implementation throws [A2ATaskNotCancelableException], meaning cancellation is not supported by default.
     *
     * Example implementation:
     * ```kotlin
     * // Cancel agent execution job, if the agent is currently running, to terminate it.
     * agentJob?.cancelAndJoin()
     * // Send task cancellation event with custom message to event processor
     * eventProcessor.sendTaskEvent(
     *     TaskStatusUpdateEvent(
     *         taskId = context.taskId,
     *         contextId = context.contextId,
     *         status = TaskStatus(
     *             state = TaskState.TASK_STATE_CANCELED,
     *             message = Message(
     *                 role = Role.ROLE_AGENT,
     *                 taskId = context.taskId,
     *                 contextId = context.contextId,
     *                 parts = listOf(
     *                     TextPart("Task was canceled by the user")
     *                 )
     *             ),
     *             timestamp = Clock.System.now()
     *         ),
     *     )
     * )
     * ```
     *
     * @param context The context containing the necessary information and accessors for executing the agent.
     * @param eventProcessor The event processor to publish events to.
     * @param agentJob Optional job executing the agent logic, if the agent is currently running.
     * @throws Exception if something goes wrong during execution or the cancellation is impossible. Should prefer more
     * specific exceptions if possible, e.g., [A2ATaskNotCancelableException], [A2AUnsupportedOperationException], etc.
     * See full list of available A2A exceptions in [ai.koog.a2a.exceptions].
     */
    public suspend fun cancel(
        context: RequestContext<CancelTaskRequest>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?,
    ) {
        throw A2ATaskNotCancelableException("Cancellation is not supported")
    }
}
