package ai.grazie.code.agents.local.features.eventHandler.feature

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.local.features.common.config.FeatureConfig
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

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
 *     onToolCall = { stage, tool, toolArgs ->
 *         println("Tool called: ${tool.name} with args $toolArgs")
 *     }
 *     
 *     onAgentFinished = { strategyName, result ->
 *         println("Agent finished with result: $result")
 *     }
 * }
 * ```
 */
public class EventHandlerConfig : FeatureConfig() {

    //region Trigger Agent Handlers

    /**
     * Handler called when an agent is created.
     */
    public var onBeforeAgentStarted: suspend (strategy: AIAgentStrategy, agent: AIAgent) -> Unit =
        { strategy: AIAgentStrategy, agent: AIAgent -> }

    /**
     * Handler called when an agent finishes execution.
     */
    public var onAgentFinished: suspend (strategyName: String, result: String?) -> Unit =
        { strategyName: String, result: String? -> }

    /**
     * Handler called when an error occurs during agent execution.
     */
    public var onAgentRunError: suspend (strategyName: String, throwable: Throwable) -> Unit =
        { strategyName: String, throwable: Throwable -> }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Handler called when a strategy starts execution.
     */
    public var onStrategyStarted: suspend (strategy: AIAgentStrategy) -> Unit =
        { strategy: AIAgentStrategy -> }

    /**
     * Handler called when a strategy finishes execution.
     */
    public var onStrategyFinished: suspend (strategyName: String, result: String) -> Unit =
        { strategyName: String, result: String -> }

    //endregion Trigger Strategy Handlers

    //region Trigger Node Handlers

    /**
     * Handler called before a node in the agent's execution graph is processed.
     */
    public var onBeforeNode: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any?) -> Unit =
        { node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any? -> }

    /**
     * Handler called after a node in the agent's execution graph has been processed.
     */
    public var onAfterNode: suspend (node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any?, output: Any?) -> Unit =
        { node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any?, output: Any? -> }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    /**
     * Handler called before a call is made to the language model.
     */
    public var onBeforeLLMCall: (prompt: Prompt) -> Unit =
        { prompt: Prompt -> }

    /**
     * Handler called before a call is made to the language model with tools.
     */
    public var onBeforeLLMWithToolsCall: (prompt: Prompt, tools: List<ToolDescriptor>) -> Unit =
        { prompt: Prompt, tools: List<ToolDescriptor> -> }

    /**
     * Handler called after a response is received from the language model.
     */
    public var onAfterLLMCall: (response: String) -> Unit =
        { response: String -> }

    /**
     * Handler called after a response with tool calls is received from the language model.
     */
    public var onAfterLLMWithToolsCall: (response: List<Message.Response>, tools: List<ToolDescriptor>) -> Unit =
        { response: List<Message.Response>, tools: List<ToolDescriptor> -> }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Handler called when a tool is about to be called.
     */
    public var onToolCall: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args -> }

    /**
     * Handler called when a validation error occurs during a tool call.
     */
    public var onToolValidationError: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String -> }

    /**
     * Handler called when a tool call fails with an exception.
     */
    public var onToolCallFailure: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable -> }

    /**
     * Handler called when a tool call completes successfully.
     */
    public var onToolCallResult: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? -> }

    //endregion Trigger Tool Call Handlers
}
