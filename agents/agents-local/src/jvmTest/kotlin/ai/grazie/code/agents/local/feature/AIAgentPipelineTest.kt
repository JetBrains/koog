package ai.grazie.code.agents.local.feature

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.KotlinAIAgent.FeatureContext
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.grazie.code.agents.local.calculator.CalculatorChatExecutor
import ai.grazie.code.agents.local.calculator.CalculatorTools.PlusTool
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.simpleStrategy
import ai.grazie.code.agents.local.dsl.extensions.*
import ai.grazie.code.agents.testing.tools.DummyTool
import ai.grazie.code.agents.testing.tools.getMockExecutor
import ai.grazie.code.agents.testing.tools.mockLLMAnswer
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AIAgentPipelineTest {

    @Test
    fun `test pipeline interceptors for node events`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()

        val strategy = simpleStrategy("test-interceptors-strategy") {
            val dummyNode by nodeDoNothing<Unit>("dummy node")

            edge(nodeStart forwardTo dummyNode transformed { })
            edge(dummyNode forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(this, strategy) {
            install(TestFeature) { events = interceptedEvents }
        }
        agent.run("")

        val actualEvents = interceptedEvents.filter { it.startsWith("Node: ") }
        val expectedEvents = listOf(
            "Node: start node (name: '__start__', input: 'kotlin.Unit')",
            "Node: finish node (name: '__start__', input: 'kotlin.Unit', output: 'kotlin.Unit')",
            "Node: start node (name: 'dummy node', input: 'kotlin.Unit')",
            "Node: finish node (name: 'dummy node', input: 'kotlin.Unit', output: 'kotlin.Unit')",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test pipeline interceptors for llm call events`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()

        val strategy = simpleStrategy("test-interceptors-strategy") {
            val llmCallWithoutTools by nodeLLMRequest("test LLM call", allowToolCalls = false)
            val llmCall by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallWithoutTools transformed { "Test LLM call prompt" })
            edge(llmCallWithoutTools forwardTo llmCall transformed { "Test LLM call with tools prompt" })
            edge(llmCall forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(this, strategy) {
            install(TestFeature) { events = interceptedEvents }
        }
        agent.run("")

        val actualEvents = interceptedEvents.filter { it.startsWith("LLM + Tools: ") || it.startsWith("LLM: ") }
        val expectedEvents = listOf(
            "LLM + Tools: start LLM call with tools (prompt: 'Test user message', tools: [])",
            "LLM + Tools: finish LLM call with tools (responses: '[Assistant(content=Default test response)]', tools: [])",
            "LLM + Tools: start LLM call with tools (prompt: 'Test user message', tools: [dummy, __tools_list__])",
            "LLM + Tools: finish LLM call with tools (responses: '[Assistant(content=Default test response)]', tools: [dummy, __tools_list__])",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test pipeline interceptors for tool call events`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()

        val strategy = simpleStrategy("test-interceptors-strategy") {
            val nodeSendInput by nodeLLMSendStageInput()
            val toolCallNode by nodeExecuteTool("tool call node")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo toolCallNode onToolCall { true })
            edge(toolCallNode forwardTo nodeFinish transformed { it.content })
        }

        // Use custom tool registry with plus tool to be called
        val toolRegistry = ToolRegistry {
            stage("default") {
                tool(PlusTool)
            }
        }

        val agent = createAgent(
            this,
            strategy,
            toolRegistry = toolRegistry,
            userPrompt = "add 2 and 2",
            codePromptExecutor = CalculatorChatExecutor
        ) {
            install(TestFeature) { events = interceptedEvents }
        }

        agent.run("")

        val actualEvents = interceptedEvents.filter { it.startsWith("Tool: ") }
        val expectedEvents = listOf(
            "Tool: start tool calls [Call(id=1, tool=plus, content={\"a\":2.0,\"b\":2.0})]",
            "Tool: finish tool calls [Result(id=1, tool=plus, content=4.0)]"
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted tool events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test pipeline interceptors for agent create events`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()

        val strategy = simpleStrategy("test-interceptors-strategy") {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
        }

        createAgent(this, strategy) {
            install(TestFeature) { events = interceptedEvents }
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Agent: agent created") }
        val expectedEvents = listOf(
            "Agent: agent created (strategy name: 'test-interceptors-strategy')",
            "Agent: agent created (strategy name: 'test-interceptors-strategy'). read stages (size: 1)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test pipeline interceptors for strategy started events`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()

        val strategy = simpleStrategy("test-interceptors-strategy") {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(this, strategy) {
            install(TestFeature) { events = interceptedEvents }
        }
        agent.run("")

        val actualEvents = interceptedEvents.filter { it.startsWith("Agent: strategy started") }
        val expectedEvents = listOf(
            "Agent: strategy started (strategy name: 'test-interceptors-strategy')",
            "Agent: strategy started (strategy name: 'test-interceptors-strategy'). read stages (size: 1)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test pipeline interceptors for stage context events`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()
        val strategy = simpleStrategy("test-interceptors-strategy") {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(this, strategy) {
            install(TestFeature) { events = interceptedEvents }
        }
        agent.run("")

        val actualEvents = interceptedEvents.filter { it.startsWith("Stage Context: ") }
        val expectedEvents = listOf(
            "Stage Context: request features from stage context (stage name: default)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test several agents share one pipeline`() = runBlocking {

        val interceptedEvents = mutableListOf<String>()

        val agent1 = createAgent(
            coroutineScope = this,
            strategy = simpleStrategy("test-interceptors-strategy-1") {
                edge(nodeStart forwardTo nodeFinish transformed { "Done" })
            }) {
            install(TestFeature) { events = interceptedEvents }
        }

        val agent2 = createAgent(
            coroutineScope = this,
            strategy = simpleStrategy("test-interceptors-strategy-2") {
                edge(nodeStart forwardTo nodeFinish transformed { "Done" })
            }) {
            install(TestFeature) { events = interceptedEvents }
        }

        agent1.run("")
        agent2.run("")

        val actualEvents = interceptedEvents.filter { it.startsWith("Agent: agent created") }
        val expectedEvents = listOf(
            "Agent: agent created (strategy name: 'test-interceptors-strategy-1')",
            "Agent: agent created (strategy name: 'test-interceptors-strategy-1'). read stages (size: 1)",
            "Agent: agent created (strategy name: 'test-interceptors-strategy-2')",
            "Agent: agent created (strategy name: 'test-interceptors-strategy-2'). read stages (size: 1)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    //region Private Methods

    private fun createAgent(
        coroutineScope: CoroutineScope,
        strategy: LocalAgentStrategy,
        userPrompt: String? = null,
        systemPrompt: String? = null,
        assistantPrompt: String? = null,
        toolRegistry: ToolRegistry? = null,
        codePromptExecutor: CodePromptExecutor? = null,
        installFeatures: suspend FeatureContext.() -> Unit = {}
    ): KotlinAIAgent {

        val agentConfig = LocalAgentConfig(
            prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "test") {
                system(systemPrompt ?: "Test system message")
                user(userPrompt ?: "Test user message")
                assistant(assistantPrompt ?: "Test assistant response")
            },
            maxAgentIterations = 10
        )

        val testExecutor = getMockExecutor {
            mockLLMAnswer("Here's a summary of the conversation: Test user asked questions and received responses.") onRequestContains "Summarize all the main achievements"
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        return KotlinAIAgent(
            toolRegistry = toolRegistry ?: ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            strategy = strategy,
            eventHandler = EventHandler { },
            agentConfig = agentConfig,
            promptExecutor = codePromptExecutor ?: testExecutor,
            cs = coroutineScope,
            installFeatures = installFeatures,
        )
    }
    //endregion Private Methods
}
