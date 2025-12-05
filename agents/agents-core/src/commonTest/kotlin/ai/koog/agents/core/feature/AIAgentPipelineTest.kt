package ai.koog.agents.core.feature

import ai.koog.agents.core.CalculatorChatExecutor
import ai.koog.agents.core.CalculatorTools
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class AIAgentPipelineTest {

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    @JsName("testPipelineInterceptorsForNodeEvents")
    fun `test pipeline interceptors for node events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val agentInput = "Hello World!"
        val agentResult = "Done"

        val dummyNodeName = "dummy node"
        val strategy = strategy<String, String>("test-interceptors-strategy") {
            val dummyNode by nodeDoNothing<Unit>(dummyNodeName)

            edge(nodeStart forwardTo dummyNode transformed { })
            edge(dummyNode forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(strategy = strategy) {
            install(TestFeature) { events = interceptedEvents }
        }.use { agent ->
            agent.run("Hello World!")
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Node: ") }
        val expectedEvents = listOf(
            "Node: start node (name: __start__, input: $agentInput)",
            "Node: finish node (name: __start__, input: $agentInput, output: $agentInput)",
            "Node: start node (name: $dummyNodeName, input: kotlin.Unit)",
            "Node: finish node (name: $dummyNodeName, input: kotlin.Unit, output: kotlin.Unit)",
            "Node: start node (name: __finish__, input: $agentResult)",
            "Node: finish node (name: __finish__, input: $agentResult, output: $agentResult)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForNodeExecutionErrorEvents")
    fun `test pipeline interceptors for node execution errors events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val nodeName = "Node with error"
        val testErrorMessage = "Test error"

        val strategy = strategy<String, String>("test-interceptors-strategy") {
            val nodeWithError by node<String, String>(nodeName) {
                throw IllegalStateException(testErrorMessage)
            }

            edge(nodeStart forwardTo nodeWithError)
            edge(nodeWithError forwardTo nodeFinish transformed { "Done" })
        }

        val agentInput = "Hello World!"

        createAgent(strategy = strategy) {
            install(TestFeature) { events = interceptedEvents }
        }.use { agent ->
            val throwable = assertFails { agent.run(agentInput) }
            assertEquals(testErrorMessage, throwable.message)
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Node: ") }
        val expectedEvents = listOf(
            "Node: start node (name: __start__, input: $agentInput)",
            "Node: finish node (name: __start__, input: $agentInput, output: $agentInput)",
            "Node: start node (name: $nodeName, input: $agentInput)",
            "Node: execution error (name: $nodeName, error: $testErrorMessage)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsDoNotCaptureNodeFailedEventOnCancellation")
    fun `test pipeline interceptors do not capture node failed event on cancellation`() = runTest {
        val interceptedEvents = mutableListOf<String>()
        val agentInput = "Test input"
        val nodeWithErrorName = "test-node-with-error"
        val testErrorMessage = "Test cancellation error"

        val strategy = strategy<String, String>("test-node-with-error-cancellation") {
            val nodeWithError by node<String, String>(nodeWithErrorName) {
                throw CancellationException(testErrorMessage)
            }
            nodeStart then nodeWithError then nodeFinish
        }

        val throwable = assertFailsWith<CancellationException> {
            createAgent(strategy = strategy) {
                install(TestFeature) {
                    events = interceptedEvents
                }
            }.use { agent ->
                agent.run(agentInput)
            }
        }

        assertEquals(testErrorMessage, throwable.message)

        val actualEvents = interceptedEvents.filter { it.startsWith("Node: ") }

        val expectedEvents = listOf(
            "Node: start node (name: __start__, input: $agentInput)",
            "Node: finish node (name: __start__, input: $agentInput, output: $agentInput)",
            "Node: start node (name: $nodeWithErrorName, input: $agentInput)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Mismatch between expected and actual intercepted events. " +
                "Expected:\n${expectedEvents.joinToString("\n") { " - $it" }}\n" +
                ", but received:\n${actualEvents.joinToString("\n") { " - $it" }}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForSubgraphEvents")
    fun `test pipeline interceptors for subgraph events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val agentInput = "Test agent input"
        val subgraphOutput = "Test subgraph output"

        val subgraphName = "test-subgraph"
        val strategy = strategy<String, String>("test-interceptors-strategy") {
            val subgraph by subgraph<String, String>(subgraphName) {
                edge(nodeStart forwardTo nodeFinish transformed { subgraphOutput })
            }
            nodeStart then subgraph then nodeFinish
        }

        createAgent(strategy = strategy) {
            install(TestFeature) { events = interceptedEvents }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Subgraph: ") }
        val expectedEvents = listOf(
            "Subgraph: start subgraph (name: $subgraphName, input: $agentInput)",
            "Subgraph: finish subgraph (name: $subgraphName, input: $agentInput, output: $subgraphOutput)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Mismatch between expected and actual intercepted events. " +
                "Expected:\n${expectedEvents.joinToString("\n") { " - $it" }}\n" +
                ", but received:\n${actualEvents.joinToString("\n") { " - $it" }}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForSubgraphExecutionErrorEvents")
    fun `test pipeline interceptors for subgraph execution errors events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val agentInput = "Test agent input"
        val subgraphName = "subgraph-with-error"
        val nodeWithErrorName = "subgraph-node-with-error"
        val testErrorMessage = "Test error"

        val strategy = strategy<String, String>("test-interceptors-strategy") {
            val subgraphWithError by subgraph<String, String>(subgraphName) {
                val nodeWithError by node<String, String>(nodeWithErrorName) {
                    throw IllegalStateException(testErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraphWithError then nodeFinish
        }

        val throwable =
            createAgent(strategy = strategy) {
                install(TestFeature) { events = interceptedEvents }
            }.use { agent ->
                assertFailsWith<IllegalStateException> {
                    agent.run(agentInput)
                }
            }

        assertEquals(testErrorMessage, throwable.message)

        val actualEvents = interceptedEvents.filter { it.startsWith("Subgraph: ") }
        val expectedEvents = listOf(
            "Subgraph: start subgraph (name: $subgraphName, input: $agentInput)",
            "Subgraph: execution error (name: $subgraphName, error: $testErrorMessage)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsDoNotCaptureSubgraphFailedEventOnCancellation")
    fun `test pipeline interceptors do not capture subgraph failed event on cancellation`() = runTest {
        val interceptedEvents = mutableListOf<String>()
        val agentInput = "Test input"
        val subgraphWithErrorName = "test-subgraph-with-error"
        val nodeWithErrorName = "subgraph-node-with-error"
        val testErrorMessage = "Test cancellation error"

        val strategy = strategy<String, String>("test-subgraph-with-error-cancellation") {
            val subgraphWithError by subgraph<String, String>(subgraphWithErrorName) {
                val nodeWithError by node<String, String>(nodeWithErrorName) {
                    throw CancellationException(testErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraphWithError then nodeFinish
        }

        val throwable =
            createAgent(strategy = strategy) {
                install(TestFeature) {
                    events = interceptedEvents
                }
            }.use { agent ->
                assertFailsWith<CancellationException> {
                    agent.run(agentInput)
                }
            }

        assertEquals(testErrorMessage, throwable.message)

        val actualEvents = interceptedEvents.filter { it.startsWith("Subgraph: ") }

        val expectedEvents = listOf(
            "Subgraph: start subgraph (name: $subgraphWithErrorName, input: $agentInput)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Mismatch between expected and actual intercepted events. " +
                "Expected:\n${expectedEvents.joinToString("\n") { " - $it" }}\n" +
                ", but received:\n${actualEvents.joinToString("\n") { " - $it" }}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForLLmCallEvents")
    fun `test pipeline interceptors for llm call events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val strategy = strategy<String, String>("test-interceptors-strategy") {
            val llmCallWithoutTools by nodeLLMRequest("test LLM call", allowToolCalls = false)
            val llmCall by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallWithoutTools transformed { "Test LLM call prompt" })
            edge(llmCallWithoutTools forwardTo llmCall transformed { "Test LLM call with tools prompt" })
            edge(llmCall forwardTo nodeFinish transformed { "Done" })
        }

        createAgent(strategy = strategy) {
            install(TestFeature) { events = interceptedEvents }
        }.use { agent ->
            agent.run("")
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("LLM: ") }
        val expectedEvents = listOf(
            "LLM: start LLM call (prompt: Test user message, tools: [])",
            "LLM: finish LLM call (responses: [Assistant: Default test response])",
            "LLM: start LLM call (prompt: Test user message, tools: [dummy])",
            "LLM: finish LLM call (responses: [Assistant: Default test response])",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForToolCallEvents")
    fun `test pipeline interceptors for tool call events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val strategy = strategy("test-interceptors-strategy") {
            val nodeSendInput by nodeLLMRequest()
            val toolCallNode by nodeExecuteTool("tool call node")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo toolCallNode onToolCall { true })
            edge(toolCallNode forwardTo nodeFinish transformed { it.content })
        }

        // Use custom tool registry with plus tool to be called
        val toolRegistry = ToolRegistry {
            tool(CalculatorTools.PlusTool)
        }

        createAgent(
            strategy = strategy,
            toolRegistry = toolRegistry,
            userPrompt = "add 2.2 and 2.2",
            promptExecutor = CalculatorChatExecutor
        ) {
            install(TestFeature) { events = interceptedEvents }
        }.use { agent ->
            agent.run("")
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Tool: ") }
        val expectedEvents = listOf(
            "Tool: call tool (tool: ${CalculatorTools.PlusTool.name}, args: ${
                CalculatorTools.PlusTool.encodeArgs(CalculatorTools.CalculatorTool.Args(2.2F, 2.2F))
            })",
            "Tool: finish tool call with result (tool: ${CalculatorTools.PlusTool.name}, result: ${
                CalculatorTools.PlusTool.encodeResult(CalculatorTools.CalculatorTool.Result(4.4F))
            })"
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted tool events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForAgentCreateEvents")
    fun `test pipeline interceptors before agent started events`() = runTest {
        val interceptedEvents = mutableListOf<String>()
        val interceptedRunIds = mutableListOf<String>()

        val strategy = strategy<String, String>("test-interceptors-strategy") {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
        }

        val agentId = "test-agent-id"
        createAgent(strategy = strategy, id = agentId) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run("")
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Agent: before agent started") }
        val expectedEvents = listOf(
            "Agent: before agent started (id: $agentId, run id: ${interceptedRunIds.last()})",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testPipelineInterceptorsForStrategyEvents")
    fun `test pipeline interceptors for strategy started events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val strategy = strategy<String, String>("test-interceptors-strategy") {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
        }

        createAgent(strategy = strategy) {
            install(TestFeature) { events = interceptedEvents }
        }.use { agent ->
            agent.run("")
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("Agent: strategy started") }
        val expectedEvents = listOf(
            "Agent: strategy started (strategy name: test-interceptors-strategy)",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testSeveralAgentsShareOnePipeline")
    fun `test several agents share one pipeline`() = runTest {
        val interceptedEvents = mutableListOf<String>()
        val interceptedRunIds = mutableListOf<String>()

        val agent1Id = "agent1-id"
        val agent2Id = "agent2-id"

        createAgent(
            id = agent1Id,
            strategy = strategy("test-interceptors-strategy-1") {
                edge(nodeStart forwardTo nodeFinish transformed { "Done" })
            }
        ) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent1 ->

            createAgent(
                id = agent2Id,
                strategy = strategy("test-interceptors-strategy-2") {
                    edge(nodeStart forwardTo nodeFinish transformed { "Done" })
                }
            ) {
                install(TestFeature) {
                    events = interceptedEvents
                    runIds = interceptedRunIds
                }
            }.use { agent2 ->

                agent1.run("")
                agent2.run("")
            }
        }

        assertEquals(2, interceptedRunIds.size)

        val actualEvents = interceptedEvents.filter { it.startsWith("Agent: before agent started") }
        val expectedEvents = listOf(
            "Agent: before agent started (id: $agent1Id, run id: ${interceptedRunIds[0]})",
            "Agent: before agent started (id: $agent2Id, run id: ${interceptedRunIds[1]})",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    @JsName("testFilterLLMCallStartEvents")
    fun `test filter llm call finish events`() = runTest {
        val interceptedEvents = mutableListOf<String>()

        val strategy = strategy<String, String>("test-interceptors-strategy") {
            val llmCallWithoutTools by nodeLLMRequest("test LLM call", allowToolCalls = false)
            val llmCall by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallWithoutTools transformed { "Test LLM call prompt" })
            edge(llmCallWithoutTools forwardTo llmCall transformed { "Test LLM call with tools prompt" })
            edge(llmCall forwardTo nodeFinish transformed { "Done" })
        }

        createAgent(strategy = strategy) {
            install(TestFeature) {
                setEventFilter { eventContext ->
                    eventContext.eventType !is AgentLifecycleEventType.LLMCallCompleted
                }
                events = interceptedEvents
            }
        }.use { agent ->
            agent.run("")
        }

        val actualEvents = interceptedEvents.filter { it.startsWith("LLM: ") }
        val expectedEvents = listOf(
            "LLM: start LLM call (prompt: Test user message, tools: [])",
            "LLM: start LLM call (prompt: Test user message, tools: [dummy])",
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
        strategy: AIAgentGraphStrategy<String, String>,
        id: String? = null,
        userPrompt: String? = null,
        systemPrompt: String? = null,
        assistantPrompt: String? = null,
        toolRegistry: ToolRegistry? = null,
        promptExecutor: PromptExecutor? = null,
        installFeatures: FeatureContext.() -> Unit = {}
    ): AIAgent<String, String> {
        val agentConfig = AIAgentConfig(
            prompt = prompt("test", clock = testClock) {
                system(systemPrompt ?: "Test system message")
                user(userPrompt ?: "Test user message")
                assistant(assistantPrompt ?: "Test assistant response")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val testExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(
                "Here's a summary of the conversation: Test user asked questions and received responses."
            ) onRequestContains
                "Summarize all the main achievements"
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        return AIAgent(
            id = id ?: "test-agent-id",
            promptExecutor = promptExecutor ?: testExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry ?: ToolRegistry {
                tool(DummyTool())
            },
            installFeatures = installFeatures,
        )
    }

    //endregion Private Methods
}
