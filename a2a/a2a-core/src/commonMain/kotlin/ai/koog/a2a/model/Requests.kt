package ai.koog.a2a.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Base interface for request parameters for A2A methods.
 */
public sealed interface Request {
    /**
     * Opaque routing identifier. Must match the [AgentInterface.tenant] value from
     * the selected [AgentInterface] in the Agent Card when that field is set.
     */
    public val tenant: String?
}

/**
 * Represents a request for the `SendMessage` method.
 *
 * @property message The message object being sent to the agent.
 * @property configuration Optional configuration for the send request.
 * @property metadata Optional metadata for extensions.
 */
@Serializable
public data class SendMessageRequest(
    public val message: Message,
    public val configuration: SendMessageConfiguration? = null,
    public val metadata: JsonObject? = null,
    override val tenant: String? = null,
) : Request

/**
 * Configuration of a send message request.
 *
 * @property acceptedOutputModes A list of output MIME types the client is prepared to accept in the response.
 * @property historyLength The number of most recent messages from the task's history to retrieve in the response.
 * @property taskPushNotificationConfig Configuration for the agent to send push notifications for updates after the initial response.
 * @property blocking If true, the client will wait for the task to complete. The server may reject this if the task is long-running.
 * @property returnImmediately If `true`, the operation returns immediately after creating the task, even if processing is still in progress.
 */
@Serializable
public data class SendMessageConfiguration(
    public val blocking: Boolean? = null,
    public val acceptedOutputModes: List<String>? = null,
    public val historyLength: Int? = null,
    public val taskPushNotificationConfig: TaskPushNotificationConfig? = null,
    public val returnImmediately: Boolean? = null,
)

/**
 * Represents a request for the `GetTask` method.
 *
 * @property id The resource ID of the task to retrieve.
 * @property historyLength The maximum number of most recent messages from the task's history to retrieve.
 * An unset value means the client does not impose any limit. A value of zero requests that no messages are included.
 * The server must not return more messages than the provided value, but may apply a lower limit.
 */
@Serializable
public data class GetTaskRequest(
    public val id: String,
    public val historyLength: Int? = null,
    override val tenant: String? = null,
) : Request


/**
 * Represents a request for the `ListTasks` method.
 *
 * @property contextId Filter tasks by context ID to get tasks from a specific conversation or session.
 * @property status Filter tasks by their current status state.
 * @property pageSize The maximum number of tasks to return. The service may return fewer than this value.
 * If unspecified, at most 50 tasks will be returned. The minimum value is 1, and the maximum value is 100.
 * @property pageToken A page token received from a previous `ListTasks` call. Provide this to retrieve the subsequent page.
 * @property historyLength The maximum number of messages to include in each task's history.
 * @property statusTimestampAfter Filter tasks whose status was updated at or after the provided timestamp.
 * @property includeArtifacts Whether to include artifacts in the returned tasks. Defaults to false to reduce payload size.
 */
@Serializable
public data class ListTasksRequest(
    public val contextId: String? = null,
    public val status: TaskState? = null,
    public val pageSize: Int? = null,
    public val pageToken: String? = null,
    public val historyLength: Int? = null,
    public val statusTimestampAfter: Instant? = null,
    public val includeArtifacts: Boolean? = null,
    override val tenant: String? = null,
) : Request

/**
 * Represents a request for the `CancelTask` method.
 *
 * @property id The resource ID of the task to cancel.
 * @property metadata A flexible key-value map for passing additional context.
 */
@Serializable
public data class CancelTaskRequest(
    public val id: String,
    public val metadata: JsonObject? = null,
    override val tenant: String? = null,
) : Request

/**
 * Represents a request for the `GetTaskPushNotificationConfig` method.
 *
 * @property taskId The parent task resource ID.
 * @property id The resource ID of the configuration to retrieve.
 */
@Serializable
public data class GetTaskPushNotificationConfigRequest(
    public val taskId: String,
    public val id: String,
    override val tenant: String? = null,
) : Request

/**
 * Represents a request for the `DeleteTaskPushNotificationConfig` method.
 *
 * @property taskId The parent task resource ID.
 * @property id The resource ID of the configuration to delete.
 */
@Serializable
public data class DeleteTaskPushNotificationConfigRequest(
    public val taskId: String,
    public val id: String,
    override val tenant: String? = null,
) : Request

/**
 * Represents a request for the `SubscribeToTask` method.
 *
 * @property id The resource ID of the task to subscribe to.
 */
@Serializable
public data class SubscribeToTaskRequest(
    public val id: String,
    override val tenant: String? = null,
) : Request

/**
 * Represents a request for the `ListTaskPushNotificationConfigs` method.
 *
 * @property taskId The parent task resource ID.
 * @property pageSize The maximum number of configurations to return.
 * @property pageToken A page token received from a previous `ListTaskPushNotificationConfigs` call.
 */
@Serializable
public data class ListTaskPushNotificationConfigsRequest(
    public val taskId: String,
    public val pageSize: Int? = null,
    public val pageToken: String? = null,
    override val tenant: String? = null,
) : Request

/**
 * Represents a request for the `GetExtendedAgentCard` method.
 */
@Serializable
public data class GetExtendedAgentCardRequest(
    override val tenant: String? = null,
) : Request
