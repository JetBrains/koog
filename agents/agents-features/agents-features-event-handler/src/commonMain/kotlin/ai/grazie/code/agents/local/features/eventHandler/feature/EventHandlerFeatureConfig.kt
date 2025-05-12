package ai.grazie.code.agents.local.features.eventHandler.feature

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.local.features.common.config.FeatureConfig
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

class EventHandlerFeatureConfig : FeatureConfig() {

    //region Trigger Agent Handlers

    var onAgentCreated: suspend (strategy: LocalAgentStrategy, agent: AIAgentBase) -> Unit =
        { strategy: LocalAgentStrategy, agent: AIAgentBase -> }

    var onAgentStarted: suspend (strategyName: String) -> Unit =
        { strategyName: String -> }

    var onAgentFinished: suspend (strategyName: String, result: String?) -> Unit =
        { strategyName: String, result: String? -> }

    var onAgentRunError: suspend (strategyName: String, throwable: Throwable) -> Unit =
        { strategyName: String, throwable: Throwable -> }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    var onStrategyStarted: suspend (strategy: LocalAgentStrategy) -> Unit =
        { strategy: LocalAgentStrategy -> }

    var onStrategyFinished: suspend (strategyName: String, result: String) -> Unit =
        { strategyName: String, result: String -> }

    //endregion Trigger Strategy Handlers

    //region Trigger Node Handlers

    var onBeforeNode: suspend (node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?) -> Unit =
        { node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any? -> }

    var onAfterNode: suspend (node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any?) -> Unit =
        { node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any? -> }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    var onBeforeLLMCall: (prompt: Prompt) -> Unit =
        { prompt: Prompt -> }

    var onBeforeLLMWithToolsCall: (prompt: Prompt, tools: List<ToolDescriptor>) -> Unit =
        { prompt: Prompt, tools: List<ToolDescriptor> -> }

    var onAfterLLMCall: (response: String) -> Unit =
        { response: String -> }

    var onAfterLLMWithToolsCall: (response: List<Message.Response>, tools: List<ToolDescriptor>) -> Unit =
        { response: List<Message.Response>, tools: List<ToolDescriptor> -> }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    var onBeforeToolCalls: suspend (tools: List<Message.Tool.Call>) -> Unit =
        { tools: List<Message.Tool.Call> -> }

    var onAfterToolCalls: suspend (tools: List<Message.Tool.Call>, results: List<ReceivedToolResult>) -> Unit =
        { tools: List<Message.Tool.Call>, results: List<ReceivedToolResult> -> }

    var onToolCall: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args -> }

    var onToolValidationError: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String -> }

    var onToolCallFailure: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable -> }

    var onToolCallResult: suspend (stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit =
        { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? -> }

    //endregion Trigger Tool Call Handlers
}