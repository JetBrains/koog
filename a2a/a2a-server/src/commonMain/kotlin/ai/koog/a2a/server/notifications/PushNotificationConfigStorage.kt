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
 * Every method but [getAll] takes the [ServerCallContext] of the call it is made for, last, so multi-tenant or
 * authorization-aware storages can scope the data (e.g. by an owner in [ServerCallContext.state], and by
 * [ServerCallContext.tenant]). The first four methods serve the push notification config RPCs; [deleteAll] is internal
 * cleanup. [getAll] is the one read without a caller: the server dispatches notifications with it, after the configs
 * were authorized when they were saved.
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
     * @param callContext the context of the call
     * @return the config as it was stored
     * @throws PushNotificationException if the configuration cannot be saved
     */
    public suspend fun save(
        config: TaskPushNotificationConfig,
        callContext: ServerCallContext
    ): TaskPushNotificationConfig

    /**
     * Serves the `GetTaskPushNotificationConfig` RPC: retrieves a config by [GetTaskPushNotificationConfigRequest.taskId]
     * and [GetTaskPushNotificationConfigRequest.id].
     *
     * @param request the `GetTaskPushNotificationConfig` request
     * @param callContext the context of the call
     * @return the config, or `null` if it does not exist
     */
    public suspend fun get(
        request: GetTaskPushNotificationConfigRequest,
        callContext: ServerCallContext
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
     * @param callContext the context of the call
     * @return the configs of the task (an empty list if there are none)
     */
    public suspend fun list(
        request: ListTaskPushNotificationConfigsRequest,
        callContext: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse

    /**
     * Serves the `DeleteTaskPushNotificationConfig` RPC: deletes a config.
     *
     * Idempotent: deleting a config that does not exist is a no-op and never an error.
     *
     * @param request the `DeleteTaskPushNotificationConfig` request
     * @param callContext the context of the call
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
     * Deletes all push notification configurations of the given task ID, for internal cleanup. A no-op if there are none.
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
