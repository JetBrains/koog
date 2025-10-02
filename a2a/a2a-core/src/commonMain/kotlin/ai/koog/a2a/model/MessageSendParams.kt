package ai.koog.a2a.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Defines the parameters for a request to send a message to an agent. This can be used
 * to create a new task, continue an existing one, or restart a task.
 *
 * @property message The message object being sent to the agent.
 * @property configuration Optional configuration for the send request.
 * @property metadata Optional metadata for extensions.
 */
@Serializable
public data class MessageSendParams(
    public val message: Message,
    public val configuration: MessageSendConfiguration? = null,
    public val metadata: JsonObject? = null,
)

/**
 * Defines configuration options for a `message/send` or `message/stream` request.
 *
 * @property acceptedOutputModes A list of output MIME types the client is prepared to accept in the response.
 * @property historyLength The number of most recent messages from the task's history to retrieve in the response.
 * @property pushNotificationConfig Configuration for the agent to send push notifications for updates after the initial response.
 * @property blocking If true, the client will wait for the task to complete. The server may reject this if the task is long-running.
 */
@Serializable
public data class MessageSendConfiguration(
    public val blocking: Boolean? = null,
    public val acceptedOutputModes: List<String>? = null,
    public val historyLength: Int? = null,
    public val pushNotificationConfig: PushNotificationConfig? = null,
)
