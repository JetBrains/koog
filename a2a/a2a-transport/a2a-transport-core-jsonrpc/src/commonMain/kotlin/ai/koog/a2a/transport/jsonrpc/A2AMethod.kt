package ai.koog.a2a.transport.jsonrpc

/**
 * A2A JSON-RPC methods.
 */
public enum class A2AMethod(public val value: String) {
    GetAuthenticatedExtendedAgentCard("agent/getAuthenticatedExtendedCard"),
    SendMessage("message/send"),
    SendMessageStreaming("message/stream"),
    GetTask("tasks/get"),
    CancelTask("tasks/cancel"),
    SetTaskPushNotificationConfig("tasks/pushNotificationConfig/set"),
    GetTaskPushNotificationConfig("tasks/pushNotificationConfig/get"),
    ListTaskPushNotificationConfig("tasks/pushNotificationConfig/list"),
    DeleteTaskPushNotificationConfig("tasks/pushNotificationConfig/delete"),
}
