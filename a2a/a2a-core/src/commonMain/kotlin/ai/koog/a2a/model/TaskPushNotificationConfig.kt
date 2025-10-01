package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * A container associating a push notification configuration with a specific task.
 *
 * @property taskId The unique identifier (e.g. UUID) of the task.
 * @property pushNotificationConfig The push notification configuration for this task.
 */
@Serializable
public data class TaskPushNotificationConfig(
    public val taskId: String,
    public val pushNotificationConfig: PushNotificationConfig,
)

/**
 * Defines the configuration for setting up push notifications for task updates.
 *
 * @property id A unique identifier (e.g. UUID) for the push notification configuration, set by the client to support multiple notification callbacks.
 * @property url The callback URL where the agent should send push notifications.
 * @property token A unique token for this task or session to validate incoming push notifications.
 * @property authentication Optional authentication details for the agent to use when calling the notification URL.
 */
@Serializable
public data class PushNotificationConfig(
    public val id: String? = null,
    public val url: String,
    public val token: String? = null,
    public val authentication: PushNotificationAuthenticationInfo? = null,
)

/**
 * Defines authentication details for a push notification endpoint.
 *
 * @property schemes A list of supported authentication schemes (e.g., 'Basic', 'Bearer').
 * @property credentials Optional credentials required by the push notification endpoint.
 */
@Serializable
public data class PushNotificationAuthenticationInfo(
    public val schemes: List<String>,
    public val credentials: String? = null,
)
