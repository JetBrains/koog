package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.server.exceptions.PushNotificationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Storage of push notification configs.
 *
 * - Scoping: storages may scope every method that takes a [ServerCallContext] by it, e.g. by an owner in
 *   [ServerCallContext.state] and by [ServerCallContext.tenant]. A config not visible to the caller is reported as
 *   absent.
 * - [getAll] is not scoped: notifications are dispatched with it to the configs of every caller.
 * - [ai.koog.a2a.server.A2AServer] checks the task before the RPC methods and reports absence as `TaskNotFound`, so
 *   storages only report absence (`null`, empty list, no-op).
 *
 * Implementations must ensure concurrency safety.
 */
public interface PushNotificationConfigStorage {
    /**
     * Serves `CreateTaskPushNotificationConfig`, and saves the config sent inline in SendMessage when its turn starts.
     * Creates the config, or replaces the task's config with the same id; the server has set the task id and the id.
     *
     * @param config the config to save
     * @param callContext the context of the call
     * @return the config as stored, which [get] and [list] return unchanged
     * @throws PushNotificationException if the config can't be saved
     */
    public suspend fun save(
        config: TaskPushNotificationConfig,
        callContext: ServerCallContext
    ): TaskPushNotificationConfig

    /**
     * Serves `GetTaskPushNotificationConfig`.
     *
     * @param request the config to read
     * @param callContext the context of the call
     * @return the config, or `null` if it doesn't exist or isn't visible to [callContext]
     */
    public suspend fun get(
        request: GetTaskPushNotificationConfigRequest,
        callContext: ServerCallContext
    ): TaskPushNotificationConfig?

    /**
     * Serves `ListTaskPushNotificationConfigs`.
     *
     * @param request the task and the page of its configs
     * @param callContext the context of the call
     * @return the configs of the task visible to [callContext]
     */
    public suspend fun list(
        request: ListTaskPushNotificationConfigsRequest,
        callContext: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse

    /**
     * Serves `DeleteTaskPushNotificationConfig`. A missing config is a no-op.
     *
     * @param request the config to delete
     * @param callContext the context of the call
     * @throws PushNotificationException if the config can't be deleted
     */
    public suspend fun delete(request: DeleteTaskPushNotificationConfigRequest, callContext: ServerCallContext)

    /**
     * The dispatch read, not scoped.
     *
     * @param taskId the task ID
     * @return the configs of [taskId] saved by every caller
     */
    public suspend fun getAll(taskId: String): List<TaskPushNotificationConfig>

    /**
     * Deletes the configs of [taskId] when a turn that saved one inline ends without creating its task. A no-op if
     * there are none.
     *
     * @param taskId the task ID
     * @param callContext the context of that turn; an owner-aware storage may delete only the configs saved with it
     * @throws PushNotificationException if the configs can't be deleted
     */
    public suspend fun deleteAll(taskId: String, callContext: ServerCallContext)
}
