package ai.koog.a2a.transport.jsonrpc

/**
 * A2A JSON-RPC methods.
 */
public enum class A2AMethod(
    public val value: String,
    public val streaming: Boolean = false
) {
    GetAuthenticatedExtendedAgentCard("agent/getAuthenticatedExtendedCard"),
    SendMessage("message/send"),
    SendMessageStreaming("message/stream", streaming = true),
    GetTask("tasks/get"),
    CancelTask("tasks/cancel"),
    ResubscribeTask("tasks/resubscribe", streaming = true),
    SetTaskPushNotificationConfig("tasks/pushNotificationConfig/set"),
    GetTaskPushNotificationConfig("tasks/pushNotificationConfig/get"),
    ListTaskPushNotificationConfig("tasks/pushNotificationConfig/list"),
    DeleteTaskPushNotificationConfig("tasks/pushNotificationConfig/delete"),
}
