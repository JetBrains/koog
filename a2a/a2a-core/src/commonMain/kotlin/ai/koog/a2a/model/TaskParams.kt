package ai.koog.a2a.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Defines parameters containing a task ID, used for simple task operations.
 *
 * @property id The unique identifier (e.g. UUID) of the task.
 * @property metadata Optional metadata associated with this request.
 */
@Serializable
public data class TaskIdParams(
    public val id: String,
    public val metadata: JsonObject? = null,
)

/**
 * Defines parameters for querying a task, with an option to limit history length.
 *
 * @property id The unique identifier (e.g. UUID) of the task.
 * @property historyLength The number of most recent messages from the task's history to retrieve.
 * @property metadata Optional metadata associated with this request.
 */
@Serializable
public data class TaskQueryParams(
    public val id: String,
    public val historyLength: Int? = null,
    public val metadata: JsonObject? = null,
)

/**
 * Defines parameters for fetching a specific push notification configuration for a task.
 *
 * @property id The unique identifier (e.g. UUID) of the task.
 * @property pushNotificationConfigId The ID of the push notification configuration to retrieve.
 * @property metadata Optional metadata associated with this request.
 */
@Serializable
public data class TaskPushNotificationConfigParams(
    public val id: String,
    public val pushNotificationConfigId: String? = null,
    public val metadata: JsonObject? = null,
)
