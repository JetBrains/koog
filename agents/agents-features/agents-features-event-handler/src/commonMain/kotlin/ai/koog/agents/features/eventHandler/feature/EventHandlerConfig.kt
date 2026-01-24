package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AfterLLMCallContext
import ai.koog.agents.core.feature.handler.AgentBeforeCloseContext
import ai.koog.agents.core.feature.handler.AgentFinishedContext
import ai.koog.agents.core.feature.handler.AgentRunErrorContext
import ai.koog.agents.core.feature.handler.AgentStartContext
import ai.koog.agents.core.feature.handler.BeforeLLMCallContext
import ai.koog.agents.core.feature.handler.NodeAfterExecuteContext
import ai.koog.agents.core.feature.handler.NodeBeforeExecuteContext
import ai.koog.agents.core.feature.handler.NodeExecutionErrorContext
import ai.koog.agents.core.feature.handler.StrategyFinishedContext
import ai.koog.agents.core.feature.handler.StrategyStartContext
import ai.koog.agents.core.feature.handler.ToolCallContext
import ai.koog.agents.core.feature.handler.ToolCallFailureContext
import ai.koog.agents.core.feature.handler.ToolCallResultContext
import ai.koog.agents.core.feature.handler.ToolValidationErrorContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext

/**
 * Configuration class for the EventHandler feature.
 *
 * This class provides a way to configure handlers for various events that occur during
 * the execution of an agent. These events include agent lifecycle events, strategy events,
 * node events, LLM call events, and tool call events.
 *
 * Each handler is a property that can be assigned a lambda function to be executed when
 * the corresponding event occurs.
 *
 * Example usage:
 * ```
 * handleEvents {
 *     onToolCallStarting { eventContext ->
 *         println("Tool called: ${eventContext.tool.name} with args ${eventContext.toolArgs}")
 *     }
 *
 *     onAgentCompleted { eventContext ->
 *         println("Agent finished with result: ${eventContext.result}")
 *     }
 * }
 * ```
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class EventHandlerConfig constructor() : FeatureConfig, EventHandlerConfigAPI {
    //region Agent Handlers

    public override fun onAgentStarting(handler: suspend (eventContext: AgentStartingContext) -> Unit)

    public override fun onAgentCompleted(handler: suspend (eventContext: AgentCompletedContext) -> Unit)

    public override fun onAgentExecutionFailed(handler: suspend (eventContext: AgentExecutionFailedContext) -> Unit)

    public override fun onAgentClosing(handler: suspend (eventContext: AgentClosingContext) -> Unit)

    //endregion Trigger Agent Handlers

    //region Strategy Handlers

    public override fun onStrategyStarting(handler: suspend (eventContext: StrategyStartingContext) -> Unit)

    public override fun onStrategyCompleted(handler: suspend (eventContext: StrategyCompletedContext) -> Unit)

    //endregion Strategy Handlers

    //region Node Handlers

    public override fun onNodeExecutionStarting(handler: suspend (eventContext: NodeExecutionStartingContext) -> Unit)

    public override fun onNodeExecutionCompleted(handler: suspend (eventContext: NodeExecutionCompletedContext) -> Unit)

    public override fun onNodeExecutionFailed(handler: suspend (eventContext: NodeExecutionFailedContext) -> Unit)

    @InternalAgentsApi
    public override suspend fun invokeOnSubgraphExecutionStarting(eventContext: SubgraphExecutionStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnSubgraphExecutionCompleted(eventContext: SubgraphExecutionCompletedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnSubgraphExecutionFailed(interceptContext: SubgraphExecutionFailedContext)

    //endregion Node Handlers

    //region Subgraph Handlers

    public override fun onSubgraphExecutionStarting(handler: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit)

    public override fun onSubgraphExecutionCompleted(handler: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit)

    public override fun onSubgraphExecutionFailed(handler: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit)

    //endregion Subgraph Handlers

    //region LLM Call Handlers

    public override fun onLLMCallStarting(handler: suspend (eventContext: LLMCallStartingContext) -> Unit)

    public override fun onLLMCallCompleted(handler: suspend (eventContext: LLMCallCompletedContext) -> Unit)

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    public override fun onToolCallStarting(handler: suspend (eventContext: ToolCallStartingContext) -> Unit)

    public override fun onToolValidationFailed(handler: suspend (eventContext: ToolValidationFailedContext) -> Unit)

    public override fun onToolCallFailed(handler: suspend (eventContext: ToolCallFailedContext) -> Unit)

    public override fun onToolCallCompleted(handler: suspend (eventContext: ToolCallCompletedContext) -> Unit)

    //endregion Tool Call Handlers

    //region Stream Handlers

    public override fun onLLMStreamingStarting(handler: suspend (eventContext: LLMStreamingStartingContext) -> Unit)

    public override fun onLLMStreamingFrameReceived(handler: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit)

    public override fun onLLMStreamingFailed(handler: suspend (eventContext: LLMStreamingFailedContext) -> Unit)

    public override fun onLLMStreamingCompleted(handler: suspend (eventContext: LLMStreamingCompletedContext) -> Unit)

    //endregion Stream Handlers

    //region Deprecated Handlers

    @Deprecated(
        message = "Use onAgentStarting instead",
        ReplaceWith("onAgentStarting(handler)", "ai.koog.agents.core.feature.handler.AgentStartingContext")
    )
    public override fun onBeforeAgentStarted(handler: suspend (eventContext: AgentStartContext) -> Unit)

    @Deprecated(
        message = "Use onAgentCompleted instead",
        ReplaceWith("onAgentCompleted(handler)", "ai.koog.agents.core.feature.handler.AgentCompletedContext")
    )
    public override fun onAgentFinished(handler: suspend (eventContext: AgentFinishedContext) -> Unit)

    @Deprecated(
        message = "Use onAgentExecutionFailed instead",
        ReplaceWith(
            "onAgentExecutionFailed(handler)",
            "ai.koog.agents.core.feature.handler.AgentExecutionFailedContext"
        )
    )
    public override fun onAgentRunError(handler: suspend (eventContext: AgentRunErrorContext) -> Unit)

    @Deprecated(
        message = "Use onAgentClosing instead",
        ReplaceWith("onAgentClosing(handler)", "ai.koog.agents.core.feature.handler.AgentClosingContext")
    )
    public override fun onAgentBeforeClose(handler: suspend (eventContext: AgentBeforeCloseContext) -> Unit)

    @Deprecated(
        message = "Use onStrategyStarting instead",
        ReplaceWith("onStrategyStarting(handler)", "ai.koog.agents.core.feature.handler.StrategyStartingContext")
    )
    public override fun onStrategyStarted(handler: suspend (eventContext: StrategyStartContext) -> Unit)

    @Deprecated(
        message = "Use onStrategyCompleted instead",
        ReplaceWith("onStrategyCompleted(handler)", "ai.koog.agents.core.feature.handler.StrategyCompletedContext")
    )
    public override fun onStrategyFinished(handler: suspend (eventContext: StrategyFinishedContext) -> Unit)

    @Deprecated(
        message = "Use onNodeExecutionStarting instead",
        ReplaceWith(
            "onNodeExecutionStarting(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionStartingContext"
        )
    )
    public override fun onBeforeNode(handler: suspend (eventContext: NodeBeforeExecuteContext) -> Unit)

    @Deprecated(
        message = "Use onNodeExecutionCompleted instead",
        ReplaceWith(
            "onNodeExecutionCompleted(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionCompletedContext"
        )
    )
    public override fun onAfterNode(handler: suspend (eventContext: NodeAfterExecuteContext) -> Unit)

    @Deprecated(
        message = "Use onNodeExecutionError instead",
        ReplaceWith("onNodeExecutionFailed(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionFailedContext")
    )
    public override fun onNodeExecutionError(handler: suspend (eventContext: NodeExecutionErrorContext) -> Unit)

    @Deprecated(
        message = "Use onLLMCallStarting instead",
        ReplaceWith("onLLMCallStarting(handler)", "ai.koog.agents.core.feature.handler.LLMCallStartingContext")
    )
    public override fun onBeforeLLMCall(handler: suspend (eventContext: BeforeLLMCallContext) -> Unit)

    @Deprecated(
        message = "Use onLLMCallCompleted instead",
        ReplaceWith("onLLMCallCompleted(handler)", "ai.koog.agents.core.feature.handler.LLMCallCompletedContext")
    )
    public override fun onAfterLLMCall(handler: suspend (eventContext: AfterLLMCallContext) -> Unit)

    @Deprecated(
        message = "Use onToolCallStarting instead",
        ReplaceWith("onToolCallStarting(handler)", "ai.koog.agents.core.feature.handler.ToolCallStartingContext")
    )
    public override fun onToolCall(handler: suspend (eventContext: ToolCallContext) -> Unit)

    @Deprecated(
        message = "Use onToolValidationFailed instead",
        ReplaceWith(
            "onToolValidationFailed(handler)",
            "ai.koog.agents.core.feature.handler.ToolValidationFailedContext"
        )
    )
    public override fun onToolValidationError(handler: suspend (eventContext: ToolValidationErrorContext) -> Unit)

    @Deprecated(
        message = "Use onToolCallFailed instead",
        ReplaceWith("onToolCallFailed(handler)", "ai.koog.agents.core.feature.handler.ToolCallFailedContext")
    )
    public override fun onToolCallFailure(handler: suspend (eventContext: ToolCallFailureContext) -> Unit)

    @Deprecated(
        message = "Use onToolCallCompleted instead",
        ReplaceWith("onToolCallCompleted(handler)", "ai.koog.agents.core.feature.handler.ToolCallCompletedContext")
    )
    public override fun onToolCallResult(handler: suspend (eventContext: ToolCallResultContext) -> Unit)

    //endregion Deprecated Handlers

    //region Invoke Agent Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnAgentStarting(eventContext: AgentStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnAgentCompleted(eventContext: AgentCompletedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnAgentExecutionFailed(eventContext: AgentExecutionFailedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnAgentClosing(eventContext: AgentClosingContext)

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnStrategyStarting(eventContext: StrategyStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnStrategyCompleted(eventContext: StrategyCompletedContext)

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnNodeExecutionStarting(eventContext: NodeExecutionStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnNodeExecutionCompleted(eventContext: NodeExecutionCompletedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnNodeExecutionFailed(interceptContext: NodeExecutionFailedContext)

    //endregion Invoke Node Handlers

    //region Invoke LLM Call Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnLLMCallStarting(eventContext: LLMCallStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnLLMCallCompleted(eventContext: LLMCallCompletedContext)

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnToolCallStarting(eventContext: ToolCallStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnToolValidationFailed(eventContext: ToolValidationFailedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnToolCallFailed(eventContext: ToolCallFailedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnToolCallCompleted(eventContext: ToolCallCompletedContext)

    //endregion Invoke Tool Call Handlers

    //region Invoke Stream Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingStarting(eventContext: LLMStreamingStartingContext)

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingFrameReceived(eventContext: LLMStreamingFrameReceivedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingFailed(eventContext: LLMStreamingFailedContext)

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingCompleted(eventContext: LLMStreamingCompletedContext)

    //endregion Invoke Stream Handlers
}
