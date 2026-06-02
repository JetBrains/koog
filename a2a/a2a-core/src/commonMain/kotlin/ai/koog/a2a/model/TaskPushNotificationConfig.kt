package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * A container associating a push notification configuration with a specific task.
 *
 * @property url The callback URL where the agent should send push notifications.
 * @property taskId The unique identifier (e.g. UUID) of the task.
 * @property id A unique identifier (e.g. UUID) for the push notification configuration, set by the client to support multiple notification callbacks.
 * @property token A unique token for this task or session to validate incoming push notifications.
 * @property authentication Authentication details for the agent to use when calling the notification URL.
 * @property tenant Optional. Opaque routing identifier. Must match the [AgentInterface.tenant] value from
 * the selected [AgentInterface] in the Agent Card when that field is set.
 */
@Serializable
public data class TaskPushNotificationConfig(
    public val url: String,
    public val taskId: String,
    public val id: String? = null,
    public val token: String? = null,
    public val authentication: AuthenticationInfo? = null,
    override val tenant: String? = null,
) : Request

/**
 * Defines authentication details for a push notification endpoint.
 *
 * @property schemes A list of supported authentication schemes (e.g., 'Basic', 'Bearer').
 * @property credentials Optional credentials required by the push notification endpoint.
 */
@Serializable
public data class AuthenticationInfo(
    public val schemes: List<String>,
    public val credentials: String? = null,
)
