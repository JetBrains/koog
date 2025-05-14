package ai.grazie.code.agents.local.features.eventHandler.feature

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.core.dsl.extension.nodeLLMRequest
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventHandlerTest {

    private val collectedEvents = mutableListOf<String>()

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onAgentCreated = { strategy: AIAgentStrategy, agent: AIAgent ->
            collectedEvents.add("OnAgentCreated (strategy: ${strategy.name})")
        }

        onAgentStarted = { strategyName: String ->
            collectedEvents.add("OnAgentStarted (strategy: $strategyName)")
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

        onBeforeNode = { node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any? ->
            collectedEvents.add("OnBeforeNode (node: ${node.name}, input: $input)")
        }

        onAfterNode = { node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any?, output: Any? ->
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

        onToolCall = { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args ->
            collectedEvents.add("OnToolCall (stage: ${stage.name}, tool: ${tool.name}, args: $toolArgs)")
        }

        onToolValidationError = { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String ->
            collectedEvents.add("OnToolValidationError (stage: ${stage.name}, tool: ${tool.name}, args: $toolArgs, value: $value)")
        }

        onToolCallFailure = { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable ->
            collectedEvents.add("OnToolCallFailure (stage: ${stage.name}, tool: ${tool.name}, args: $toolArgs, throwable: ${throwable.message})")
        }

        onToolCallResult = { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult? ->
            collectedEvents.add("OnToolCallResult (stage: ${stage.name}, tool: ${tool.name}, args: $toolArgs, result: $result)")
        }
    }

    @AfterTest
    fun cleanUpEvents() {
        collectedEvents.clear()
    }

    @Test
    fun `test event handler process defined events`() = runBlocking {

        val strategyName = "tracing-test-strategy"

        val strategy = simpleStrategy(strategyName) {
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

        agent.run("")

        val expectedEvents = listOf(
            "OnAgentCreated (strategy: $strategyName)",
            "OnAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${Unit::class.qualifiedName})",
            "OnAfterNode (node: __start__, input: ${Unit::class.qualifiedName}, output: ${Unit::class.qualifiedName})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMWithToolsCall (prompt: [System(content=Test system message), User(content=Test user message), Assistant(content=Test assistant response), User(content=Test LLM call prompt)], tools: [dummy, __tools_list__])",
            "OnAfterLLMWithToolsCall (response: [Default test response], tools: [dummy, __tools_list__])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response))",
            "OnBeforeNode (node: test LLM call with tools, input: Test LLM call with tools prompt)",
            "OnBeforeLLMWithToolsCall (prompt: [System(content=Test system message), User(content=Test user message), Assistant(content=Test assistant response), User(content=Test LLM call prompt), Assistant(content=Default test response), User(content=Test LLM call with tools prompt)], tools: [dummy, __tools_list__])",
            "OnAfterLLMWithToolsCall (response: [Default test response], tools: [dummy, __tools_list__])",
            "OnAfterNode (node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response))",
            "OnStrategyFinished (strategy: $strategyName, result: Done)",
            "OnAgentFinished (strategy: $strategyName, result: Done)",
        )

        assertEquals(expectedEvents.size, collectedEvents.size)
        assertContentEquals(expectedEvents, collectedEvents)
    }

}