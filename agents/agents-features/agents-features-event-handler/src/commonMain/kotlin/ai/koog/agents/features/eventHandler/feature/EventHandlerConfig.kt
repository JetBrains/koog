package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
@OptIn(ExperimentalUuidApi::class)
public class EventHandlerConfig : FeatureConfig() {

    //region Agent Handlers

    private var _onBeforeAgentStarted: suspend (strategy: AIAgentStrategy, agent: AIAgent) -> Unit =
        { strategy: AIAgentStrategy, agent: AIAgent -> }

    private var _onAgentFinished: suspend (strategyName: String, result: String?) -> Unit =
        { strategyName: String, result: String? -> }

    private var _onAgentRunError: suspend (strategyName: String, sessionUuid: Uuid?, throwable: Throwable) -> Unit =
        { strategyName: String, sessionUuid: Uuid?, throwable: Throwable -> }

    //endregion Agent Handlers

    //region Strategy Handlers

    private var _onStrategyStarted: suspend (strategy: AIAgentStrategy) -> Unit =
        { strategy: AIAgentStrategy -> }

    private var _onStrategyFinished: suspend (strategy: AIAgentStrategy, result: String) -> Unit =
        { strategy: AIAgentStrategy, result: String -> }

    //endregion Strategy Handlers

    //region Node Handlers

