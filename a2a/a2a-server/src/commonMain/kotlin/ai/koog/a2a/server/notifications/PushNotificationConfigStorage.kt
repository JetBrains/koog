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
 * Scoping: multi-tenant or authorization-aware storages may scope methods by the call context, e.g. by an owner
 * the server's subclass puts into [ServerCallContext.state], and by [ServerCallContext.tenant], which the transport
 * sets from the request . A task that is not visible to the caller must be reported as not found.
 *
 * [ai.koog.a2a.server.A2AServer] checks that the task exists and is visible to the caller, and maps a missing config
 * to `TaskNotFound`. Storages only report absence (`null`, empty list, no-op).
 *
 * Implementations must ensure concurrency safety.
 */
public interface PushNotificationConfigStorage {
    /**
     * Serves the `CreateTaskPushNotificationConfig` RPC, and saves the config sent inline in SendMessage: creates
     * the config, or replaces the one with the same [TaskPushNotificationConfig.taskId] and
     * [TaskPushNotificationConfig.id].
     *
     * [ai.koog.a2a.server.A2AServer] has set both to non-blank values, assigning the id if the client didn't. The
     * config is stored as given, [TaskPushNotificationConfig.tenant] included, so that [get] and [list] return it
     * unchanged.
     *
     * @param config the config to save
     * @param callContext the context of the call, see [PushNotificationConfigStorage]
     * @return the config as it was stored
     * @throws PushNotificationException if the configuration cannot be saved
     */
    public suspend fun save(
        config: TaskPushNotificationConfig,
        callContext: ServerCallContext
    ): TaskPushNotificationConfig

    /**
     * Serves the `GetTaskPushNotificationConfig` RPC.
     *
     * @param request the `GetTaskPushNotificationConfig` request
     * @param callContext the context of the call, see [PushNotificationConfigStorage]
     * @return the config, or `null` if it doesn't exist or isn't visible to [callContext]
     */
    public suspend fun get(
        request: GetTaskPushNotificationConfigRequest,
        callContext: ServerCallContext
    ): TaskPushNotificationConfig?

    /**
     * Serves the `ListTaskPushNotificationConfigs` RPC: returns the configs of the task visible to [callContext].
     *
     * Paging is up to the storage. Without it, ignore [ListTaskPushNotificationConfigsRequest.pageSize] and
     * [ListTaskPushNotificationConfigsRequest.pageToken], and return all configs with an empty
     * [ListTaskPushNotificationConfigsResponse.nextPageToken]. With it, page tokens are opaque and storage-defined, and
     * `nextPageToken` is non-empty if and only if more results exist.
     *
     * @param request the `ListTaskPushNotificationConfigs` request
     * @param callContext the context of the call, see [PushNotificationConfigStorage]
     * @return the configs of the task (an empty list if there are none)
     */
    public suspend fun list(
        request: ListTaskPushNotificationConfigsRequest,
        callContext: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse

    /**
     * Serves the `DeleteTaskPushNotificationConfig` RPC. Idempotent: deleting a config that doesn't exist is a no-op.
     *
     * @param request the `DeleteTaskPushNotificationConfig` request
     * @param callContext the context of the call, see [PushNotificationConfigStorage]
     * @throws PushNotificationException if the configuration cannot be deleted
     */
    public suspend fun delete(request: DeleteTaskPushNotificationConfigRequest, callContext: ServerCallContext)

    /**
     * Retrieves all push notification configurations of the given task ID, for internal use when notifications are
     * dispatched. The one read without a caller context: it returns the configs of every caller, since one task's
     * notifications carry the events of every turn and caller.
     *
     * @param taskId Task ID for which to retrieve the configurations.
     * @return all configs of the task (an empty list if there are none)
     */
    public suspend fun getAll(taskId: String): List<TaskPushNotificationConfig>

    /**
     * Deletes all push notification configurations of the given task ID, for internal cleanup. A no-op if there are
     * none.
     *
     * The server calls it with the context of the turn that saved the configs inline, when the turn ends without
     * creating its task; an authorization-aware storage may delete only the configs saved with that context.
     *
     * @param taskId Task ID for which to delete the configurations.
     * @param callContext the context of the call the configurations are deleted for
     * @throws PushNotificationException if the configurations cannot be deleted.
     */
    public suspend fun deleteAll(taskId: String, callContext: ServerCallContext)
}
