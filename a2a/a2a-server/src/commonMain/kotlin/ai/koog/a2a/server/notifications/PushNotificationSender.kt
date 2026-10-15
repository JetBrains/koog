package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig

/**
 * Interface for sending push notifications.
 *
 * [More info on push notifications in specification](https://a2a-protocol.org/v1.0.1/specification/#43-push-notification-delivery)
 */
public interface PushNotificationSender {
    /**
     * Sends a push notification.
     *
     * @param config Push notification configuration.
     * @param task Task object to send in the notification.
     */
    public suspend fun send(config: TaskPushNotificationConfig, task: Task)
}
