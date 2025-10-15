package ai.koog.agents.core.feature.handler.strategy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import kotlin.reflect.KType

/**
 * Defines the context specifically for handling strategy-related events within the AI agent framework.
 * Extends the base event handler context to include functionality and behavior dedicated to managing
 * the lifecycle and operations of strategies associated with AI agents.
 */
public interface StrategyEventContext : AgentLifecycleEventContext

/**
 * Represents the context for updating AI agent strategies during execution.
 *
 * @property runId A unique identifier for the session during which the strategy is being updated.
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 */
public class StrategyStartingContext(
    public val runId: String,
    public val strategy: AIAgentStrategy<*, *, *>,
    public val context: AIAgentContext,
) : StrategyEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.StrategyStarting
}

/**
 * Represents the context associated with the completion of an AI agent strategy execution.
 *
 * @property runId A unique identifier for the session during which the strategy is being updated.
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property result Strategy result.
 * @property resultType [KType] representing the type of the [result]
 */
public class StrategyCompletedContext(
    public val runId: String,
    public val strategy: AIAgentStrategy<*, *, *>,
    public val result: Any?,
    public val resultType: KType,
    public val agentId: String
) : StrategyEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.StrategyCompleted
}
