package ai.koog.agents.features.opentelemetry.integration.mcp

/**
 * Enumeration of MCP (Model Context Protocol) method names.
 *
 * These method names follow the MCP specification and are used in OpenTelemetry
 * instrumentation to identify the type of MCP operation being performed.
 *
 * Based on the OpenTelemetry semantic conventions for MCP:
 * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/registry/attributes/mcp.md
 *
 * @property methodName The MCP method name as defined in the protocol specification.
 */
internal enum class McpMethod(val methodName: String) {
    /** Request to complete a prompt. */
    COMPLETION_COMPLETE("completion/complete"),

    /** Request from the server to elicit additional information from the user via the client. */
    ELICITATION_CREATE("elicitation/create"),

    /** Request to initialize the MCP client. */
    INITIALIZE("initialize"),

    /** Request to set the logging level. */
    LOGGING_SET_LEVEL("logging/setLevel"),

    /** Notification cancelling a previously-issued request. */
    NOTIFICATIONS_CANCELLED("notifications/cancelled"),

    /** Notification indicating that the MCP client has been initialized. */
    NOTIFICATIONS_INITIALIZED("notifications/initialized"),

    /** Notification indicating that a message has been received. */
    NOTIFICATIONS_MESSAGE("notifications/message"),

    /** Notification indicating the progress for a long-running operation. */
    NOTIFICATIONS_PROGRESS("notifications/progress"),

    /** Notification indicating that the list of prompts has changed. */
    NOTIFICATIONS_PROMPTS_LIST_CHANGED("notifications/prompts/list_changed"),

    /** Notification indicating that the list of resources has changed. */
    NOTIFICATIONS_RESOURCES_LIST_CHANGED("notifications/resources/list_changed"),

    /** Notification indicating that a resource has been updated. */
    NOTIFICATIONS_RESOURCES_UPDATED("notifications/resources/updated"),

    /** Notification indicating that the list of roots has changed. */
    NOTIFICATIONS_ROOTS_LIST_CHANGED("notifications/roots/list_changed"),

    /** Notification indicating that the list of tools has changed. */
    NOTIFICATIONS_TOOLS_LIST_CHANGED("notifications/tools/list_changed"),

    /** Request to check that the other party is still alive. */
    PING("ping"),

    /** Request to get a prompt. */
    PROMPTS_GET("prompts/get"),

    /** Request to list prompts available on server. */
    PROMPTS_LIST("prompts/list"),

    /** Request to list resources available on server. */
    RESOURCES_LIST("resources/list"),

    /** Request to read a resource. */
    RESOURCES_READ("resources/read"),

    /** Request to subscribe to a resource. */
    RESOURCES_SUBSCRIBE("resources/subscribe"),

    /** Request to list resource templates available on server. */
    RESOURCES_TEMPLATES_LIST("resources/templates/list"),

    /** Request to unsubscribe from resource updates. */
    RESOURCES_UNSUBSCRIBE("resources/unsubscribe"),

    /** Request to list roots available on server. */
    ROOTS_LIST("roots/list"),

    /** Request to create a sampling message. */
    SAMPLING_CREATE_MESSAGE("sampling/createMessage"),

    /** Request to call a tool. */
    TOOLS_CALL("tools/call"),

    /** Request to list tools available on server. */
    TOOLS_LIST("tools/list")
}
