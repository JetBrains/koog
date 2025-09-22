package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext
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
 *     onToolCall { stage, tool, toolArgs ->
 *         println("Tool called: ${tool.name} with args $toolArgs")
 *     }
 *
 *     onAgentFinished { strategyName, result ->
 *         println("Agent finished with result: $result")
 *     }
 * }
 * ```
 */
public class EventHandlerConfig : FeatureConfig() {

    //region Agent Handlers

    private var _onBeforeAgentStarted: suspend (eventHandler: AgentStartingContext<EventHandler>) -> Unit = { _ -> }

    private var _onAgentFinished: suspend (eventHandler: AgentCompletedContext) -> Unit = { _ -> }

    private var _onAgentRunError: suspend (eventHandler: AgentExecutionFailedContext) -> Unit = { _ -> }

    private var _onAgentBeforeClose: suspend (eventHandler: AgentClosingContext) -> Unit = { _ -> }

    //endregion Agent Handlers

    //region Strategy Handlers

    private var _onStrategyStarted: suspend (eventHandler: StrategyStartingContext<EventHandler>) -> Unit = { _ -> }

    private var _onStrategyFinished: suspend (eventHandler: StrategyCompletedContext<EventHandler>) -> Unit = { _ -> }

    //endregion Strategy Handlers

    //region Node Handlers

    private var _onBeforeNode: suspend (eventHandler: NodeExecutionStartingContext) -> Unit = { _ -> }

    private var _onAfterNode: suspend (eventHandler: NodeExecutionCompletedContext) -> Unit = { _ -> }

    private var _onNodeExecutionError: suspend (eventHandler: NodeExecutionFailedContext) -> Unit = { _ -> }

    //endregion Node Handlers

    //region LLM Call Handlers

    private var _onBeforeLLMCall: suspend (eventHandler: LLMCallStartingContext) -> Unit = { _ -> }

    private var _onAfterLLMCall: suspend (eventHandler: LLMCallCompletedContext) -> Unit = { _ -> }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    private var _onToolCall: suspend (eventHandler: ToolExecutionStartingContext) -> Unit = { _ -> }

    private var _onToolValidationError: suspend (eventHandler: ToolValidationFailedContext) -> Unit = { _ -> }

    private var _onToolCallFailure: suspend (eventHandler: ToolExecutionFailedContext) -> Unit = { _ -> }

    private var _onToolCallResult: suspend (eventHandler: ToolExecutionCompletedContext) -> Unit = { _ -> }

    //endregion Tool Call Handlers

    //region Stream Handlers

    private var _onBeforeStream: suspend (eventHandler: LLMStreamingStartingContext) -> Unit = { _ -> }

    private var _onStreamFrame: suspend (eventHandler: LLMStreamingFrameReceivedContext) -> Unit = { _ -> }

    private var _onStreamError: suspend (eventHandler: LLMStreamingFailedContext) -> Unit = { _ -> }

    private var _onAfterStream: suspend (eventHandler: LLMStreamingCompletedContext) -> Unit = { _ -> }

    //endregion Stream Handlers

    //region Agent Handlers

    /**
     * Append handler called when an agent is started.
     */
    public fun onBeforeAgentStarted(handler: suspend (eventContext: AgentStartingContext<*>) -> Unit) {
        val originalHandler = this._onBeforeAgentStarted
        this._onBeforeAgentStarted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    public fun onAgentFinished(handler: suspend (eventContext: AgentCompletedContext) -> Unit) {
        val originalHandler = this._onAgentFinished
        this._onAgentFinished = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    public fun onAgentRunError(handler: suspend (eventContext: AgentExecutionFailedContext) -> Unit) {
        val originalHandler = this._onAgentRunError
        this._onAgentRunError = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    public fun onAgentBeforeClose(handler: suspend (eventContext: AgentClosingContext) -> Unit) {
        val originalHandler = this._onAgentBeforeClose
        this._onAgentBeforeClose = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Trigger Agent Handlers

    //region Strategy Handlers

    /**
     * Append handler called when a strategy starts execution.
     */
    public fun onStrategyStarted(handler: suspend (eventContext: StrategyStartingContext<EventHandler>) -> Unit) {
        val originalHandler = this._onStrategyStarted
        this._onStrategyStarted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    public fun onStrategyFinished(handler: suspend (eventContext: StrategyCompletedContext<EventHandler>) -> Unit) {
        val originalHandler = this._onStrategyFinished
        this._onStrategyFinished = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Strategy Handlers

    //region Node Handlers

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    public fun onBeforeNode(handler: suspend (eventContext: NodeExecutionStartingContext) -> Unit) {
        val originalHandler = this._onBeforeNode
        this._onBeforeNode = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    public fun onAfterNode(handler: suspend (eventContext: NodeExecutionCompletedContext) -> Unit) {
        val originalHandler = this._onAfterNode
        this._onAfterNode = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    public fun onNodeExecutionError(handler: suspend (eventContext: NodeExecutionFailedContext) -> Unit) {
        val originalHandler = this._onNodeExecutionError
        this._onNodeExecutionError = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Node Handlers

    //region LLM Call Handlers

    /**
     * Append handler called before a call is made to the language model.
     */
    public fun onBeforeLLMCall(handler: suspend (eventContext: LLMCallStartingContext) -> Unit) {
        val originalHandler = this._onBeforeLLMCall
        this._onBeforeLLMCall = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    public fun onAfterLLMCall(handler: suspend (eventContext: LLMCallCompletedContext) -> Unit) {
        val originalHandler = this._onAfterLLMCall
        this._onAfterLLMCall = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    /**
     * Append handler called when a tool is about to be called.
     */
    public fun onToolCall(handler: suspend (eventContext: ToolExecutionStartingContext) -> Unit) {
        val originalHandler = this._onToolCall
        this._onToolCall = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    public fun onToolValidationError(handler: suspend (eventContext: ToolValidationFailedContext) -> Unit) {
        val originalHandler = this._onToolValidationError
        this._onToolValidationError = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    public fun onToolCallFailure(handler: suspend (eventContext: ToolExecutionFailedContext) -> Unit) {
        val originalHandler = this._onToolCallFailure
        this._onToolCallFailure = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    public fun onToolCallResult(handler: suspend (eventContext: ToolExecutionCompletedContext) -> Unit) {
        val originalHandler = this._onToolCallResult
        this._onToolCallResult = { eventContext ->
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
     * onBeforeStream { eventContext ->
     *     logger.info("Starting stream for run: ${eventContext.runId}")
     *     logger.debug("Prompt: ${eventContext.prompt}")
     * }
     * ```
     */
    public fun onBeforeStream(handler: suspend (eventContext: LLMStreamingStartingContext) -> Unit) {
        val originalHandler = this._onBeforeStream
        this._onBeforeStream = { eventContext ->
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
     * onStreamFrame { eventContext ->
     *     when (val frame = eventContext.streamFrame) {
     *         is StreamFrame.Append -> processText(frame.text)
     *         is StreamFrame.ToolCall -> processTool(frame)
     *     }
     * }
     * ```
     */
    public fun onStreamFrame(handler: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit) {
        val originalHandler = this._onStreamFrame
        this._onStreamFrame = { eventContext ->
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
     * onStreamError { eventContext ->
     *     logger.error("Stream error for run: ${eventContext.runId}")
     * }
     * ```
     */
    public fun onStreamError(handler: suspend (eventContext: LLMStreamingFailedContext) -> Unit) {
        val originalHandler = this._onStreamError
        this._onStreamError = { eventContext ->
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
     * onAfterStream { eventContext ->
     *     logger.info("Stream completed for run: ${eventContext.runId}")
     *     // Perform any cleanup or aggregation of collected stream data
     * }
     * ```
     */
    public fun onAfterStream(handler: suspend (eventContext: LLMStreamingCompletedContext) -> Unit) {
        val originalHandler = this._onAfterStream
        this._onAfterStream = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Stream Handlers

    //region Invoke Agent Handlers

    /**
     * Invoke handlers for an event when an agent is started.
     */
    internal suspend fun invokeOnBeforeAgentStarted(eventContext: AgentStartingContext<EventHandler>) {
        _onBeforeAgentStarted.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAgentFinished(eventContext: AgentCompletedContext) {
        _onAgentFinished.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when an error occurs during agent execution.
     */
    internal suspend fun invokeOnAgentRunError(eventContext: AgentExecutionFailedContext) {
        _onAgentRunError.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs before an agent is closed.
     */
    internal suspend fun invokeOnAgentBeforeClose(eventContext: AgentClosingContext) {
        _onAgentBeforeClose.invoke(eventContext)
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    /**
     * Invoke handlers for an event when strategy starts execution.
     */
    internal suspend fun invokeOnStrategyStarted(eventContext: StrategyStartingContext<EventHandler>) {
        _onStrategyStarted.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when a strategy finishes execution.
     */
    internal suspend fun invokeOnStrategyFinished(eventContext: StrategyCompletedContext<EventHandler>) {
        _onStrategyFinished.invoke(eventContext)
    }

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    /**
     * Invoke handlers for before a node in the agent's execution graph is processed event.
     */
    internal suspend fun invokeOnBeforeNode(eventContext: NodeExecutionStartingContext) {
        _onBeforeNode.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAfterNode(eventContext: NodeExecutionCompletedContext) {
        _onAfterNode.invoke(eventContext)
    }

    /**
     * Invokes the error handling logic for a node execution error event.
     */
    internal suspend fun invokeOnNodeExecutionError(interceptContext: NodeExecutionFailedContext) {
        _onNodeExecutionError.invoke(interceptContext)
    }

    //endregion Invoke Node Handlers

    //region Invoke LLM Call Handlers

    /**
     * Invoke handlers for before a call is made to the language model event.
     */
    internal suspend fun invokeOnBeforeLLMCall(eventContext: LLMCallStartingContext) {
        _onBeforeLLMCall.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a response is received from the language model event.
     */
    internal suspend fun invokeOnAfterLLMCall(eventContext: LLMCallCompletedContext) {
        _onAfterLLMCall.invoke(eventContext)
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    /**
     * Invoke handlers for the tool call event.
     */
    internal suspend fun invokeOnToolCall(eventContext: ToolExecutionStartingContext) {
        _onToolCall.invoke(eventContext)
    }

    /**
     * Invoke handlers for a validation error during a tool call event.
     */
    internal suspend fun invokeOnToolValidationError(eventContext: ToolValidationFailedContext) {
        _onToolValidationError.invoke(eventContext)
    }

    /**
     * Invoke handlers for a tool call failure with an exception event.
     */
    internal suspend fun invokeOnToolCallFailure(eventContext: ToolExecutionFailedContext) {
        _onToolCallFailure.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when a tool call is completed successfully.
     */
    internal suspend fun invokeOnToolCallResult(eventContext: ToolExecutionCompletedContext) {
        _onToolCallResult.invoke(eventContext)
    }

    //endregion Invoke Tool Call Handlers

    //region Invoke Stream Handlers

    /**
     * Invokes the handler associated with the event that occurs before streaming starts.
     *
     * @param eventContext The context containing information about the streaming session about to begin
     */
    internal suspend fun invokeOnBeforeStream(eventContext: LLMStreamingStartingContext) {
        _onBeforeStream.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with stream frame events during streaming.
     *
     * @param eventContext The context containing the stream frame data
     */
    internal suspend fun invokeOnStreamFrame(eventContext: LLMStreamingFrameReceivedContext) {
        _onStreamFrame.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs when an error occurs during streaming.
     *
     * @param eventContext The context containing information about the streaming session that experienced the error
     */
    internal suspend fun invokeOnStreamError(eventContext: LLMStreamingFailedContext) {
        _onStreamError.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs after streaming completes.
     *
     * @param eventContext The context containing information about the completed streaming session
     */
    internal suspend fun invokeOnAfterStream(eventContext: LLMStreamingCompletedContext) {
        _onAfterStream.invoke(eventContext)
    }

    //endregion Invoke Stream Handlers
}
