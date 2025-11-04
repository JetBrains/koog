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
 * API for the [EventHandlerConfig]
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public interface EventHandlerConfigAPI {

    //region Agent Handlers

    /**
     * Append handler called when an agent is started.
     */
    public fun onAgentStarting(handler: suspend (eventContext: AgentStartingContext) -> Unit)

    /**
     * Append handler called when an agent finishes execution.
     */
    public fun onAgentCompleted(handler: suspend (eventContext: AgentCompletedContext) -> Unit)

    /**
     * Append handler called when an error occurs during agent execution.
     */
    public fun onAgentExecutionFailed(handler: suspend (eventContext: AgentExecutionFailedContext) -> Unit)

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    public fun onAgentClosing(handler: suspend (eventContext: AgentClosingContext) -> Unit)

    //endregion Trigger Agent Handlers

    //region Strategy Handlers

    /**
     * Append handler called when a strategy starts execution.
     */
    public fun onStrategyStarting(handler: suspend (eventContext: StrategyStartingContext) -> Unit)

    /**
     * Append handler called when a strategy finishes execution.
     */
    public fun onStrategyCompleted(handler: suspend (eventContext: StrategyCompletedContext) -> Unit)

    //endregion Strategy Handlers

    //region Node Handlers

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    public fun onNodeExecutionStarting(handler: suspend (eventContext: NodeExecutionStartingContext) -> Unit)

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    public fun onNodeExecutionCompleted(handler: suspend (eventContext: NodeExecutionCompletedContext) -> Unit)

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    public fun onNodeExecutionFailed(handler: suspend (eventContext: NodeExecutionFailedContext) -> Unit)

    /**
     * Invoked when the execution of a subgraph is starting. This method allows
     * you to perform actions or initialize resources necessary for the subgraph
     * execution process.
     *
     * @param eventContext The context associated with the start of the subgraph
     * execution. Contains relevant details such as metadata and configuration
     * for the current subgraph execution.
     */
    @InternalAgentsApi
    public suspend fun invokeOnSubgraphExecutionStarting(eventContext: SubgraphExecutionStartingContext)

    /**
     * Invoke handlers for after a subgraph in the agent's execution graph has been processed event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnSubgraphExecutionCompleted(eventContext: SubgraphExecutionCompletedContext)

    /**
     * Invokes the error handling logic for a subgraph execution error event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnSubgraphExecutionFailed(interceptContext: SubgraphExecutionFailedContext)

    //endregion Node Handlers

    //region Subgraph Handlers

    /**
     * Append handler called before a subgraph in the agent's execution graph is processed.
     */
    public fun onSubgraphExecutionStarting(handler: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit)

    /**
     * Append handler called after a subgraph in the agent's execution graph has been processed.
     */
    public fun onSubgraphExecutionCompleted(handler: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit)

    /**
     * Append handler called when an error occurs during the execution of a subgraph.
     */
    public fun onSubgraphExecutionFailed(handler: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit)

    //endregion Subgraph Handlers

    //region LLM Call Handlers

    /**
     * Append handler called before a call is made to the language model.
     */
    public fun onLLMCallStarting(handler: suspend (eventContext: LLMCallStartingContext) -> Unit)

    /**
     * Append handler called after a response is received from the language model.
     */
    public fun onLLMCallCompleted(handler: suspend (eventContext: LLMCallCompletedContext) -> Unit)

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    /**
     * Append handler called when a tool is about to be called.
     */
    public fun onToolCallStarting(handler: suspend (eventContext: ToolCallStartingContext) -> Unit)

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    public fun onToolValidationFailed(handler: suspend (eventContext: ToolValidationFailedContext) -> Unit)

    /**
     * Append handler called when a tool call fails with an exception.
     */
    public fun onToolCallFailed(handler: suspend (eventContext: ToolCallFailedContext) -> Unit)

    /**
     * Append handler called when a tool call completes successfully.
     */
    public fun onToolCallCompleted(handler: suspend (eventContext: ToolCallCompletedContext) -> Unit)

    //endregion Tool Call Handlers

    //region Stream Handlers

    /**
     * Registers a handler to be invoked before streaming from a language model begins.
     *
     * This handler is called immediately before starting a streaming operation,
     * allowing you to perform preprocessing, validation, or logging of the streaming request.
     *
     * @param handler The handler function that receives a [LLMStreamingStartingContext] containing
     *                the run ID, prompt, model, and available tools for the streaming session.
     *
     * Example:
     * ```
     * onLLMStreamingStarting { eventContext ->
     *     logger.info("Starting stream for run: ${eventContext.runId}")
     *     logger.debug("Prompt: ${eventContext.prompt}")
     * }
     * ```
     */
    public fun onLLMStreamingStarting(handler: suspend (eventContext: LLMStreamingStartingContext) -> Unit)

    /**
     * Registers a handler to be invoked when stream frames are received during streaming.
     *
     * This handler is called for each stream frame as it arrives from the language model,
     * enabling real-time processing, monitoring, or aggregation of streaming content.
     *
     * @param handler The handler function that receives a [LLMStreamingFrameReceivedContext] containing
     *                the run ID and the stream frame with partial response data.
     *
     * Example:
     * ```
     * onLLMStreamingFrameReceived { eventContext ->
     *     when (val frame = eventContext.streamFrame) {
     *         is StreamFrame.Append -> processText(frame.text)
     *         is StreamFrame.ToolCall -> processTool(frame)
     *     }
     * }
     * ```
     */
    public fun onLLMStreamingFrameReceived(handler: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit)

    /**
     * Registers a handler to be invoked when an error occurs during streaming.
     *
     * This handler is called when an error occurs during streaming,
     * allowing you to perform error handling or logging.
     *
     * @param handler The handler function that receives a [LLMStreamingFailedContext] containing
     *                the run ID, prompt, model, and tools that were used for the streaming session.
     *
     * Example:
     * ```
     * onLLMStreamingFailed { eventContext ->
     *     logger.error("Stream error for run: ${eventContext.runId}")
     * }
     * ```
     */
    public fun onLLMStreamingFailed(handler: suspend (eventContext: LLMStreamingFailedContext) -> Unit)

    /**
     * Registers a handler to be invoked after streaming from a language model completes.
     *
     * This handler is called when the streaming operation finishes,
     * allowing you to perform post-processing, cleanup, or final logging operations.
     *
     * @param handler The handler function that receives an [LLMStreamingCompletedContext] containing
     *                the run ID, prompt, model, and tools that were used for the streaming session.
     *
     * Example:
     * ```
     * onLLMStreamingCompleted { eventContext ->
     *     logger.info("Stream completed for run: ${eventContext.runId}")
     *     // Perform any cleanup or aggregation of collected stream data
     * }
     * ```
     */
    public fun onLLMStreamingCompleted(handler: suspend (eventContext: LLMStreamingCompletedContext) -> Unit)

    //endregion Stream Handlers

    //region Deprecated Handlers

    /**
     * Append handler called when an agent is started.
     */
    @Deprecated(
        message = "Use onAgentStarting instead",
        ReplaceWith("onAgentStarting(handler)", "ai.koog.agents.core.feature.handler.AgentStartingContext")
    )
    public fun onBeforeAgentStarted(handler: suspend (eventContext: AgentStartContext) -> Unit)

    /**
     * Append handler called when an agent finishes execution.
     */
    @Deprecated(
        message = "Use onAgentCompleted instead",
        ReplaceWith("onAgentCompleted(handler)", "ai.koog.agents.core.feature.handler.AgentCompletedContext")
    )
    public fun onAgentFinished(handler: suspend (eventContext: AgentFinishedContext) -> Unit)

    /**
     * Append handler called when an error occurs during agent execution.
     */
    @Deprecated(
        message = "Use onAgentExecutionFailed instead",
        ReplaceWith(
            "onAgentExecutionFailed(handler)",
            "ai.koog.agents.core.feature.handler.AgentExecutionFailedContext"
        )
    )
    public fun onAgentRunError(handler: suspend (eventContext: AgentRunErrorContext) -> Unit)

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    @Deprecated(
        message = "Use onAgentClosing instead",
        ReplaceWith("onAgentClosing(handler)", "ai.koog.agents.core.feature.handler.AgentClosingContext")
    )
    public fun onAgentBeforeClose(handler: suspend (eventContext: AgentBeforeCloseContext) -> Unit)

    /**
     * Append handler called when a strategy starts execution.
     */
    @Deprecated(
        message = "Use onStrategyStarting instead",
        ReplaceWith("onStrategyStarting(handler)", "ai.koog.agents.core.feature.handler.StrategyStartingContext")
    )
    public fun onStrategyStarted(handler: suspend (eventContext: StrategyStartContext) -> Unit)

    /**
     * Append handler called when a strategy finishes execution.
     */
    @Deprecated(
        message = "Use onStrategyCompleted instead",
        ReplaceWith("onStrategyCompleted(handler)", "ai.koog.agents.core.feature.handler.StrategyCompletedContext")
    )
    public fun onStrategyFinished(handler: suspend (eventContext: StrategyFinishedContext) -> Unit)

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    @Deprecated(
        message = "Use onNodeExecutionStarting instead",
        ReplaceWith(
            "onNodeExecutionStarting(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionStartingContext"
        )
    )
    public fun onBeforeNode(handler: suspend (eventContext: NodeBeforeExecuteContext) -> Unit)

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    @Deprecated(
        message = "Use onNodeExecutionCompleted instead",
        ReplaceWith(
            "onNodeExecutionCompleted(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionCompletedContext"
        )
    )
    public fun onAfterNode(handler: suspend (eventContext: NodeAfterExecuteContext) -> Unit)

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    @Deprecated(
        message = "Use onNodeExecutionError instead",
        ReplaceWith("onNodeExecutionFailed(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionFailedContext")
    )
    public fun onNodeExecutionError(handler: suspend (eventContext: NodeExecutionErrorContext) -> Unit)

    /**
     * Append handler called before a call is made to the language model.
     */
    @Deprecated(
        message = "Use onLLMCallStarting instead",
        ReplaceWith("onLLMCallStarting(handler)", "ai.koog.agents.core.feature.handler.LLMCallStartingContext")
    )
    public fun onBeforeLLMCall(handler: suspend (eventContext: BeforeLLMCallContext) -> Unit)

    /**
     * Append handler called after a response is received from the language model.
     */
    @Deprecated(
        message = "Use onLLMCallCompleted instead",
        ReplaceWith("onLLMCallCompleted(handler)", "ai.koog.agents.core.feature.handler.LLMCallCompletedContext")
    )
    public fun onAfterLLMCall(handler: suspend (eventContext: AfterLLMCallContext) -> Unit)

    /**
     * Append handler called when a tool is about to be called.
     */
    @Deprecated(
        message = "Use onToolCallStarting instead",
        ReplaceWith("onToolCallStarting(handler)", "ai.koog.agents.core.feature.handler.ToolCallStartingContext")
    )
    public fun onToolCall(handler: suspend (eventContext: ToolCallContext) -> Unit)

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    @Deprecated(
        message = "Use onToolValidationFailed instead",
        ReplaceWith(
            "onToolValidationFailed(handler)",
            "ai.koog.agents.core.feature.handler.ToolValidationFailedContext"
        )
    )
    public fun onToolValidationError(handler: suspend (eventContext: ToolValidationErrorContext) -> Unit)

    /**
     * Append handler called when a tool call fails with an exception.
     */
    @Deprecated(
        message = "Use onToolCallFailed instead",
        ReplaceWith("onToolCallFailed(handler)", "ai.koog.agents.core.feature.handler.ToolCallFailedContext")
    )
    public fun onToolCallFailure(handler: suspend (eventContext: ToolCallFailureContext) -> Unit)

    /**
     * Append handler called when a tool call completes successfully.
     */
    @Deprecated(
        message = "Use onToolCallCompleted instead",
        ReplaceWith("onToolCallCompleted(handler)", "ai.koog.agents.core.feature.handler.ToolCallCompletedContext")
    )
    public fun onToolCallResult(handler: suspend (eventContext: ToolCallResultContext) -> Unit)

    //endregion Deprecated Handlers

    //region Invoke Agent Handlers

    /**
     * Invoke handlers for an event when an agent is started.
     */
    @InternalAgentsApi
    public suspend fun invokeOnAgentStarting(eventContext: AgentStartingContext)

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnAgentCompleted(eventContext: AgentCompletedContext)

    /**
     * Invoke handlers for an event when an error occurs during agent execution.
     */
    @InternalAgentsApi
    public suspend fun invokeOnAgentExecutionFailed(eventContext: AgentExecutionFailedContext)

    /**
     * Invokes the handler associated with the event that occurs before an agent is closed.
     */
    @InternalAgentsApi
    public suspend fun invokeOnAgentClosing(eventContext: AgentClosingContext)

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    /**
     * Invoke handlers for an event when strategy starts execution.
     */
    @InternalAgentsApi
    public suspend fun invokeOnStrategyStarting(eventContext: StrategyStartingContext)

    /**
     * Invoke handlers for an event when a strategy finishes execution.
     */
    @InternalAgentsApi
    public suspend fun invokeOnStrategyCompleted(eventContext: StrategyCompletedContext)

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    /**
     * Invoke handlers for before a node in the agent's execution graph is processed event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnNodeExecutionStarting(eventContext: NodeExecutionStartingContext)

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnNodeExecutionCompleted(eventContext: NodeExecutionCompletedContext)

    /**
     * Invokes the error handling logic for a node execution error event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnNodeExecutionFailed(interceptContext: NodeExecutionFailedContext)

    //endregion Invoke Node Handlers

    //region Invoke LLM Call Handlers

    /**
     * Invoke handlers for before a call is made to the language model event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnLLMCallStarting(eventContext: LLMCallStartingContext)

    /**
     * Invoke handlers for after a response is received from the language model event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnLLMCallCompleted(eventContext: LLMCallCompletedContext)

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    /**
     * Invoke handlers for the tool call event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnToolCallStarting(eventContext: ToolCallStartingContext)

    /**
     * Invoke handlers for a validation error during a tool call event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnToolValidationFailed(eventContext: ToolValidationFailedContext)

    /**
     * Invoke handlers for a tool call failure with an exception event.
     */
    @InternalAgentsApi
    public suspend fun invokeOnToolCallFailed(eventContext: ToolCallFailedContext)

    /**
     * Invoke handlers for an event when a tool call is completed successfully.
     */
    @InternalAgentsApi
    public suspend fun invokeOnToolCallCompleted(eventContext: ToolCallCompletedContext)

    //endregion Invoke Tool Call Handlers

    //region Invoke Stream Handlers

    /**
     * Invokes the handler associated with the event that occurs before streaming starts.
     *
     * @param eventContext The context containing information about the streaming session about to begin
     */
    @InternalAgentsApi
    public suspend fun invokeOnLLMStreamingStarting(eventContext: LLMStreamingStartingContext)

    /**
     * Invokes the handler associated with stream frame events during streaming.
     *
     * @param eventContext The context containing the stream frame data
     */
    @InternalAgentsApi
    public suspend fun invokeOnLLMStreamingFrameReceived(eventContext: LLMStreamingFrameReceivedContext)

    /**
     * Invokes the handler associated with the event that occurs when an error occurs during streaming.
     *
     * @param eventContext The context containing information about the streaming session that experienced the error
     */
    @InternalAgentsApi
    public suspend fun invokeOnLLMStreamingFailed(eventContext: LLMStreamingFailedContext)

    /**
     * Invokes the handler associated with the event that occurs after streaming completes.
     *
     * @param eventContext The context containing information about the completed streaming session
     */
    @InternalAgentsApi
    public suspend fun invokeOnLLMStreamingCompleted(eventContext: LLMStreamingCompletedContext)

    //endregion Invoke Stream Handlers
}
