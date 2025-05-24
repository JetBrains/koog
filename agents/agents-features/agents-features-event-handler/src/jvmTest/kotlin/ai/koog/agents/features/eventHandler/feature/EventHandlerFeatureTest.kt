package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventHandlerTest {

    private val collectedEvents = mutableListOf<String>()

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onBeforeAgentStarted = { strategy: AIAgentStrategy, agent: AIAgent ->
            collectedEvents.add("OnBeforeAgentStarted (strategy: ${strategy.name})")
        }

        onAgentFinished = { strategyName: String, result: String? ->
            collectedEvents.add("OnAgentFinished (strategy: $strategyName, result: $result)")
        }

        onAgentRunError = { strategyName: String, throwable: Throwable ->
            collectedEvents.add("OnAgentRunError (strategy: $strategyName, throwable: ${throwable.message})")
        }

        onStrategyStarted = { strategy: AIAgentStrategy ->
            collectedEvents.add("OnStrategyStarted (strategy: ${strategy.name})")
        }

        onStrategyFinished = { strategyName: String, result: String ->
            collectedEvents.add("OnStrategyFinished (strategy: $strategyName, result: $result)")
        }

        onBeforeNode = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
            collectedEvents.add("OnBeforeNode (node: ${node.name}, input: $input)")
        }

        onAfterNode = { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
            collectedEvents.add("OnAfterNode (node: ${node.name}, input: $input, output: $output)")
        }

        onBeforeLLMCall = { prompt: Prompt ->
            collectedEvents.add("OnBeforeLLMCall (prompt: ${prompt.messages})")
        }

        onBeforeLLMWithToolsCall = { prompt: Prompt, tools: List<ToolDescriptor> ->
            collectedEvents.add("OnBeforeLLMWithToolsCall (prompt: ${prompt.messages}, tools: [${tools.joinToString(", ") { it.name } }])")
        }

        onAfterLLMCall = { response: String ->
            collectedEvents.add("OnAfterLLMCall (response: $response)")
        }

        onAfterLLMWithToolsCall = { response: List<Message.Response>, tools: List<ToolDescriptor> ->
            collectedEvents.add("OnAfterLLMWithToolsCall (response: [${response.joinToString(", ") { response -> response.content }}], tools: [${tools.joinToString(", ") { it.name } }])")
        }

        onToolCall = { tool: Tool<*, *>, toolArgs: Tool.Args ->
            collectedEvents.add("OnToolCall (tool: ${tool.name}, args: $toolArgs)")
        }

        onToolValidationError = { tool: Tool<*, *>, toolArgs: Tool.Args, value: String ->
            collectedEvents.add("OnToolValidationError (tool: ${tool.name}, args: $toolArgs, value: $value)")
        }

        onToolCallFailure = { tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable ->
            collectedEvents.add("OnToolCallFailure (tool: ${tool.name}, args: $toolArgs, throwable: ${throwable.message})")
        }

        onToolCallResult = { tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? ->
            collectedEvents.add("OnToolCallResult (tool: ${tool.name}, args: $toolArgs, result: $result)")
        }
    }

    @AfterTest
    fun cleanUpEvents() {
        collectedEvents.clear()
    }

    @Test
    fun `test event handler process defined events`() = runBlocking {

        val strategyName = "tracing-test-strategy"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler, eventHandlerConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMWithToolsCall (prompt: [System(content=Test system message), User(content=Test user message), Assistant(content=Test assistant response, finishReason=null), User(content=Test LLM call prompt)], tools: [dummy])",
            "OnAfterLLMWithToolsCall (response: [Default test response], tools: [dummy])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, finishReason=null))",
            "OnBeforeNode (node: test LLM call with tools, input: Test LLM call with tools prompt)",
            "OnBeforeLLMWithToolsCall (prompt: [System(content=Test system message), User(content=Test user message), Assistant(content=Test assistant response, finishReason=null), User(content=Test LLM call prompt), Assistant(content=Default test response, finishReason=null), User(content=Test LLM call with tools prompt)], tools: [dummy])",
            "OnAfterLLMWithToolsCall (response: [Default test response], tools: [dummy])",
            "OnAfterNode (node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response, finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: Done)",
            "OnAgentFinished (strategy: $strategyName, result: Done)",
        )

        println("EXPECTED:\n${expectedEvents.joinToString("\n")}\n\nACTUAL:\n${collectedEvents.joinToString("\n")}")

        assertEquals(expectedEvents.size, collectedEvents.size)
        assertContentEquals(expectedEvents, collectedEvents)
    }

}