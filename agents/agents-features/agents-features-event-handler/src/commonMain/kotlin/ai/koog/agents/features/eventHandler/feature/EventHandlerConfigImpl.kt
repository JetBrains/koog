package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.annotation.InternalAgentsApi
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

internal class EventHandlerConfigImpl : EventHandlerConfigAPI {

    //region Private Agent Handlers

    private var _onAgentStarting: suspend (eventHandler: AgentStartingContext) -> Unit = { _ -> }

    private var _onAgentCompleted: suspend (eventHandler: AgentCompletedContext) -> Unit = { _ -> }

    private var _onAgentExecutionFailed: suspend (eventHandler: AgentExecutionFailedContext) -> Unit = { _ -> }

    private var _onAgentClosing: suspend (eventHandler: AgentClosingContext) -> Unit = { _ -> }

    //endregion Private Agent Handlers

    //region Private Strategy Handlers

    private var _onStrategyStarting: suspend (eventHandler: StrategyStartingContext) -> Unit = { _ -> }

    private var _onStrategyCompleted: suspend (eventHandler: StrategyCompletedContext) -> Unit = { _ -> }

    //endregion Private Strategy Handlers

    //region Private Node Handlers

    private var _onNodeExecutionStarting: suspend (eventHandler: NodeExecutionStartingContext) -> Unit = { _ -> }

    private var _onNodeExecutionCompleted: suspend (eventHandler: NodeExecutionCompletedContext) -> Unit = { _ -> }

    private var _onNodeExecutionFailed: suspend (eventHandler: NodeExecutionFailedContext) -> Unit = { _ -> }

    //endregion Private Node Handlers

    //region Private Subgraph Handlers

    private var _onSubgraphExecutionStarting: suspend (eventHandler: SubgraphExecutionStartingContext) -> Unit =
        { _ -> }

    private var _onSubgraphExecutionCompleted: suspend (eventHandler: SubgraphExecutionCompletedContext) -> Unit =
        { _ -> }

    private var _onSubgraphExecutionFailed: suspend (eventHandler: SubgraphExecutionFailedContext) -> Unit = { _ -> }

    //endregion Private Subgraph Handlers

    //region Private LLM Call Handlers

    private var _onLLMCallStarting: suspend (eventHandler: LLMCallStartingContext) -> Unit = { _ -> }

    private var _onLLMCallCompleted: suspend (eventHandler: LLMCallCompletedContext) -> Unit = { _ -> }

    //endregion Private LLM Call Handlers

    //region Private Tool Call Handlers

    private var _onToolCallStarting: suspend (eventHandler: ToolCallStartingContext) -> Unit = { _ -> }

    private var _onToolValidationFailed: suspend (eventHandler: ToolValidationFailedContext) -> Unit = { _ -> }

    private var _onToolCallFailed: suspend (eventHandler: ToolCallFailedContext) -> Unit = { _ -> }

    private var _onToolCallCompleted: suspend (eventHandler: ToolCallCompletedContext) -> Unit = { _ -> }

    //endregion Private Tool Call Handlers

    //region Private Stream Handlers

    private var _onLLMStreamingStarting: suspend (eventHandler: LLMStreamingStartingContext) -> Unit = { _ -> }

    private var _onLLMStreamingFrameReceived: suspend (eventHandler: LLMStreamingFrameReceivedContext) -> Unit =
        { _ -> }

    private var _onLLMStreamingFailed: suspend (eventHandler: LLMStreamingFailedContext) -> Unit = { _ -> }

    private var _onLLMStreamingCompleted: suspend (eventHandler: LLMStreamingCompletedContext) -> Unit = { _ -> }

    //endregion Private Stream Handlers

    //region Agent Handlers

