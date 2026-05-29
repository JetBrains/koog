package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.Task

/**
 * Interface for sending push notifications.
 *
 * [More info on push notifications in specification](https://a2a-protocol.org/v0.3.0/specification/#95-push-notification-setup-and-usage)
 */
public interface PushNotificationSender {
    /**
     * Sends a push notification.
     *
     * @param config Push notification configuration.
     * @param task Task object to send in the notification.
     */
    public suspend fun send(config: PushNotificationConfig, task: Task)
}
