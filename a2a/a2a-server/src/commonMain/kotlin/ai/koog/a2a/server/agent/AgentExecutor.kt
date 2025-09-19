package ai.koog.a2a.server.agent

import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.Session
import ai.koog.a2a.server.session.SessionEventProcessor
import kotlin.jvm.JvmName

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
     * yields control (e.g., enters an [TaskState.InputRequired] state).
     *
     * Can throw an exception if the input is invalid or the agent fails to execute the request.
     *
     * @param context The context containing the necessary information and accessors for executing the agent.
     * @param eventProcessor The event processor to publish events to.
     * @throws Exception if something goes wrong during execution. Should prefer more specific exceptions when possible,
     * e.g., [A2AContentTypeNotSupportedException], [A2AUnsupportedOperationException], etc. See full list of available
     * A2A exceptions in [ai.koog.a2a.exceptions].
     */
    public suspend fun execute(context: RequestContext<MessageSendParams>, eventProcessor: SessionEventProcessor)

    /**
     * Request to cancel an ongoing task in the running [session].
     *
     * The executor should attempt to stop the task identified by the task id in the [context] or throw an exception if
     * cancellation is not supported or not possible, e.g. [A2ATaskNotCancelableException].
     *
     * If this method finishes normally, it will be considered successful cancellation and the [session] will be explicitly closed.
     * This means the agent execution job (the code running in the [execute]) will be canceled, and
     * [SessionEventProcessor] associated with this session will be closed.
     *
     * Implementations can call [Session.close] explicitly themselves if they want to stop the agent execution first and
     * then perform some cleanup afterwards, e.g., closing connection to external resources.
     *
     * Must throw an exception if the cancellation fails or is impossible.
     *
     * Default implementation does nothing, meaning cancellations will always be successful and the [session] will be closed
     * immediately.
     *
     * Example simple implementation:
     * ```kotlin
     * // Explicitly close the session to stop the agent execution job and event processor
     * session.close()
     * // Log the fact that the task was canceled
     * log.info("Task '${context.taskId}' canceled")
     * ```
     *
     * Example more advanced implementation:
     * ```kotlin
     * // Cancel only the agent execution job to terminate the agent run, but keep event processor running.
     * session.agentJob.cancel()
     * // Send task cancellation event with custom message to event processor
     * session.eventProcessor.sendTaskEvent(
     *     TaskStatusUpdateEvent(
     *         taskId = context.taskId,
     *         contextId = context.contextId,
     *         status = TaskStatus(
     *             state = TaskState.Canceled,
     *             message = Message(
     *                 role = Role.Agent,
     *                 taskId = context.taskId,
     *                 contextId = context.contextId,
     *                 parts = listOf(
     *                     TextPart("Task was canceled by the user")
     *                 )
     *             )
     *         ),
     *         final = true,
     *     )
     * )
     * // Close the session completely
     * session.close()
     * ```
     *
     * @throws Exception if something goes wrong during execution or the cancellation is impossible. Should prefer more
     * specific exceptions when available, e.g., [A2ATaskNotCancelableException], [A2AUnsupportedOperationException], etc.
     * See full list of available A2A exceptions in [ai.koog.a2a.exceptions].
     */
    public suspend fun cancel(context: RequestContext<TaskIdParams>, session: Session) {}
}

/**
 * Returns the task id from the [MessageSendParams] in the [RequestContext].
 */
@get:JvmName("getMessageTaskId")
public val RequestContext<MessageSendParams>.taskId: String? get() = params.message.taskId

/**
 * Returns the task id from the [TaskIdParams] in the [RequestContext].
 */
@get:JvmName("getTaskIdParamsTaskId")
public val RequestContext<TaskIdParams>.taskId: String get() = params.id
