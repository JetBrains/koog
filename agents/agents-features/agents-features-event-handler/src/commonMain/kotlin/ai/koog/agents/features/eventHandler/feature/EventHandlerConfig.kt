package ai.koog.agents.features.eventHandler.feature

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
public class EventHandlerConfig : FeatureConfig() {

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

    private var _onLLMStreamingFrameReceived: suspend (eventHandler: LLMStreamingFrameReceivedContext) -> Unit = { _ -> }

    private var _onLLMStreamingFailed: suspend (eventHandler: LLMStreamingFailedContext) -> Unit = { _ -> }

    private var _onLLMStreamingCompleted: suspend (eventHandler: LLMStreamingCompletedContext) -> Unit = { _ -> }

    //endregion Private Stream Handlers

    //region Agent Handlers

    /**
     * Append handler called when an agent is started.
     */
    public fun onAgentStarting(handler: suspend (eventContext: AgentStartingContext) -> Unit) {
        val originalHandler = this._onAgentStarting
        this._onAgentStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    public fun onAgentCompleted(handler: suspend (eventContext: AgentCompletedContext) -> Unit) {
        val originalHandler = this._onAgentCompleted
        this._onAgentCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    public fun onAgentExecutionFailed(handler: suspend (eventContext: AgentExecutionFailedContext) -> Unit) {
        val originalHandler = this._onAgentExecutionFailed
        this._onAgentExecutionFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    public fun onAgentClosing(handler: suspend (eventContext: AgentClosingContext) -> Unit) {
        val originalHandler = this._onAgentClosing
        this._onAgentClosing = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Trigger Agent Handlers

    //region Strategy Handlers

    /**
     * Append handler called when a strategy starts execution.
     */
    public fun onStrategyStarting(handler: suspend (eventContext: StrategyStartingContext) -> Unit) {
        val originalHandler = this._onStrategyStarting
        this._onStrategyStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    public fun onStrategyCompleted(handler: suspend (eventContext: StrategyCompletedContext) -> Unit) {
        val originalHandler = this._onStrategyCompleted
        this._onStrategyCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Strategy Handlers

    //region Node Handlers

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    public fun onNodeExecutionStarting(handler: suspend (eventContext: NodeExecutionStartingContext) -> Unit) {
        val originalHandler = this._onNodeExecutionStarting
        this._onNodeExecutionStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    public fun onNodeExecutionCompleted(handler: suspend (eventContext: NodeExecutionCompletedContext) -> Unit) {
        val originalHandler = this._onNodeExecutionCompleted
        this._onNodeExecutionCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    public fun onNodeExecutionFailed(handler: suspend (eventContext: NodeExecutionFailedContext) -> Unit) {
        val originalHandler = this._onNodeExecutionFailed
        this._onNodeExecutionFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Node Handlers

    //region LLM Call Handlers

    /**
     * Append handler called before a call is made to the language model.
     */
    public fun onLLMCallStarting(handler: suspend (eventContext: LLMCallStartingContext) -> Unit) {
        val originalHandler = this._onLLMCallStarting
        this._onLLMCallStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    public fun onLLMCallCompleted(handler: suspend (eventContext: LLMCallCompletedContext) -> Unit) {
        val originalHandler = this._onLLMCallCompleted
        this._onLLMCallCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    /**
     * Append handler called when a tool is about to be called.
     */
    public fun onToolCallStarting(handler: suspend (eventContext: ToolCallStartingContext) -> Unit) {
        val originalHandler = this._onToolCallStarting
        this._onToolCallStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    public fun onToolValidationFailed(handler: suspend (eventContext: ToolValidationFailedContext) -> Unit) {
        val originalHandler = this._onToolValidationFailed
        this._onToolValidationFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    public fun onToolCallFailed(handler: suspend (eventContext: ToolCallFailedContext) -> Unit) {
        val originalHandler = this._onToolCallFailed
        this._onToolCallFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    public fun onToolCallCompleted(handler: suspend (eventContext: ToolCallCompletedContext) -> Unit) {
        val originalHandler = this._onToolCallCompleted
        this._onToolCallCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

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
    public fun onLLMStreamingStarting(handler: suspend (eventContext: LLMStreamingStartingContext) -> Unit) {
        val originalHandler = this._onLLMStreamingStarting
        this._onLLMStreamingStarting = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

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
    public fun onLLMStreamingFrameReceived(handler: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit) {
        val originalHandler = this._onLLMStreamingFrameReceived
        this._onLLMStreamingFrameReceived = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

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
    public fun onLLMStreamingFailed(handler: suspend (eventContext: LLMStreamingFailedContext) -> Unit) {
        val originalHandler = this._onLLMStreamingFailed
        this._onLLMStreamingFailed = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

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
    public fun onLLMStreamingCompleted(handler: suspend (eventContext: LLMStreamingCompletedContext) -> Unit) {
        val originalHandler = this._onLLMStreamingCompleted
        this._onLLMStreamingCompleted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Stream Handlers

    //region Deprecated Handlers

    /**
     * Append handler called when an agent is started.
     */
    @Deprecated(
        message = "Use onAgentStarting instead",
        ReplaceWith("onAgentStarting(handler)", "ai.koog.agents.core.feature.handler.AgentStartingContext")
    )
    public fun onBeforeAgentStarted(handler: suspend (eventContext: AgentStartContext) -> Unit) {
        onAgentStarting(handler)
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    @Deprecated(
        message = "Use onAgentCompleted instead",
        ReplaceWith("onAgentCompleted(handler)", "ai.koog.agents.core.feature.handler.AgentCompletedContext")
    )
    public fun onAgentFinished(handler: suspend (eventContext: AgentFinishedContext) -> Unit) {
        onAgentCompleted(handler)
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    @Deprecated(
        message = "Use onAgentExecutionFailed instead",
        ReplaceWith("onAgentExecutionFailed(handler)", "ai.koog.agents.core.feature.handler.AgentExecutionFailedContext")
    )
    public fun onAgentRunError(handler: suspend (eventContext: AgentRunErrorContext) -> Unit) {
        onAgentExecutionFailed(handler)
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    @Deprecated(
        message = "Use onAgentClosing instead",
        ReplaceWith("onAgentClosing(handler)", "ai.koog.agents.core.feature.handler.AgentClosingContext")
    )
    public fun onAgentBeforeClose(handler: suspend (eventContext: AgentBeforeCloseContext) -> Unit) {
        onAgentClosing(handler)
    }

    /**
     * Append handler called when a strategy starts execution.
     */
    @Deprecated(
        message = "Use onStrategyStarting instead",
        ReplaceWith("onStrategyStarting(handler)", "ai.koog.agents.core.feature.handler.StrategyStartingContext")
    )
    public fun onStrategyStarted(handler: suspend (eventContext: StrategyStartContext) -> Unit) {
        onStrategyStarting(handler)
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    @Deprecated(
        message = "Use onStrategyCompleted instead",
        ReplaceWith("onStrategyCompleted(handler)", "ai.koog.agents.core.feature.handler.StrategyCompletedContext")
    )
    public fun onStrategyFinished(handler: suspend (eventContext: StrategyFinishedContext) -> Unit) {
        onStrategyCompleted(handler)
    }

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    @Deprecated(
        message = "Use onNodeExecutionStarting instead",
        ReplaceWith("onNodeExecutionStarting(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionStartingContext")
    )
    public fun onBeforeNode(handler: suspend (eventContext: NodeBeforeExecuteContext) -> Unit) {
        onNodeExecutionStarting(handler)
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    @Deprecated(
        message = "Use onNodeExecutionCompleted instead",
        ReplaceWith("onNodeExecutionCompleted(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionCompletedContext")
    )
    public fun onAfterNode(handler: suspend (eventContext: NodeAfterExecuteContext) -> Unit) {
        onNodeExecutionCompleted(handler)
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    @Deprecated(
        message = "Use onNodeExecutionError instead",
        ReplaceWith("onNodeExecutionError(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionFailedContext")
    )
    public fun onNodeExecutionError(handler: suspend (eventContext: NodeExecutionErrorContext) -> Unit) {
        onNodeExecutionFailed(handler)
    }

    /**
     * Append handler called before a call is made to the language model.
     */
    @Deprecated(
        message = "Use onLLMCallStarting instead",
        ReplaceWith("onLLMCallStarting(handler)", "ai.koog.agents.core.feature.handler.LLMCallStartingContext")
    )
    public fun onBeforeLLMCall(handler: suspend (eventContext: BeforeLLMCallContext) -> Unit) {
        onLLMCallStarting(handler)
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    @Deprecated(
        message = "Use onLLMCallCompleted instead",
        ReplaceWith("onLLMCallCompleted(handler)", "ai.koog.agents.core.feature.handler.LLMCallCompletedContext")
    )
    public fun onAfterLLMCall(handler: suspend (eventContext: AfterLLMCallContext) -> Unit) {
        onLLMCallCompleted(handler)
    }

    /**
     * Append handler called when a tool is about to be called.
     */
    @Deprecated(
        message = "Use onToolCallStarting instead",
        ReplaceWith("onToolCallStarting(handler)", "ai.koog.agents.core.feature.handler.ToolCallStartingContext")
    )
    public fun onToolCall(handler: suspend (eventContext: ToolCallContext) -> Unit) {
        onToolCallStarting(handler)
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    @Deprecated(
        message = "Use onToolValidationFailed instead",
        ReplaceWith("onToolValidationFailed(handler)", "ai.koog.agents.core.feature.handler.ToolValidationFailedContext")
    )
    public fun onToolValidationError(handler: suspend (eventContext: ToolValidationErrorContext) -> Unit) {
        onToolValidationFailed(handler)
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    @Deprecated(
        message = "Use onToolCallFailed instead",
        ReplaceWith("onToolCallFailed(handler)", "ai.koog.agents.core.feature.handler.ToolCallFailedContext")
    )
    public fun onToolCallFailure(handler: suspend (eventContext: ToolCallFailureContext) -> Unit) {
        onToolCallFailed(handler)
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    @Deprecated(
        message = "Use onToolCallCompleted instead",
        ReplaceWith("onToolCallCompleted(handler)", "ai.koog.agents.core.feature.handler.ToolCallCompletedContext")
    )
    public fun onToolCallResult(handler: suspend (eventContext: ToolCallResultContext) -> Unit) {
        onToolCallCompleted(handler)
    }

    //endregion Deprecated Handlers

    //region Invoke Agent Handlers

    /**
     * Invoke handlers for an event when an agent is started.
     */
    internal suspend fun invokeOnAgentStarting(eventContext: AgentStartingContext) {
        _onAgentStarting.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAgentCompleted(eventContext: AgentCompletedContext) {
        _onAgentCompleted.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when an error occurs during agent execution.
     */
    internal suspend fun invokeOnAgentExecutionFailed(eventContext: AgentExecutionFailedContext) {
        _onAgentExecutionFailed.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs before an agent is closed.
     */
    internal suspend fun invokeOnAgentClosing(eventContext: AgentClosingContext) {
        _onAgentClosing.invoke(eventContext)
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    /**
     * Invoke handlers for an event when strategy starts execution.
     */
    internal suspend fun invokeOnStrategyStarting(eventContext: StrategyStartingContext) {
        _onStrategyStarting.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when a strategy finishes execution.
     */
    internal suspend fun invokeOnStrategyCompleted(eventContext: StrategyCompletedContext) {
        _onStrategyCompleted.invoke(eventContext)
    }

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    /**
     * Invoke handlers for before a node in the agent's execution graph is processed event.
     */
    internal suspend fun invokeOnNodeExecutionStarting(eventContext: NodeExecutionStartingContext) {
        _onNodeExecutionStarting.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnNodeExecutionCompleted(eventContext: NodeExecutionCompletedContext) {
        _onNodeExecutionCompleted.invoke(eventContext)
    }

    /**
     * Invokes the error handling logic for a node execution error event.
     */
    internal suspend fun invokeOnNodeExecutionFailed(interceptContext: NodeExecutionFailedContext) {
        _onNodeExecutionFailed.invoke(interceptContext)
    }

    //endregion Invoke Node Handlers

    //region Invoke LLM Call Handlers

    /**
     * Invoke handlers for before a call is made to the language model event.
     */
    internal suspend fun invokeOnLLMCallStarting(eventContext: LLMCallStartingContext) {
        _onLLMCallStarting.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a response is received from the language model event.
     */
    internal suspend fun invokeOnLLMCallCompleted(eventContext: LLMCallCompletedContext) {
        _onLLMCallCompleted.invoke(eventContext)
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    /**
     * Invoke handlers for the tool call event.
     */
    internal suspend fun invokeOnToolCallStarting(eventContext: ToolCallStartingContext) {
        _onToolCallStarting.invoke(eventContext)
    }

    /**
     * Invoke handlers for a validation error during a tool call event.
     */
    internal suspend fun invokeOnToolValidationFailed(eventContext: ToolValidationFailedContext) {
        _onToolValidationFailed.invoke(eventContext)
    }

    /**
     * Invoke handlers for a tool call failure with an exception event.
     */
    internal suspend fun invokeOnToolCallFailed(eventContext: ToolCallFailedContext) {
        _onToolCallFailed.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when a tool call is completed successfully.
     */
    internal suspend fun invokeOnToolCallCompleted(eventContext: ToolCallCompletedContext) {
        _onToolCallCompleted.invoke(eventContext)
    }

    //endregion Invoke Tool Call Handlers

    //region Invoke Stream Handlers

    /**
     * Invokes the handler associated with the event that occurs before streaming starts.
     *
     * @param eventContext The context containing information about the streaming session about to begin
     */
    internal suspend fun invokeOnLLMStreamingStarting(eventContext: LLMStreamingStartingContext) {
        _onLLMStreamingStarting.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with stream frame events during streaming.
     *
     * @param eventContext The context containing the stream frame data
     */
    internal suspend fun invokeOnLLMStreamingFrameReceived(eventContext: LLMStreamingFrameReceivedContext) {
        _onLLMStreamingFrameReceived.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs when an error occurs during streaming.
     *
     * @param eventContext The context containing information about the streaming session that experienced the error
     */
    internal suspend fun invokeOnLLMStreamingFailed(eventContext: LLMStreamingFailedContext) {
        _onLLMStreamingFailed.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs after streaming completes.
     *
     * @param eventContext The context containing information about the completed streaming session
     */
    internal suspend fun invokeOnLLMStreamingCompleted(eventContext: LLMStreamingCompletedContext) {
        _onLLMStreamingCompleted.invoke(eventContext)
    }

    //endregion Invoke Stream Handlers
}
