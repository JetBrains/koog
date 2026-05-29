package ai.koog.a2a.consts

/**
 * Common A2A protocol headers and constants.
 */
public object A2AHeaders {
    /**
     * HTTP header name for A2A protocol version.
     * Used to communicate the protocol version that the client is using.
     */
    public const val A2A_VERSION: String = "A2A-Version"

    /**
     * HTTP header name for A2A extensions.
     * Used to communicate which extensions are requested by the client.
     */
    public const val A2A_EXTENSIONS: String = "A2A-Extensions"

    /**
     * HTTP header name for a push notification token.
     */
    public const val X_A2A_NOTIFICATION_TOKEN: String = "X-A2A-Notification-Token"
}
