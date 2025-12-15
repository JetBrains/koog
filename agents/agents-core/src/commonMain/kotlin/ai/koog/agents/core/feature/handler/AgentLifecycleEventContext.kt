package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.execution.AgentExecutionInfo

/**
 * Represents the context in which event handlers operate, providing a foundational
 * interface for all event handling activities within the AI Agent framework.
 */
public interface AgentLifecycleEventContext {

    /**
     * Holds execution-specific context information to support observability and tracing
     * during the lifecycle of an agent.
     */
    public val executionInfo: AgentExecutionInfo

    /**
     * Represents the specific type of event handled within the event handler context,
     * categorizing the nature of agent-related or strategy-related events.
     */
    public val eventType: AgentLifecycleEventType
}
