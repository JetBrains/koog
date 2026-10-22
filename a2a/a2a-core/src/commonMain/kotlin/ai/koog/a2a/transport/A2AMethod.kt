package ai.koog.a2a.transport

/**
 * The A2A operations.
 *
 * @property value The operation's method name, as used by the JSON-RPC (and gRPC) bindings.
 * @property streaming Whether the operation responds with a stream of events.
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
