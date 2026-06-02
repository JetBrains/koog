package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * Base interface for response objects for A2A methods.
 */
public sealed interface Response

/**
 * Result object for `ListTasks` method.
 *
 * @property tasks Array of tasks matching the specified criteria.
 * @property nextPageToken A token to retrieve the next page of results, or empty if there are no more results in the list.
 * @property pageSize The page size used for this response.
 * @property totalSize Total number of tasks available (before pagination).
 */
@Serializable
public data class ListTasksResponse(
    public val tasks: List<Task>,
    public val nextPageToken: String,
    public val pageSize: Int,
    public val totalSize: Int,
) : Response

/**
 * Result object for `ListTaskPushNotificationConfigs` method.
 *
 * @property configs The list of push notification configurations.
 * @property nextPageToken A token to retrieve the next page of results, or empty if there are no more results in the list.
 */
@Serializable
public data class ListTaskPushNotificationConfigsResponse(
    public val configs: List<TaskPushNotificationConfig>,
    public val nextPageToken: String,
) : Response