    public override fun onAgentStarting(handler: suspend (eventContext: AgentStartingContext) -> Unit) {
        val originalHandler = this._onAgentStarting
        this._onAgentStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onAgentCompleted(handler: suspend (eventContext: AgentCompletedContext) -> Unit) {
        val originalHandler = this._onAgentCompleted
        this._onAgentCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onAgentExecutionFailed(handler: suspend (eventContext: AgentExecutionFailedContext) -> Unit) {
        val originalHandler = this._onAgentExecutionFailed
        this._onAgentExecutionFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onAgentClosing(handler: suspend (eventContext: AgentClosingContext) -> Unit) {
        val originalHandler = this._onAgentClosing
        this._onAgentClosing = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Trigger Agent Handlers

    //region Strategy Handlers

    public override fun onStrategyStarting(handler: suspend (eventContext: StrategyStartingContext) -> Unit) {
        val originalHandler = this._onStrategyStarting
        this._onStrategyStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onStrategyCompleted(handler: suspend (eventContext: StrategyCompletedContext) -> Unit) {
        val originalHandler = this._onStrategyCompleted
        this._onStrategyCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Strategy Handlers

    //region Node Handlers

    public override fun onNodeExecutionStarting(handler: suspend (eventContext: NodeExecutionStartingContext) -> Unit) {
        val originalHandler = this._onNodeExecutionStarting
        this._onNodeExecutionStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onNodeExecutionCompleted(handler: suspend (eventContext: NodeExecutionCompletedContext) -> Unit) {
        val originalHandler = this._onNodeExecutionCompleted
        this._onNodeExecutionCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onNodeExecutionFailed(handler: suspend (eventContext: NodeExecutionFailedContext) -> Unit) {
        val originalHandler = this._onNodeExecutionFailed
        this._onNodeExecutionFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Node Handlers

    //region Subgraph Handlers

    public override fun onSubgraphExecutionStarting(handler: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit) {
        val originalHandler = this._onSubgraphExecutionStarting
        this._onSubgraphExecutionStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onSubgraphExecutionCompleted(handler: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit) {
        val originalHandler = this._onSubgraphExecutionCompleted
        this._onSubgraphExecutionCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onSubgraphExecutionFailed(handler: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit) {
        val originalHandler = this._onSubgraphExecutionFailed
        this._onSubgraphExecutionFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Subgraph Handlers

    //region LLM Call Handlers

    public override fun onLLMCallStarting(handler: suspend (eventContext: LLMCallStartingContext) -> Unit) {
        val originalHandler = this._onLLMCallStarting
        this._onLLMCallStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onLLMCallCompleted(handler: suspend (eventContext: LLMCallCompletedContext) -> Unit) {
        val originalHandler = this._onLLMCallCompleted
        this._onLLMCallCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    public override fun onToolCallStarting(handler: suspend (eventContext: ToolCallStartingContext) -> Unit) {
        val originalHandler = this._onToolCallStarting
        this._onToolCallStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onToolValidationFailed(handler: suspend (eventContext: ToolValidationFailedContext) -> Unit) {
        val originalHandler = this._onToolValidationFailed
        this._onToolValidationFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onToolCallFailed(handler: suspend (eventContext: ToolCallFailedContext) -> Unit) {
        val originalHandler = this._onToolCallFailed
        this._onToolCallFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onToolCallCompleted(handler: suspend (eventContext: ToolCallCompletedContext) -> Unit) {
        val originalHandler = this._onToolCallCompleted
        this._onToolCallCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Tool Call Handlers

    //region Stream Handlers

    public override fun onLLMStreamingStarting(handler: suspend (eventContext: LLMStreamingStartingContext) -> Unit) {
        val originalHandler = this._onLLMStreamingStarting
        this._onLLMStreamingStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onLLMStreamingFrameReceived(handler: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit) {
        val originalHandler = this._onLLMStreamingFrameReceived
        this._onLLMStreamingFrameReceived = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onLLMStreamingFailed(handler: suspend (eventContext: LLMStreamingFailedContext) -> Unit) {
        val originalHandler = this._onLLMStreamingFailed
        this._onLLMStreamingFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    public override fun onLLMStreamingCompleted(handler: suspend (eventContext: LLMStreamingCompletedContext) -> Unit) {
        val originalHandler = this._onLLMStreamingCompleted
        this._onLLMStreamingCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Stream Handlers

    //region Deprecated Handlers

    @Deprecated(
        message = "Use onAgentStarting instead",
        ReplaceWith("onAgentStarting(handler)", "ai.koog.agents.core.feature.handler.AgentStartingContext")
    )
    public override fun onBeforeAgentStarted(handler: suspend (eventContext: AgentStartContext) -> Unit) {
        onAgentStarting(handler)
    }

    @Deprecated(
        message = "Use onAgentCompleted instead",
        ReplaceWith("onAgentCompleted(handler)", "ai.koog.agents.core.feature.handler.AgentCompletedContext")
    )
    public override fun onAgentFinished(handler: suspend (eventContext: AgentFinishedContext) -> Unit) {
        onAgentCompleted(handler)
    }

    @Deprecated(
        message = "Use onAgentExecutionFailed instead",
        ReplaceWith(
            "onAgentExecutionFailed(handler)",
            "ai.koog.agents.core.feature.handler.AgentExecutionFailedContext"
        )
    )
    public override fun onAgentRunError(handler: suspend (eventContext: AgentRunErrorContext) -> Unit) {
        onAgentExecutionFailed(handler)
    }

    @Deprecated(
        message = "Use onAgentClosing instead",
        ReplaceWith("onAgentClosing(handler)", "ai.koog.agents.core.feature.handler.AgentClosingContext")
    )
    public override fun onAgentBeforeClose(handler: suspend (eventContext: AgentBeforeCloseContext) -> Unit) {
        onAgentClosing(handler)
    }

    @Deprecated(
        message = "Use onStrategyStarting instead",
        ReplaceWith("onStrategyStarting(handler)", "ai.koog.agents.core.feature.handler.StrategyStartingContext")
    )
    public override fun onStrategyStarted(handler: suspend (eventContext: StrategyStartContext) -> Unit) {
        onStrategyStarting(handler)
    }

    @Deprecated(
        message = "Use onStrategyCompleted instead",
        ReplaceWith("onStrategyCompleted(handler)", "ai.koog.agents.core.feature.handler.StrategyCompletedContext")
    )
    public override fun onStrategyFinished(handler: suspend (eventContext: StrategyFinishedContext) -> Unit) {
        onStrategyCompleted(handler)
    }

    @Deprecated(
        message = "Use onNodeExecutionStarting instead",
        ReplaceWith(
            "onNodeExecutionStarting(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionStartingContext"
        )
    )
    public override fun onBeforeNode(handler: suspend (eventContext: NodeBeforeExecuteContext) -> Unit) {
        onNodeExecutionStarting(handler)
    }

    @Deprecated(
        message = "Use onNodeExecutionCompleted instead",
        ReplaceWith(
            "onNodeExecutionCompleted(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionCompletedContext"
        )
    )
    public override fun onAfterNode(handler: suspend (eventContext: NodeAfterExecuteContext) -> Unit) {
        onNodeExecutionCompleted(handler)
    }

    @Deprecated(
        message = "Use onNodeExecutionError instead",
        ReplaceWith("onNodeExecutionFailed(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionFailedContext")
    )
    public override fun onNodeExecutionError(handler: suspend (eventContext: NodeExecutionErrorContext) -> Unit) {
        onNodeExecutionFailed(handler)
    }

    @Deprecated(
        message = "Use onLLMCallStarting instead",
        ReplaceWith("onLLMCallStarting(handler)", "ai.koog.agents.core.feature.handler.LLMCallStartingContext")
    )
    public override fun onBeforeLLMCall(handler: suspend (eventContext: BeforeLLMCallContext) -> Unit) {
        onLLMCallStarting(handler)
    }

    @Deprecated(
        message = "Use onLLMCallCompleted instead",
        ReplaceWith("onLLMCallCompleted(handler)", "ai.koog.agents.core.feature.handler.LLMCallCompletedContext")
    )
    public override fun onAfterLLMCall(handler: suspend (eventContext: AfterLLMCallContext) -> Unit) {
        onLLMCallCompleted(handler)
    }

    @Deprecated(
        message = "Use onToolCallStarting instead",
        ReplaceWith("onToolCallStarting(handler)", "ai.koog.agents.core.feature.handler.ToolCallStartingContext")
    )
    public override fun onToolCall(handler: suspend (eventContext: ToolCallContext) -> Unit) {
        onToolCallStarting(handler)
    }

    @Deprecated(
        message = "Use onToolValidationFailed instead",
        ReplaceWith(
            "onToolValidationFailed(handler)",
            "ai.koog.agents.core.feature.handler.ToolValidationFailedContext"
        )
    )
    public override fun onToolValidationError(handler: suspend (eventContext: ToolValidationErrorContext) -> Unit) {
        onToolValidationFailed(handler)
    }

    @Deprecated(
        message = "Use onToolCallFailed instead",
        ReplaceWith("onToolCallFailed(handler)", "ai.koog.agents.core.feature.handler.ToolCallFailedContext")
    )
    public override fun onToolCallFailure(handler: suspend (eventContext: ToolCallFailureContext) -> Unit) {
        onToolCallFailed(handler)
    }

    @Deprecated(
        message = "Use onToolCallCompleted instead",
        ReplaceWith("onToolCallCompleted(handler)", "ai.koog.agents.core.feature.handler.ToolCallCompletedContext")
    )
    public override fun onToolCallResult(handler: suspend (eventContext: ToolCallResultContext) -> Unit) {
        onToolCallCompleted(handler)
    }

    //endregion Deprecated Handlers

    //region Invoke Agent Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnAgentStarting(eventContext: AgentStartingContext) {
        _onAgentStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnAgentCompleted(eventContext: AgentCompletedContext) {
        _onAgentCompleted.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnAgentExecutionFailed(eventContext: AgentExecutionFailedContext) {
        _onAgentExecutionFailed.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnAgentClosing(eventContext: AgentClosingContext) {
        _onAgentClosing.invoke(eventContext)
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnStrategyStarting(eventContext: StrategyStartingContext) {
        _onStrategyStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnStrategyCompleted(eventContext: StrategyCompletedContext) {
        _onStrategyCompleted.invoke(eventContext)
    }

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnNodeExecutionStarting(eventContext: NodeExecutionStartingContext) {
        _onNodeExecutionStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnNodeExecutionCompleted(eventContext: NodeExecutionCompletedContext) {
        _onNodeExecutionCompleted.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnNodeExecutionFailed(interceptContext: NodeExecutionFailedContext) {
        _onNodeExecutionFailed.invoke(interceptContext)
    }

    //endregion Invoke Node Handlers

    //region Invoke Subgraph Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnSubgraphExecutionStarting(eventContext: SubgraphExecutionStartingContext) {
        _onSubgraphExecutionStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnSubgraphExecutionCompleted(eventContext: SubgraphExecutionCompletedContext) {
        _onSubgraphExecutionCompleted.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnSubgraphExecutionFailed(interceptContext: SubgraphExecutionFailedContext) {
        _onSubgraphExecutionFailed.invoke(interceptContext)
    }

    //endregion Invoke Subgraph Handlers

    //region Invoke LLM Call Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnLLMCallStarting(eventContext: LLMCallStartingContext) {
        _onLLMCallStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnLLMCallCompleted(eventContext: LLMCallCompletedContext) {
        _onLLMCallCompleted.invoke(eventContext)
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnToolCallStarting(eventContext: ToolCallStartingContext) {
        _onToolCallStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnToolValidationFailed(eventContext: ToolValidationFailedContext) {
        _onToolValidationFailed.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnToolCallFailed(eventContext: ToolCallFailedContext) {
        _onToolCallFailed.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnToolCallCompleted(eventContext: ToolCallCompletedContext) {
        _onToolCallCompleted.invoke(eventContext)
    }

    //endregion Invoke Tool Call Handlers

    //region Invoke Stream Handlers

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingStarting(eventContext: LLMStreamingStartingContext) {
        _onLLMStreamingStarting.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingFrameReceived(eventContext: LLMStreamingFrameReceivedContext) {
        _onLLMStreamingFrameReceived.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingFailed(eventContext: LLMStreamingFailedContext) {
        _onLLMStreamingFailed.invoke(eventContext)
    }

    @InternalAgentsApi
    public override suspend fun invokeOnLLMStreamingCompleted(eventContext: LLMStreamingCompletedContext) {
        _onLLMStreamingCompleted.invoke(eventContext)
    }

    //endregion Invoke Stream Handlers
}