    private var _onBeforeNode: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) -> Unit =
        { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? -> }

    private var _onAfterNode: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any?) -> Unit =
        { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? -> }

    //endregion Node Handlers

    //region LLM Call Handlers

    private var _onBeforeLLMCall: suspend (prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid) -> Unit =
        { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid -> }

    private var _onAfterLLMCall: suspend (prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid) -> Unit =
        { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid -> }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    private var _onToolCall: suspend (tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit =
        { tool: Tool<*, *>, toolArgs: Tool.Args -> }

    private var _onToolValidationError: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit =
        { tool: Tool<*, *>, toolArgs: Tool.Args, value: String -> }

    private var _onToolCallFailure: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit =
        { tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable -> }

    private var _onToolCallResult: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit =
        { tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? -> }

    //endregion Tool Call Handlers


    //region Deprecated Agent Handlers

    /**
     * A handler invoked before an AI agent is started.
     *
     * Deprecated: Use the corresponding `onBeforeAgentStarted` function instead to append event handlers.
     *
     * The handler is a suspendable function that receives an `AIAgentStrategy` and an `AIAgent` as parameters. It can be used
     * to perform custom logic or setup tasks before the agent's execution begins.
     *
     * To ensure future compatibility, transition to the recommended function-based approach for appending handlers.
     */
    @Deprecated(message = "Please use onBeforeAgentStarted() instead", replaceWith = ReplaceWith("onBeforeAgentStarted(handler)"))
    public var onBeforeAgentStarted: suspend (strategy: AIAgentStrategy, agent: AIAgent) -> Unit = { strategy: AIAgentStrategy, agent: AIAgent -> }
        set(value) = this.onBeforeAgentStarted(value)

    /**
     * A deprecated handler invoked when an agent finishes execution.
     *
     * Provides the name of the strategy and an optional result of the execution.
     *
     * It is recommended to use the `onAgentFinished()` function instead to append handlers.
     *
     * @param strategyName The name of the strategy associated with the finished agent.
     * @param result The result of the execution, if available; otherwise, null.
     * @deprecated Use `onAgentFinished(handler)` instead.
     */
    @Deprecated(message = "Please use onAgentFinished() instead", replaceWith = ReplaceWith("onAgentFinished(handler)"))
    public var onAgentFinished: suspend (strategyName: String, result: String?) -> Unit = { strategyName: String, result: String? -> }
        set(value) = this.onAgentFinished(value)

    /**
     * A deprecated variable used to define a handler that is called when an error occurs during agent execution.
     *
     * This handler is invoked with the strategy name, an optional session UUID, and the throwable that caused the error.
     *
     * @deprecated Use the `onAgentRunError` function instead for appending custom error handlers.
     */
    @Deprecated(message = "Please use onAgentRunError() instead", replaceWith = ReplaceWith("onAgentRunError(handler)"))
    public var onAgentRunError: suspend (strategyName: String, sessionUuid: Uuid?, throwable: Throwable) -> Unit = { strategyName: String, sessionUuid: Uuid?, throwable: Throwable -> }
        set(value) = this.onAgentRunError(value)

    //endregion Deprecated Agent Handlers

    //region Deprecated Strategy Handlers

    /**
     * A suspendable handler invoked when a strategy starts execution in the AI Agent workflow.
     *
     * This property is deprecated and replaced by the `onStrategyStarted(handler)` function for appending handlers.
     *
     * The handler receives an `AIAgentStrategy` instance, which represents the strategy being executed.
     *
     * @deprecated Use `onStrategyStarted(handler)` instead for appending multiple handlers.
     * Replace this property with the `onStrategyStarted(handler)` function for better extensibility.
     */
    @Deprecated(message = "Please use onStrategyStarted() instead", replaceWith = ReplaceWith("onStrategyStarted(handler)"))
    public var onStrategyStarted: suspend (strategy: AIAgentStrategy) -> Unit = { strategy: AIAgentStrategy -> }
        set(value) = this.onStrategyStarted(value)

    /**
     * A deprecated variable that defines a handler to be invoked when a strategy finishes execution.
     * Replaced by the `onStrategyFinished(handler)` method to provide a more structured and extensible approach.
     *
     * @deprecated Use `onStrategyFinished(handler)` instead for appending handlers.
     * This variable is retained for backward compatibility but is not the recommended approach.
     */
    @Deprecated(message = "Please use onStrategyFinished() instead", replaceWith = ReplaceWith("onStrategyFinished(handler)"))
    public var onStrategyFinished: suspend (strategy: AIAgentStrategy, result: String) -> Unit = { strategy: AIAgentStrategy, result: String -> }
        set(value) = this.onStrategyFinished(value)

    //endregion Deprecated Strategy Handlers

    //region Deprecated Node Handlers

    /**
     * A handler invoked before a node in the agent's execution graph is processed.
     *
     * This property is deprecated and should be replaced with the `onBeforeNode` method.
     * It accepts a suspend function that takes the following parameters:
     * - `node`: The node being processed.
     * - `context`: The context in which the node is being executed.
     * - `input`: The input provided to the node.
     *
     * Deprecated: Use the `onBeforeNode(handler)` method for appending handlers to the event.
     */
    @Deprecated(message = "Please use onBeforeNode() instead", replaceWith = ReplaceWith("onBeforeNode(handler)"))
    public var onBeforeNode: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) -> Unit = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? -> }
        set(value) = this.onBeforeNode(value)

    /**
     * A deprecated variable used to define a handler that is called after a node
     * in the agent's execution graph has been processed.
     *
     * The handler is a suspend function that receives the following parameters:
     * - `node`: The node that was processed, represented by an instance of `AIAgentNodeBase`.
     * - `context`: The context of the agent containing relevant execution state and data.
     * - `input`: The input passed to the node during processing.
     * - `output`: The output produced after the node was processed.
     *
     * It is recommended to use the function `onAfterNode(handler)` to set the handler,
     * as this variable is deprecated.
     */
    @Deprecated(message = "Please use onAfterNode() instead", replaceWith = ReplaceWith("onAfterNode(handler)"))
    public var onAfterNode: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any?) -> Unit = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? -> }
        set(value) = this.onAfterNode(value)

    //endregion Deprecated Node Handlers

    //region Deprecated LLM Call Handlers

    /**
     * Deprecated variable used to define a handler that is invoked before a call is made to the language model.
     *
     * It allows custom logic to be executed prior to making a call to the language model with the given prompt,
     * tools, model, and session UUID.
     *
     * @deprecated Use the `onBeforeLLMCall(handler)` function to achieve the same functionality.
     */
    @Deprecated(message = "Please use onBeforeLLMCall() instead", replaceWith = ReplaceWith("onBeforeLLMCall(handler)"))
    public var onBeforeLLMCall: suspend (prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid) -> Unit = { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid -> }
        set(value) = this.onBeforeLLMCall(value)

    /**
     * A deprecated property to handle events triggered after a response is received from the language model (LLM).
     *
     * Use the `onAfterLLMCall(handler: suspend (prompt, tools, model, responses, sessionUuid) -> Unit)` method instead.
     *
     * The handler is a suspending function that is executed after an LLM call and receives the following parameters:
     * - `prompt`: The prompt that was sent to the language model.
     * - `tools`: A list of available tool descriptors.
     * - `model`: The language model instance that processed the request.
     * - `responses`: A list of responses returned by the language model.
     * - `sessionUuid`: The unique identifier for the session in which this call occurred.
     *
     * Updating this property will automatically delegate to the newer `onAfterLLMCall` method.
     */
    @Deprecated(message = "Please use onAfterLLMCall() instead", replaceWith = ReplaceWith("onAfterLLMCall(handler)"))
    public var onAfterLLMCall: suspend (prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid) -> Unit = { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid -> }
        set(value) = this.onAfterLLMCall(value)

    //endregion Deprecated LLM Call Handlers

    //region Deprecated Tool Call Handlers

    /**
     * A deprecated variable for appending a handler called when a tool is about to be invoked.
     *
     * Use the `onToolCall` function to properly append a handler for tool invocation events.
     *
     * @deprecated Use `onToolCall(handler)` instead for appending handlers in a preferred manner.
     */
    @Deprecated(message = "Please use onToolCall() instead", replaceWith = ReplaceWith("onToolCall(handler)"))
    public var onToolCall: suspend (tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit = { tool: Tool<*, *>, toolArgs: Tool.Args -> }
        set(value) = this.onToolCall(value)

    /**
     * A deprecated variable representing the handler invoked when a validation error occurs during a tool call.
     * Use `onToolValidationError(handler)` instead to register error handling logic.
     *
     * The handler receives the following parameters:
     * - `tool`: The tool instance where the validation error occurred.
     * - `toolArgs`: The arguments provided to the tool during the call.
     * - `value`: The string representing the invalid value or other contextual information about the error.
     *
     * This property is deprecated and maintained for backward compatibility.
     */
    @Deprecated(message = "Please use onToolValidationError() instead", replaceWith = ReplaceWith("onToolValidationError(handler)"))
    public var onToolValidationError: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit = { tool: Tool<*, *>, toolArgs: Tool.Args, value: String -> }
        set(value) = this.onToolValidationError(value)

    /**
     * Defines a handler that is invoked when a tool call fails due to an exception.
     *
     * This property is deprecated and will be removed in future versions.
     * Use the `onToolCallFailure(handler: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit)` function instead to add handlers for tool call failure events
     * .
     *
     * Replacing this property with the newer `onToolCallFailure` function ensures better consistency and management of handlers.
     */
    @Deprecated(message = "Please use onToolCallFailure() instead", replaceWith = ReplaceWith("onToolCallFailure(handler)"))
    public var onToolCallFailure: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit = { tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable -> }
        set(value) = this.onToolCallFailure(value)

    /**
     * Deprecated variable representing a handler invoked when a tool call is completed successfully.
     * The handler is a suspend function with parameters for the tool, its arguments, and the result of the tool call.
     *
     * @deprecated Use the `onToolCallResult(handler)` function instead. This property will be removed in future versions.
     * @see onToolCallResult
     */
    @Deprecated(message = "Please use onToolCallResult() instead", replaceWith = ReplaceWith("onToolCallResult(handler)"))
    public var onToolCallResult: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit = { tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? -> }
        set(value) = this.onToolCallResult(value)

    //endregion Deprecated Tool Call Handlers


    //region Agent Handlers

    /**
     * Append handler called when an agent is started.
     */
    public fun onBeforeAgentStarted(handler: suspend (strategy: AIAgentStrategy, agent: AIAgent) -> Unit) {
        val originalHandler = this._onBeforeAgentStarted
        this._onBeforeAgentStarted = { strategy: AIAgentStrategy, agent: AIAgent ->
            originalHandler(strategy, agent)
            handler.invoke(strategy, agent)
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    public fun onAgentFinished(handler: suspend (strategyName: String, result: String?) -> Unit) {
        val originalHandler = this._onAgentFinished
        this._onAgentFinished = { strategyName: String, result: String? ->
            originalHandler(strategyName, result)
            handler.invoke(strategyName, result)
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    public fun onAgentRunError(handler: suspend (strategyName: String, sessionUuid: Uuid?, throwable: Throwable) -> Unit) {
        val originalHandler = this._onAgentRunError
        this._onAgentRunError = { strategyName: String, sessionUuid: Uuid?, throwable: Throwable ->
            originalHandler(strategyName, sessionUuid, throwable)
            handler.invoke(strategyName, sessionUuid, throwable)
        }
    }

    //endregion Trigger Agent Handlers

    //region Strategy Handlers

    /**
     * Append handler called when a strategy starts execution.
     */
    public fun onStrategyStarted(handler: suspend (strategy: AIAgentStrategy) -> Unit) {
        val originalHandler = this._onStrategyStarted
        this._onStrategyStarted = { strategy: AIAgentStrategy ->
            originalHandler(strategy)
            handler.invoke(strategy)
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    public fun onStrategyFinished(handler: suspend (strategy: AIAgentStrategy, result: String) -> Unit) {
        val originalHandler = this._onStrategyFinished
        this._onStrategyFinished = { strategy: AIAgentStrategy, result: String ->
            originalHandler(strategy, result)
            handler.invoke(strategy, result)
        }
    }

    //endregion Strategy Handlers

    //region Node Handlers

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    public fun onBeforeNode(handler: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) -> Unit) {
        val originalHandler = this._onBeforeNode
        this._onBeforeNode = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
            originalHandler(node, context, input)
            handler.invoke(node, context, input)
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    public fun onAfterNode(handler: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any?) -> Unit) {
        val originalHandler = this._onAfterNode
        this._onAfterNode = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
            originalHandler(node, context, input, output)
            handler.invoke(node, context, input, output)
        }
    }

    //endregion Node Handlers

    //region LLM Call Handlers

    /**
     * Append handler called before a call is made to the language model.
     */
    public fun onBeforeLLMCall(handler: suspend (prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid) -> Unit) {
        val originalHandler = this._onBeforeLLMCall
        this._onBeforeLLMCall = { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid ->
            originalHandler(prompt, tools, model, sessionUuid)
            handler.invoke(prompt, tools, model, sessionUuid)
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    public fun onAfterLLMCall(handler: suspend (prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid) -> Unit) {
        val originalHandler = this._onAfterLLMCall
        this._onAfterLLMCall = { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid ->
            originalHandler(prompt, tools, model, responses, sessionUuid)
            handler.invoke(prompt, tools, model, responses, sessionUuid)
        }
    }

    //endregion LLM Call Handlers

    //region Tool Call Handlers

    /**
     * Append handler called when a tool is about to be called.
     */
    public fun onToolCall(handler: suspend (tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit) {
        val originalHandler = this._onToolCall
        this._onToolCall = { tool: Tool<*, *>, toolArgs: Tool.Args ->
            originalHandler(tool, toolArgs)
            handler.invoke(tool, toolArgs)
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    public fun onToolValidationError(handler: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit) {
        val originalHandler = this._onToolValidationError
        this._onToolValidationError = { tool: Tool<*, *>, toolArgs: Tool.Args, value: String ->
            originalHandler(tool, toolArgs, value)
            handler.invoke(tool, toolArgs, value)
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    public fun onToolCallFailure(handler: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit) {
        val originalHandler = this._onToolCallFailure
        this._onToolCallFailure = { tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable ->
            originalHandler(tool, toolArgs, throwable)
            handler.invoke(tool, toolArgs, throwable)
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    public fun onToolCallResult(handler: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit) {
        val originalHandler = this._onToolCallResult
        this._onToolCallResult = { tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? ->
            originalHandler(tool, toolArgs, result)
            handler.invoke(tool, toolArgs, result)
        }
    }

    //endregion Tool Call Handlers


    //region Invoke Agent Handlers

    /**
     * Invoke handlers for an event when an agent is started.
     */
    internal suspend fun invokeOnBeforeAgentStarted(strategy: AIAgentStrategy, agent: AIAgent) {
        _onBeforeAgentStarted.invoke(strategy, agent)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAgentFinished(strategyName: String, result: String?) {
        _onAgentFinished.invoke(strategyName, result)
    }

    /**
     * Invoke handlers for an event when an error occurs during agent execution.
     */
    internal suspend fun invokeOnAgentRunError(strategyName: String, sessionUuid: Uuid?, throwable: Throwable) {
        _onAgentRunError.invoke(strategyName, sessionUuid, throwable)
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    /**
     * Invoke handlers for an event when strategy starts execution.
     */
    internal suspend fun invokeOnStrategyStarted(strategy: AIAgentStrategy) {
        _onStrategyStarted.invoke(strategy)
    }

    /**
     * Invoke handlers for an event when a strategy finishes execution.
     */
    internal suspend fun invokeOnStrategyFinished(strategy: AIAgentStrategy, result: String) {
        _onStrategyFinished.invoke(strategy, result)
    }

    //endregion Invoke Strategy Handlers

    //region Invoke Node Handlers

    /**
     * Invoke handlers for before a node in the agent's execution graph is processed event.
     */
    internal suspend fun invokeOnBeforeNode(node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) {
        _onBeforeNode.invoke(node, context, input)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal suspend fun invokeOnAfterNode(node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any?) {
        _onAfterNode.invoke(node, context, input, output)
    }

    //endregion Invoke Node Handlers

    //region Invoke LLM Call Handlers

    /**
     * Invoke handlers for before a call is made to the language model event.
     */
    internal suspend fun invokeOnBeforeLLMCall(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid) {
        _onBeforeLLMCall.invoke(prompt, tools, model, sessionUuid)
    }

    /**
     * Invoke handlers for after a response is received from the language model event.
     */
    internal suspend fun invokeOnAfterLLMCall(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid) {
        _onAfterLLMCall.invoke(prompt, tools, model, responses, sessionUuid)
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    /**
     * Invoke handlers for tool call event.
     */
    internal suspend fun invokeOnToolCall(tool: Tool<*, *>, toolArgs: Tool.Args) {
        _onToolCall.invoke(tool, toolArgs)
    }

    /**
     * Invoke handlers for a validation error during a tool call event.
     */
    internal suspend fun invokeOnToolValidationError(tool: Tool<*, *>, toolArgs: Tool.Args, value: String) {
        _onToolValidationError.invoke(tool, toolArgs, value)
    }

    /**
     * Invoke handlers for a tool call failure with an exception event.
     */
    internal suspend fun invokeOnToolCallFailure(tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) {
        _onToolCallFailure.invoke(tool, toolArgs, throwable)
    }

    /**
     * Invoke handlers for an event when a tool call is completed successfully.
     */
    internal suspend fun invokeOnToolCallResult(tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) {
        _onToolCallResult.invoke(tool, toolArgs, result)
    }

    //endregion Invoke Tool Call Handlers
}
