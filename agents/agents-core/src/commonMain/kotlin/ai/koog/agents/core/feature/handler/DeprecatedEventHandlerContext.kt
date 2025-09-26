package ai.koog.agents.core.feature.handler

/**
 * Represents the context in which event handlers operate, providing a foundational
 * interface for all event handling activities within the AI Agent framework.
 */
@Deprecated(
    message = "Use AgentLifecycleEventContext instead",
    replaceWith = ReplaceWith("AgentLifecycleEventContext")
)
public typealias EventHandlerContext = AgentLifecycleEventContext
