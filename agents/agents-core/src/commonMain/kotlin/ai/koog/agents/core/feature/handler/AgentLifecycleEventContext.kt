package ai.koog.agents.core.feature.handler

/**
 * Represents the context in which event handlers operate, providing a foundational
 * interface for all event handling activities within the AI Agent framework.
 */
public interface AgentLifecycleEventContext {

    /**
     * Represents the specific type of event handled within the event handler context,
     * categorizing the nature of agent-related or strategy-related events.
     */
    public val eventType: AgentLifecycleEventType
}
