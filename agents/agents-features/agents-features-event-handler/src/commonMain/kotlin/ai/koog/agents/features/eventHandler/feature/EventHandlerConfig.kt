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
import ai.koog.agents.core.feature.handler.StrategyFinishContext
import ai.koog.agents.core.feature.handler.StrategyStartContext
import ai.koog.agents.core.feature.handler.ToolCallContext
import ai.koog.agents.core.feature.handler.ToolCallFailureContext
import ai.koog.agents.core.feature.handler.ToolCallResultContext
import ai.koog.agents.core.feature.handler.ToolValidationErrorContext

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

    private var _onBeforeAgentStarted: suspend (eventHandler: AgentStartContext<EventHandler>) -> Unit = { _ -> }

    private var _onAgentFinished: suspend (eventHandler: AgentFinishedContext) -> Unit = { _ -> }

    private var _onAgentRunError: suspend (eventHandler: AgentRunErrorContext) -> Unit = { _ -> }

    private var _onAgentBeforeClose: suspend (eventHandler: AgentBeforeCloseContext) -> Unit = { _ -> }

    //endregion Agent Handlers

    //region Strategy Handlers

    private var _onStrategyStarted: suspend (eventHandler: StrategyStartContext<EventHandler>) -> Unit = { _ -> }

    private var _onStrategyFinished: suspend (eventHandler: StrategyFinishContext<EventHandler>) -> Unit = { _ -> }

    //endregion Strategy Handlers

    //region Node Handlers

    private var _onBeforeNode: suspend (eventHandler: NodeBeforeExecuteContext) -> Unit = { _ -> }

    private var _onAfterNode: suspend (eventHandler: NodeAfterExecuteContext) -> Unit = { _ -> }

    private var _onNodeExecutionError: suspend (eventHandler: NodeExecutionErrorContext) -> Unit = { _ -> }

    //endregion Node Handlers

    //region LLM Call Handlers

    private var _onBeforeLLMCall: suspend (eventHandler: BeforeLLMCallContext) -> Unit = { _ -> }

    private var _onAfterLLMCall: suspend (eventHandler: AfterLLMCallContext) -> Unit = { _ -> }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    private var _onToolCall: suspend (eventHandler: ToolCallContext) -> Unit = { _ -> }

    private var _onToolValidationError: suspend (eventHandler: ToolValidationErrorContext) -> Unit = { _ -> }

    private var _onToolCallFailure: suspend (eventHandler: ToolCallFailureContext) -> Unit = { _ -> }

    private var _onToolCallResult: suspend (eventHandler: ToolCallResultContext) -> Unit = { _ -> }

    //endregion Tool Call Handlers

    //region Agent Handlers

    /**
     * Append handler called when an agent is started.
     */
    public fun onBeforeAgentStarted(handler: suspend (eventContext: AgentStartContext<*>) -> Unit) {
        val originalHandler = this._onBeforeAgentStarted
        this._onBeforeAgentStarted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    public fun onAgentFinished(handler: suspend (eventContext: AgentFinishedContext) -> Unit) {
        val originalHandler = this._onAgentFinished
        this._onAgentFinished = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    public fun onAgentRunError(handler: suspend (eventContext: AgentRunErrorContext) -> Unit) {
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
    public fun onAgentBeforeClose(handler: suspend (eventContext: AgentBeforeCloseContext) -> Unit) {
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
    public fun onStrategyStarted(handler: suspend (eventContext: StrategyStartContext<EventHandler>) -> Unit) {
        val originalHandler = this._onStrategyStarted
        this._onStrategyStarted = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    public fun onStrategyFinished(handler: suspend (eventContext: StrategyFinishContext<EventHandler>) -> Unit) {
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
    public fun onBeforeNode(handler: suspend (eventContext: NodeBeforeExecuteContext) -> Unit) {
        val originalHandler = this._onBeforeNode
        this._onBeforeNode = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    public fun onAfterNode(handler: suspend (eventContext: NodeAfterExecuteContext) -> Unit) {
        val originalHandler = this._onAfterNode
        this._onAfterNode = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    public fun onNodeExecutionError(handler: suspend (eventContext: NodeExecutionErrorContext) -> Unit) {
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
    public fun onBeforeLLMCall(handler: suspend (eventContext: BeforeLLMCallContext) -> Unit) {
        val originalHandler = this._onBeforeLLMCall
        this._onBeforeLLMCall = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    public fun onAfterLLMCall(handler: suspend (eventContext: AfterLLMCallContext) -> Unit) {
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
    public fun onToolCall(handler: suspend (eventContext: ToolCallContext) -> Unit) {
        val originalHandler = this._onToolCall
        this._onToolCall = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    public fun onToolValidationError(handler: suspend (eventContext: ToolValidationErrorContext) -> Unit) {
        val originalHandler = this._onToolValidationError
        this._onToolValidationError = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    public fun onToolCallFailure(handler: suspend (eventContext: ToolCallFailureContext) -> Unit) {
        val originalHandler = this._onToolCallFailure
        this._onToolCallFailure = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    public fun onToolCallResult(handler: suspend (eventContext: ToolCallResultContext) -> Unit) {
        val originalHandler = this._onToolCallResult
        this._onToolCallResult = { eventContext ->
            originalHandler(eventContext)
            handler.invoke(eventContext)
        }
    }

    //endregion Tool Call Handlers

    //region Invoke Agent Handlers

    /**
     * Invoke handlers for an event when an agent is started.
     */
    internal suspend fun invokeOnBeforeAgentStarted(eventContext: AgentStartContext<EventHandler>) {
        _onBeforeAgentStarted.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAgentFinished(eventContext: AgentFinishedContext) {
        _onAgentFinished.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when an error occurs during agent execution.
     */
    internal suspend fun invokeOnAgentRunError(eventContext: AgentRunErrorContext) {
        _onAgentRunError.invoke(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs before an agent is closed.
     */
    internal suspend fun invokeOnAgentBeforeClose(eventContext: AgentBeforeCloseContext) {
        _onAgentBeforeClose.invoke(eventContext)
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    /**
     * Invoke handlers for an event when strategy starts execution.
     */
    internal suspend fun invokeOnStrategyStarted(eventContext: StrategyStartContext<EventHandler>) {
        _onStrategyStarted.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when a strategy finishes execution.
     */
    internal suspend fun invokeOnStrategyFinished(eventContext: StrategyFinishContext<EventHandler>) {
        _onStrategyFinished.invoke(eventContext)
    }

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    /**
     * Invoke handlers for before a node in the agent's execution graph is processed event.
     */
    internal suspend fun invokeOnBeforeNode(eventContext: NodeBeforeExecuteContext) {
        _onBeforeNode.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAfterNode(eventContext: NodeAfterExecuteContext) {
        _onAfterNode.invoke(eventContext)
    }

    /**
     * Invokes the error handling logic for a node execution error event.
     */
    internal suspend fun invokeOnNodeExecutionError(interceptContext: NodeExecutionErrorContext) {
        _onNodeExecutionError.invoke(interceptContext)
    }

    //endregion Invoke Node Handlers

    //region Invoke LLM Call Handlers

    /**
     * Invoke handlers for before a call is made to the language model event.
     */
    internal suspend fun invokeOnBeforeLLMCall(eventContext: BeforeLLMCallContext) {
        _onBeforeLLMCall.invoke(eventContext)
    }

    /**
     * Invoke handlers for after a response is received from the language model event.
     */
    internal suspend fun invokeOnAfterLLMCall(eventContext: AfterLLMCallContext) {
        _onAfterLLMCall.invoke(eventContext)
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    /**
     * Invoke handlers for the tool call event.
     */
    internal suspend fun invokeOnToolCall(eventContext: ToolCallContext) {
        _onToolCall.invoke(eventContext)
    }

    /**
     * Invoke handlers for a validation error during a tool call event.
     */
    internal suspend fun invokeOnToolValidationError(eventContext: ToolValidationErrorContext) {
        _onToolValidationError.invoke(eventContext)
    }

    /**
     * Invoke handlers for a tool call failure with an exception event.
     */
    internal suspend fun invokeOnToolCallFailure(eventContext: ToolCallFailureContext) {
        _onToolCallFailure.invoke(eventContext)
    }

    /**
     * Invoke handlers for an event when a tool call is completed successfully.
     */
    internal suspend fun invokeOnToolCallResult(eventContext: ToolCallResultContext) {
        _onToolCallResult.invoke(eventContext)
    }

    //endregion Invoke Tool Call Handlers
}
