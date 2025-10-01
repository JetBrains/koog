package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.Task

/**
 * Interface for sending push notifications.
 *
 * [More info on push notifications in specification](https://a2a-protocol.org/latest/specification/#95-push-notification-setup-and-usage)
 */
public interface PushNotificationSender {
    public companion object {
        /**
         * Represents a custom optional HTTP header used to include a token for authenticating A2A notifications.
         */
        public const val A2A_NOTIFICATION_TOKEN_HEADER: String = "X-A2A-Notification-Token"
    }

    /**
     * Sends a push notification.
     *
     * @param config Push notification configuration.
     * @param task Task object to send in the notification.
     */
    public suspend fun send(config: PushNotificationConfig, task: Task)
}
