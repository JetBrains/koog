package ai.koog.agents.core.feature.handler.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import kotlin.time.Duration

/**
 * Provides the context for handling events specific to AI agents.
 * This interface extends the foundational event handling context, `EventHandlerContext`,
 * and is specialized for scenarios involving agents and their associated workflows or features.
 *
 * The `AgentEventHandlerContext` enables implementation of event-driven systems within
 * the AI Agent framework by offering hooks for custom event handling logic tailored to agent operations.
 */
public interface AgentEventContext : AgentLifecycleEventContext {
    /**
     * The AI agent associated with this context.
     */
    public val agent: AIAgent<*, *>
}

/**
 * Represents the context available during the start of an AI agent.
 *
 * @property context The context associated with the agent's execution;
 * @property runId The identifier for the session in which the agent is being executed.
 */
public data class AgentStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val agent: AIAgent<*, *>,
    public val context: AIAgentContext,
    public val runId: String,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentStarting
}

/**
 * Represents the context for handling the completion of an agent's execution.
 *
 * @property context The context associated with the agent's execution;
 * @property runId The identifier of the session in which the agent was executed;
 * @property result The optional result of the agent's execution, if available.
 * @property duration Elapsed time for agent execution.
 *           Excludes the run time of `onAgentStarting`/`onAgentCompleted` feature handlers themselves, so the value
 *           reflects only the agent's own work, not the framework's reporting overhead.
 */
public data class AgentCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val agent: AIAgent<*, *>,
    public val context: AIAgentContext,
    public val runId: String,
    public val result: Any?,
    public val duration: Duration,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentCompleted
}

/**
 * Represents the context for handling errors that occur during the execution of an agent run.
 *
 * @property context The context associated with the agent's execution.
 * @property runId The identifier for the session during which the error occurred.
 * @property error The exception or error thrown during the execution.
 * @property duration Elapsed time from immediately after `onAgentStarting` returned until the failure was observed,
 * or `null` if the failure originated before measurement could start (for example, when an `onAgentStarting`
 * feature handler threw). A `null` value explicitly signals "execution never began".
 */
public data class AgentExecutionFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val agent: AIAgent<*, *>,
    public val context: AIAgentContext,
    public val runId: String,
    public val error: Throwable,
    public val duration: Duration?,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentExecutionFailed
}

/**
 * Represents the context passed to the handler that is executed before an agent is closed.
 *
 * @property duration Elapsed time of the entire agent session, measured from when feature preparation began
 * until just before this event fires. Includes `onAgentStarting`/`onAgentCompleted` handler time, strategy
 * execution, and feature cleanup — i.e., the full session lifetime as seen by the framework.
 */
public data class AgentClosingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val agent: AIAgent<*, *>,
    public val duration: Duration,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentClosing
}

/**
 * Provides a context for executing transformations and operations within an AI agent's environment.
 */
public class AgentEnvironmentTransformingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val agent: AIAgent<*, *>,
) : AgentEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.AgentEnvironmentTransforming
}
