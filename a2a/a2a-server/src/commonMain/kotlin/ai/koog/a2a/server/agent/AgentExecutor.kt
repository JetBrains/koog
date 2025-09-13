package ai.koog.a2a.server.agent

import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.session.RequestContext
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
     * @throws Exception if something goes wrong during execution. Should prefer more specific exceptions when available,
     * e.g., [A2AContentTypeNotSupportedException], [A2AUnsupportedOperationException], etc. See full list of available
     * A2A exceptions in [ai.koog.a2a.exceptions].
     */
    public suspend fun execute(context: RequestContext<MessageSendParams>, eventProcessor: SessionEventProcessor)

    /**
     * Request the agent to cancel an ongoing task.
     *
     * The agent should attempt to stop the task identified by the task id in the context and publish a [TaskStatusUpdateEvent] with state
     * [TaskState.Canceled] to the [eventProcessor].
     *
     * Can throw an exception if the agent fails to cancel the task.
     *
     * @throws Exception if something goes wrong during execution. Should prefer more specific exceptions when available,
     * e.g., [A2AContentTypeNotSupportedException], [A2AUnsupportedOperationException], etc. See full list of available
     * A2A exceptions in [ai.koog.a2a.exceptions].
     */
    public suspend fun cancel(context: RequestContext<TaskIdParams>, eventProcessor: SessionEventProcessor)
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
