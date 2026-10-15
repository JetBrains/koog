package ai.koog.a2a.server.notifications

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.agents.lock.RWLock

/**
 * In-memory implementation of [PushNotificationConfigStorage] using a thread-safe map.
 *
 * Configs are keyed by (task ID, config ID) and persisted as given, including their tenant. [list] doesn't paginate:
 * it ignores `pageSize` and `pageToken`, returns all configs of the task in insertion order, and leaves `nextPageToken`
 * empty. The [ServerCallContext] and the tenant are ignored. Concurrency safety is provided by a read-write lock.
 */
@OptIn(InternalA2AApi::class)
public class InMemoryPushNotificationConfigStorage : PushNotificationConfigStorage {
    private data class Key(val taskId: String, val configId: String)

    private val configs = linkedMapOf<Key, TaskPushNotificationConfig>()
    private val rwLock = RWLock()

    override suspend fun save(
        config: TaskPushNotificationConfig,
        context: ServerCallContext
    ): TaskPushNotificationConfig = rwLock.withWriteLock {
        val taskId = requireNotNull(config.taskId) { "Task id is required to save a push notification config" }
        val configId = requireNotNull(config.id) { "Config id is required to save a push notification config" }

        configs[Key(taskId, configId)] = config
        config
    }

    override suspend fun get(
        request: GetTaskPushNotificationConfigRequest,
        context: ServerCallContext
    ): TaskPushNotificationConfig? = rwLock.withReadLock {
        configs[Key(request.taskId, request.id)]
    }

    override suspend fun list(
        request: ListTaskPushNotificationConfigsRequest,
        context: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse = ListTaskPushNotificationConfigsResponse(
        configs = getAll(request.taskId)
    )

    override suspend fun delete(
        request: DeleteTaskPushNotificationConfigRequest,
        context: ServerCallContext
    ) {
        rwLock.withWriteLock {
            configs.remove(Key(request.taskId, request.id))
        }
    }

    override suspend fun getAll(taskId: String): List<TaskPushNotificationConfig> = rwLock.withReadLock {
        configs.filterKeys { it.taskId == taskId }.values.toList()
    }

    override suspend fun deleteAll(taskId: String) {
        rwLock.withWriteLock {
            configs.keys.removeAll { it.taskId == taskId }
        }
    }
}
