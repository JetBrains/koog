package ai.koog.agents.core.feature.handler.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType

/**
 * Provides the context for handling events specific to AI agents.
 * This interface extends the foundational event handling context, `EventHandlerContext`,
 * and is specialized for scenarios involving agents and their associated workflows or features.
 *
 * The `AgentEventHandlerContext` enables implementation of event-driven systems within
 * the AI Agent framework by offering hooks for custom event handling logic tailored to agent operations.
 */
public interface AgentEventContext : AgentLifecycleEventContext

/**
 * Represents the context available during the start of an AI agent.
 *
 * @property agent The AI agent associated with this context.
 * @property runId The identifier for the session in which the agent is being executed.
 * @property context The context associated with the agent's execution.
 */
public data class AgentStartingContext(
    public val agent: AIAgent<*, *>,
    public val runId: String,
    public val context: AIAgentContext,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentStarting
}

/**
 * Represents the context for handling the completion of an agent's execution.
 *
 * @property agentId The unique identifier of the agent that completed its execution.
 * @property runId The identifier of the session in which the agent was executed.
 * @property result The optional result of the agent's execution, if available.
 */
public data class AgentCompletedContext(
    public val agentId: String,
    public val runId: String,
    public val result: Any?,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentCompleted
}

/**
 * Represents the context for handling errors that occur during the execution of an agent run.
 *
 * @property agentId The unique identifier of the agent associated with the error.
 * @property runId The identifier for the session during which the error occurred.
 * @property throwable The exception or error thrown during the execution.
 */
public data class AgentExecutionFailedContext(
    val agentId: String,
    val runId: String,
    val throwable: Throwable
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentExecutionFailed
}

/**
 * Represents the context passed to the handler that is executed before an agent is closed.
 *
 * @property agentId Identifier of the agent that is about to be closed.
 */
public data class AgentClosingContext(
    val agentId: String,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentClosing
}

/**
 * Provides a context for executing transformations and operations within an AI agent's environment.
 *
 * @property strategy The AI agent strategy that defines the workflow and execution logic for the AI agent.
 * @property agent The AI agent being managed or operated upon in the context.
 */
public class AgentEnvironmentTransformingContext(
    public val strategy: AIAgentStrategy<*, *, AIAgentGraphContextBase>,
    public val agent: GraphAIAgent<*, *>,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentEnvironmentTransforming
}
