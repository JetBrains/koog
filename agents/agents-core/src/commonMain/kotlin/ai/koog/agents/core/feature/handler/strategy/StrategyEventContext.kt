package ai.koog.agents.core.feature.handler.strategy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
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
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property context The context associated with the strategy's execution.
 */
public class StrategyStartingContext(
    override val executionInfo: AgentExecutionInfo,
    public val strategy: AIAgentStrategy<*, *, *>,
    public val context: AIAgentContext,
) : StrategyEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.StrategyStarting

    /**
     * The unique identifier for this run.
     * @deprecated Use runId property from a [context] instance instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Please get runId from a context instance instead",
        replaceWith = ReplaceWith("context.runId")
    )
    public val runId: String
        get() = this.context.runId
}

/**
 * Represents the context associated with the completion of an AI agent strategy execution.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property context The context associated with the strategy's execution.
 * @property result Strategy result.
 * @property resultType [KType] representing the type of the [result]
 */
public class StrategyCompletedContext(
    override val executionInfo: AgentExecutionInfo,
    public val strategy: AIAgentStrategy<*, *, *>,
    public val context: AIAgentContext,
    public val result: Any?,
    public val resultType: KType,
) : StrategyEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.StrategyCompleted

    /**
     * The unique identifier for this run.
     * @deprecated Use runId property from a [context] instance instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Please get runId from a [context] instance instead",
        replaceWith = ReplaceWith("context.runId")
    )
    public val runId: String
        get() = this.context.runId

    /**
     * The identifier for this agent.
     * @deprecated Use agentId property from a [context] instance instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Please get agentId from a [context] instance instead",
        replaceWith = ReplaceWith("context.agentId")
    )
    public val agentId: String
        get() = this.context.agentId
}
