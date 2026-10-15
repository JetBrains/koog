package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.server.exceptions.PushNotificationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Interface for managing the storage of push notification configurations associated with task updates.
 *
 * The first four methods serve the push notification config RPCs and receive the [ServerCallContext] of the call, so
 * multi-tenant or authorization-aware storages can scope the data. [getAll] and [deleteAll] are internal (dispatching
 * notifications, cleanup) and have no caller context.
 *
 * [ai.koog.a2a.server.A2AServer] validates task existence and config presence, mapping them to `TaskNotFound`.
 * Storages only report absence (`null`, empty list, no-op).
 *
 * Implementations must ensure concurrency safety.
 */
public interface PushNotificationConfigStorage {
    /**
     * Serves the `CreateTaskPushNotificationConfig` RPC: saves (creates or replaces) a push notification config.
     *
     * Precondition, enforced by [ai.koog.a2a.server.A2AServer]: [TaskPushNotificationConfig.taskId] and
     * [TaskPushNotificationConfig.id] are non-null and non-blank. Implementations may rely on it.
     *
     * Contract: upsert by the pair ([TaskPushNotificationConfig.taskId], [TaskPushNotificationConfig.id]).
     * The config is persisted as given, including [TaskPushNotificationConfig.tenant], so that it can be returned
     * unchanged from [get] and [list].
     *
     * @param config the config to save
     * @param context the context of the call
     * @return the config as it was stored
     * @throws PushNotificationException if the configuration cannot be saved
     */
    public suspend fun save(config: TaskPushNotificationConfig, context: ServerCallContext): TaskPushNotificationConfig

    /**
     * Serves the `GetTaskPushNotificationConfig` RPC: retrieves a config by [GetTaskPushNotificationConfigRequest.taskId]
     * and [GetTaskPushNotificationConfigRequest.id].
     *
     * @param request the `GetTaskPushNotificationConfig` request
     * @param context the context of the call
     * @return the config, or `null` if it does not exist
     */
    public suspend fun get(
        request: GetTaskPushNotificationConfigRequest,
        context: ServerCallContext
    ): TaskPushNotificationConfig?

    /**
     * Serves the `ListTaskPushNotificationConfigs` RPC: returns the configs of [ListTaskPushNotificationConfigsRequest.taskId].
     *
     * Pagination is optional (`MAY`) in the specification. A storage without it may ignore
     * [ListTaskPushNotificationConfigsRequest.pageSize] and [ListTaskPushNotificationConfigsRequest.pageToken], return
     * all configs and leave [ListTaskPushNotificationConfigsResponse.nextPageToken] empty (no more results). A storage
     * with pagination must return a non-empty `nextPageToken` if and only if more results exist.
     *
     * @param request the `ListTaskPushNotificationConfigs` request
     * @param context the context of the call
     * @return the configs of the task (an empty list if there are none)
     */
    public suspend fun list(
        request: ListTaskPushNotificationConfigsRequest,
        context: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse

    /**
     * Serves the `DeleteTaskPushNotificationConfig` RPC: deletes a config.
     *
     * Idempotent: deleting a config that does not exist is a no-op and never an error.
     *
     * @param request the `DeleteTaskPushNotificationConfig` request
     * @param context the context of the call
     * @throws PushNotificationException if the configuration cannot be deleted
     */
    public suspend fun delete(request: DeleteTaskPushNotificationConfigRequest, context: ServerCallContext)

    /**
     * Retrieves all push notification configurations of the given task ID, for internal use when notifications are
     * dispatched (there is no caller request then).
     *
     * @param taskId Task ID for which to retrieve the configurations.
     * @return all configs of the task (an empty list if there are none)
     */
    public suspend fun getAll(taskId: String): List<TaskPushNotificationConfig>

    /**
     * Deletes all push notification configurations of the given task ID, for internal cleanup. A no-op if there are none.
     *
     * @param taskId Task ID for which to delete the configurations.
     * @throws PushNotificationException if the configurations cannot be deleted.
     */
    public suspend fun deleteAll(taskId: String)
}
