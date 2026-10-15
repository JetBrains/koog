package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.TaskPushNotificationConfig

/**
 * Interface for sending push notifications to a single webhook.
 *
 * The caller (the server) is responsible for loading the push notification configs of a task and calling [send]
 * once per config. Implementations only deliver the notification.
 *
 * [More info on push notifications in specification](https://a2a-protocol.org/v1.0.1/specification/#43-push-notification-delivery)
 */
public interface PushNotificationSender {
    /**
     * Sends a push notification to the webhook described by [config].
     *
     * The payload of the notification is the [event] wrapped as a `StreamResponse`, i.e. serialized the same way
     * as events of the streaming operations (for example, `{"task": {...}}`).
     *
     * @param config Push notification configuration.
     * @param event Event to send in the notification.
     */
    public suspend fun send(config: TaskPushNotificationConfig, event: Event)
}
