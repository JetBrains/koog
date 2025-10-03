package ai.koog.a2a.server.notifications

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.utils.RWLock

/**
 * In-memory implementation of [PushNotificationConfigStorage] using a thread-safe map.
 *
 * This implementation stores push notification configurations in memory grouped by task ID
 * and provides concurrency safety through read-write locks.
 */
@OptIn(InternalA2AApi::class)
public class InMemoryPushNotificationConfigStorage : PushNotificationConfigStorage {
    private val configsByTaskId = mutableMapOf<String, MutableMap<String?, PushNotificationConfig>>()
    private val rwLock = RWLock()

    override suspend fun save(taskId: String, pushNotificationConfig: PushNotificationConfig): Unit =
        rwLock.withWriteLock {
            val configId = pushNotificationConfig.id
            val taskConfigs = configsByTaskId.getOrPut(taskId) { mutableMapOf() }

            taskConfigs[configId] = pushNotificationConfig
        }

    override suspend fun getAll(taskId: String): List<PushNotificationConfig> = rwLock.withReadLock {
        configsByTaskId[taskId]?.values?.toList() ?: emptyList()
    }

    override suspend fun get(taskId: String, configId: String?): PushNotificationConfig? = rwLock.withReadLock {
        configsByTaskId[taskId]?.get(configId)
    }

    override suspend fun delete(taskId: String, configId: String?): Unit = rwLock.withWriteLock {
        if (configId == null) {
            // Delete all configurations for the task
            configsByTaskId.remove(taskId)
        } else {
            configsByTaskId[taskId]?.let { taskConfigs ->
                // Delete specific configuration
                taskConfigs.remove(configId)

                // Clean up empty task entry
                if (taskConfigs.isEmpty()) {
                    configsByTaskId.remove(taskId)
                }
            }
        }
    }
}
