package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
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
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
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
    @Deprecated(
        message = "Please use onBeforeAgentStarted() instead",
        replaceWith = ReplaceWith("onBeforeAgentStarted(handler)")
    )
    public var onBeforeAgentStarted: suspend (
        strategy: AIAgentStrategy<*, *>,
        agent: AIAgent<*, *>
    ) -> Unit = { strategy: AIAgentStrategy<*, *>, agent: AIAgent<*, *> -> }
        set(value) {
            this.onBeforeAgentStarted { eventContext ->
                value(eventContext.strategy, eventContext.agent)
            }
        }

    /**
     * A deprecated handler invoked when an agent finishes execution.
     *
     * Provides the name of the strategy and an optional result of the execution.
     *
     * It is recommended to use the `onAgentFinished()` function instead to append handlers.
     *
     * @deprecated Use `onAgentFinished(handler)` instead.
     */
    @Deprecated(message = "Please use onAgentFinished() instead", replaceWith = ReplaceWith("onAgentFinished(handler)"))
    public var onAgentFinished: suspend (
        strategyName: String,
        result: Any?
    ) -> Unit = { strategyName: String, result: Any? -> }
        set(value) {
            this.onAgentFinished { eventContext ->
                value("", eventContext.result)
            }
        }

    /**
     * A deprecated variable used to define a handler that is called when an error occurs during agent execution.
     *
     * This handler is invoked with the strategy name, an optional session UUID, and the throwable that caused the error.
     *
     * @deprecated Use the `onAgentRunError` function instead for appending custom error handlers.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Deprecated(message = "Please use onAgentRunError() instead", replaceWith = ReplaceWith("onAgentRunError(handler)"))
    public var onAgentRunError: suspend (
        strategyName: String,
        sessionUuid: Uuid?,
        throwable: Throwable
    ) -> Unit = { strategyName: String, sessionUuid: Uuid?, throwable: Throwable -> }
        set(value) {
            this.onAgentRunError { eventContext ->
                value("", Uuid.parse(eventContext.runId), eventContext.throwable)
            }
        }

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
    @Deprecated(
        message = "Please use onStrategyStarted() instead",
        replaceWith = ReplaceWith("onStrategyStarted(handler)")
    )
    public var onStrategyStarted: suspend (
        strategy: AIAgentStrategy<*, *>
    ) -> Unit = { strategy: AIAgentStrategy<*, *> -> }
        set(value) {
            this.onStrategyStarted { eventContext ->
                value(eventContext.strategy)
            }
        }

    /**
     * A deprecated variable that defines a handler to be invoked when a strategy finishes execution.
     * Replaced by the `onStrategyFinished(handler)` method to provide a more structured and extensible approach.
     *
     * @deprecated Use `onStrategyFinished(handler)` instead for appending handlers.
     * This variable is retained for backward compatibility but is not the recommended approach.
     */
    @Deprecated(
        message = "Please use onStrategyFinished() instead",
        replaceWith = ReplaceWith("onStrategyFinished(handler)")
    )
    public var onStrategyFinished: suspend (
        strategy: AIAgentStrategy<*, *>,
        result: Any?
    ) -> Unit = { strategy: AIAgentStrategy<*, *>, result: Any? -> }
        set(value) {
            this.onStrategyFinished { eventContext ->
                value(eventContext.strategy, eventContext.result)
            }
        }

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
    public var onBeforeNode: suspend (
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?
    ) -> Unit = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? -> }
        set(value) {
            this.onBeforeNode { eventContext ->
                value(eventContext.node, eventContext.context, eventContext.input)
            }
        }

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
    public var onAfterNode: suspend (
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?,
        output: Any?
    ) -> Unit = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? -> }
        set(value) {
            this.onAfterNode { eventContext ->
                value(eventContext.node, eventContext.context, eventContext.input, eventContext.output)
            }
        }

    //endregion Deprecated Node Handlers

    //region Deprecated LLM Call Handlers

    /**
     * Deprecated variable used to define a handler that is invoked before a call is made to the language model.
     *
     * It allows custom logic to be executed before making a call to the language model with the given prompt,
     * tools, model, and session UUID.
     *
     * @deprecated Use the `onBeforeLLMCall(handler)` function to achieve the same functionality.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Deprecated(message = "Please use onBeforeLLMCall() instead", replaceWith = ReplaceWith("onBeforeLLMCall(handler)"))
    public var onBeforeLLMCall: suspend (
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        sessionUuid: Uuid
    ) -> Unit = { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid -> }
        set(value) {
            this.onBeforeLLMCall { eventContext ->
                value(eventContext.prompt, eventContext.tools, eventContext.model, Uuid.parse(eventContext.runId))
            }
        }

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
    @OptIn(ExperimentalUuidApi::class)
    @Deprecated(message = "Please use onAfterLLMCall() instead", replaceWith = ReplaceWith("onAfterLLMCall(handler)"))
    public var onAfterLLMCall: suspend (
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        responses: List<Message.Response>,
        sessionUuid: Uuid
    ) -> Unit = {
            prompt: Prompt,
            tools: List<ToolDescriptor>,
            model: LLModel,
            responses: List<Message.Response>,
            sessionUuid: Uuid
        ->
    }
        set(value) {
            this.onAfterLLMCall { eventContext ->
                value(
                    eventContext.prompt,
                    eventContext.tools,
                    eventContext.model,
                    eventContext.responses,
                    Uuid.parse(eventContext.runId)
                )
            }
        }

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
    public var onToolCall: suspend (
        tool: Tool<*, *>,
        toolArgs: ToolArgs
    ) -> Unit = { tool: Tool<*, *>, toolArgs: ToolArgs -> }
        set(value) {
            this.onToolCall { eventContext ->
                value(eventContext.tool, eventContext.toolArgs)
            }
        }

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
    @Deprecated(
        message = "Please use onToolValidationError() instead",
        replaceWith = ReplaceWith("onToolValidationError(handler)")
    )
    public var onToolValidationError: suspend (
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        value: String
    ) -> Unit = { tool: Tool<*, *>, toolArgs: ToolArgs, value: String -> }
        set(value) {
            this.onToolValidationError { eventContext ->
                value(eventContext.tool, eventContext.toolArgs, eventContext.error)
            }
        }

    /**
     * Defines a handler invoked when a tool call fails due to an exception.
     *
     * This property is deprecated and will be removed in future versions.
     * Use the `onToolCallFailure(handler: suspend (tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit)` function instead to add handlers for tool call failure events.
     *
     * Replacing this property with the newer `onToolCallFailure` function ensures better consistency and management of handlers.
     */
    @Deprecated(
        message = "Please use onToolCallFailure() instead",
        replaceWith = ReplaceWith("onToolCallFailure(handler)")
    )
    public var onToolCallFailure: suspend (
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        throwable: Throwable
    ) -> Unit = { tool: Tool<*, *>, toolArgs: ToolArgs, throwable: Throwable -> }
        set(value) {
            this.onToolCallFailure { eventContext ->
                value(eventContext.tool, eventContext.toolArgs, eventContext.throwable)
            }
        }

    /**
     * Deprecated variable representing a handler invoked when a tool call is completed successfully.
     * The handler is a suspend function with parameters for the tool, its arguments, and the result of the tool call.
     *
     * @deprecated Use the `onToolCallResult(handler)` function instead. This property will be removed in future versions.
     * @see onToolCallResult
     */
    @Deprecated(
        message = "Please use onToolCallResult() instead",
        replaceWith = ReplaceWith("onToolCallResult(handler)")
    )
    public var onToolCallResult: suspend (
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        result: ToolResult?
    ) -> Unit = { tool: Tool<*, *>, toolArgs: ToolArgs, result: ToolResult? -> }
        set(value) {
            this.onToolCallResult { eventContext ->
                value(eventContext.tool, eventContext.toolArgs, eventContext.result)
            }
        }

    //endregion Deprecated Tool Call Handlers

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
