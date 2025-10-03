package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.server.exceptions.PushNotificationException

/**
 * Interface for managing the storage of push notification configurations associated with task updates.
 *
 * Implementations must ensure concurrency safety.
 */
public interface PushNotificationConfigStorage {
    /**
     * Saves a push notification configuration for a specified task ID.
     *
     * @param taskId Task ID for which to save the configration.
     * @param pushNotificationConfig Config instance containing the details of the notification setup.
     * @throws PushNotificationException if the configuration cannot be saved.
     */
    public suspend fun save(taskId: String, pushNotificationConfig: PushNotificationConfig)

    /**
     * Retrieves a push notification configuration for a specified task ID and configuration ID.
     * @param taskId Task ID for which to retrieve the configuration.
     * @param configId Configuration ID for which to retrieve the configuration.
     */
    public suspend fun get(taskId: String, configId: String?): PushNotificationConfig?

    /**
     * Retrieves all push notification configurations associated with the given task ID.
     *
     * @param taskId Task ID for which to retrieve the configurations.
     */
    public suspend fun getAll(taskId: String): List<PushNotificationConfig>

    /**
     * Deletes all push notification configurations for a specified task ID, optionally deleting a specific configuration instead.
     *
     * @param taskId Task ID for which to delete the configurations.
     * @param configId Optional configuration ID to delete. Defaults to `null`, meaning all configurations for the task will be deleted.
     * @throws PushNotificationException if the configuration cannot be deleted.
     */
    public suspend fun delete(taskId: String, configId: String? = null)
}
