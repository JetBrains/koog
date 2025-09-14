package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import kotlin.reflect.KType

/**
 * Provides the context for handling events specific to AI agents.
 * This interface extends the foundational event handling context, `EventHandlerContext`,
 * and is specialized for scenarios involving agents and their associated workflows or features.
 *
 * The `AgentEventHandlerContext` enables implementation of event-driven systems within
 * the AI Agent framework by offering hooks for custom event handling logic tailored to agent operations.
 */
public interface AgentEventHandlerContext : EventHandlerContext

/**
 * Provides a context for executing transformations and operations within an AI agent's environment.
 *
 * @param TFeature The type of the feature associated with the context.
 * @property strategy The AI agent strategy that defines the workflow and execution logic for the AI agent.
 * @property agent The AI agent being managed or operated upon in the context.
 * @property feature An additional feature or configuration associated with the context.
 */
public class AgentTransformEnvironmentContext<TFeature>(
    public val strategy: AIAgentGraphStrategy<*, *>,
    public val agent: GraphAIAgent<*, *>,
    public val feature: TFeature
) : AgentEventHandlerContext

/**
 * Represents the context available during the start of an AI agent.
 *
 * @param TFeature The type of the feature object associated with this context.
 * @property agent The AI agent associated with this context.
 * @property feature The feature-specific data associated with this context.
 */
public data class AgentStartContext<TFeature>(
    public val agent: AIAgent<*, *>,
    public val runId: String,
    public val feature: TFeature,
    public val context: AIAgentContext,
) : AgentEventHandlerContext

/**
 * Represents the context for handling the completion of an agent's execution.
 *
 * @property agentId The unique identifier of the agent that completed its execution.
 * @property runId The identifier of the session in which the agent was executed.
 * @property result The optional result of the agent's execution, if available.
 * @property resultType [KType] of the [result].
 */
public data class AgentFinishedContext(
    public val agentId: String,
    public val runId: String,
    public val result: Any?,
    public val resultType: KType,
) : AgentEventHandlerContext

/**
 * Represents the context for handling errors that occur during the execution of an agent run.
 *
 * @property agentId The unique identifier of the agent associated with the error.
 * @property runId The identifier for the session during which the error occurred.
 * @property throwable The exception or error thrown during the execution.
 */
public data class AgentRunErrorContext(
    val agentId: String,
    val runId: String,
    val throwable: Throwable
) : AgentEventHandlerContext

/**
 * Represents the context passed to the handler that is executed before an agent is closed.
 *
 * @property agentId Identifier of the agent that is about to be closed.
 */
public data class AgentBeforeCloseContext(
    val agentId: String,
)
