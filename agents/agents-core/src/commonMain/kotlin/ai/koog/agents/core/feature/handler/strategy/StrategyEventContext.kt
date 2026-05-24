package ai.koog.agents.core.feature.handler.strategy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.serialization.TypeToken

/**
 * Defines the context specifically for handling strategy-related events within the AI agent framework.
 * Extends the base event handler context to include functionality and behavior dedicated to managing
 * the lifecycle and operations of strategies associated with AI agents.
 */
public interface StrategyEventContext : AgentLifecycleEventContext {
    /**
     * The AI agent context.
     */
    public val context: AIAgentContext

    /**
     * The strategy being handled or updated, encapsulating the AI agent's workflow logic.
     */
    public val strategy: AIAgentStrategy<*, *, *>
}

/**
 * Represents the context for updating AI agent strategies during execution.
 */
public class StrategyStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val strategy: AIAgentStrategy<*, *, *>,
) : StrategyEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.StrategyStarting
}

/**
 * Represents the context associated with the completion of an AI agent strategy execution.
 *
 * @property result Strategy result.
 * @property resultType [TypeToken] representing the type of the [result]
 */
public class StrategyCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val strategy: AIAgentStrategy<*, *, *>,
    public val result: Any?,
    public val resultType: TypeToken,
) : StrategyEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.StrategyCompleted
}
