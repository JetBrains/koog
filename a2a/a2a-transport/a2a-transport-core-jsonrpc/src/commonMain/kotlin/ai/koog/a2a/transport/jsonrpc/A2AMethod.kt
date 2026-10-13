package ai.koog.a2a.transport.jsonrpc

/**
 * A2A JSON-RPC methods.
 */
public enum class A2AMethod(
    public val value: String,
    public val streaming: Boolean = false
) {
    GetExtendedAgentCard("GetExtendedAgentCard"),
    SendMessage("SendMessage"),
    SendMessageStreaming("SendStreamingMessage", streaming = true),
    GetTask("GetTask"),
    ListTasks("ListTasks"),
    CancelTask("CancelTask"),
    SubscribeToTask("SubscribeToTask", streaming = true),
    CreateTaskPushNotificationConfig("CreateTaskPushNotificationConfig"),
    GetTaskPushNotificationConfig("GetTaskPushNotificationConfig"),
    ListTaskPushNotificationConfigs("ListTaskPushNotificationConfigs"),
    DeleteTaskPushNotificationConfig("DeleteTaskPushNotificationConfig"),
}
