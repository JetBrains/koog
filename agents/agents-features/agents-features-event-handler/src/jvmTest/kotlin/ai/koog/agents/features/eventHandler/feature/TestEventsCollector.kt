package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

class TestEventsCollector {

    val size: Int
        get() = collectedEvents.size

    private val _collectedEvents = mutableListOf<String>()

    val collectedEvents: List<String>
        get() = _collectedEvents.toList()

    val eventHandlerFeatureConfig: EventHandlerConfig.() -> Unit = {

        onBeforeAgentStarted = { strategy: AIAgentStrategy, agent: AIAgent ->
            _collectedEvents.add("OnBeforeAgentStarted (strategy: ${strategy.name})")
        }

        onAgentFinished = { strategyName: String, result: String? ->
            _collectedEvents.add("OnAgentFinished (strategy: $strategyName, result: $result)")
        }

        onAgentRunError = { strategyName: String, throwable: Throwable ->
            _collectedEvents.add("OnAgentRunError (strategy: $strategyName, throwable: ${throwable.message})")
        }

        onStrategyStarted = { strategy: AIAgentStrategy ->
            _collectedEvents.add("OnStrategyStarted (strategy: ${strategy.name})")
        }

        onStrategyFinished = { strategyName: String, result: String ->
            _collectedEvents.add("OnStrategyFinished (strategy: $strategyName, result: $result)")
        }

        onBeforeNode = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
            _collectedEvents.add("OnBeforeNode (node: ${node.name}, input: $input)")
        }

        onAfterNode = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
            _collectedEvents.add("OnAfterNode (node: ${node.name}, input: $input, output: $output)")
        }

        onBeforeLLMCall = { prompt: Prompt, tools: List<ToolDescriptor> ->
            _collectedEvents.add("OnBeforeLLMCall (prompt: ${prompt.messages}, tools: [${tools.joinToString { it.name } }])")
        }

        onAfterLLMCall = { responses: List<Message.Response>, tools: List<ToolDescriptor> ->
            _collectedEvents.add("OnAfterLLMCall (responses: [${responses.joinToString { "${it.role.name}: ${it.content}" }}], tools: [${tools.joinToString { it.name } }])")
        }

        onToolCall = { tool: Tool<*, *>, toolArgs: Tool.Args ->
            _collectedEvents.add("OnToolCall (tool: ${tool.name}, args: $toolArgs)")
        }

        onToolValidationError = { tool: Tool<*, *>, toolArgs: Tool.Args, value: String ->
            _collectedEvents.add("OnToolValidationError (tool: ${tool.name}, args: $toolArgs, value: $value)")
        }

        onToolCallFailure = { tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable ->
            _collectedEvents.add("OnToolCallFailure (tool: ${tool.name}, args: $toolArgs, throwable: ${throwable.message})")
        }

        onToolCallResult = { tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? ->
            _collectedEvents.add("OnToolCallResult (tool: ${tool.name}, args: $toolArgs, result: $result)")
        }
    }

    fun reset() {
        _collectedEvents.clear()
    }
}
