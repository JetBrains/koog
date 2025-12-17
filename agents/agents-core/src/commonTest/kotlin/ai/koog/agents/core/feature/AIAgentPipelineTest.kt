package ai.koog.agents.core.feature

import ai.koog.agents.core.CalculatorChatExecutor
import ai.koog.agents.core.CalculatorTools
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.AgentClosing
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.AgentCompleted
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.AgentExecutionFailed
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.AgentStarting
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.LLMCallCompleted
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.LLMCallStarting
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.NodeExecutionCompleted
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.NodeExecutionFailed
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.NodeExecutionStarting
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.StrategyStarting
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.SubgraphExecutionCompleted
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.SubgraphExecutionFailed
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.SubgraphExecutionStarting
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.ToolCallCompleted
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.ToolCallFailed
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.ToolCallStarting
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType.ToolValidationFailed
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message.Role
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
import kotlin.time.Duration.Companion.seconds

class AIAgentPipelineTest {

    companion object {
        private const val DEFAULT_ASSISTANT_RESPONSE = "Default test response"
    }

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    @JsName("testPipelineInterceptorsForNodeEvents")
    fun `test pipeline interceptors for node events`() = runTest {
        val interceptedEvents = mutableListOf<String>()
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Hello World!"
        val agentResult = "Done"

        val strategyName = "test-interceptors-strategy"
        val dummyNodeName = "dummy node"

        val strategy = strategy<String, String>(strategyName) {
            val dummyNode by nodeDoNothing<Unit>(dummyNodeName)

            edge(nodeStart forwardTo dummyNode transformed { })
            edge(dummyNode forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(id = agentId, strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run("Hello World!")
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(NodeExecutionStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(NodeExecutionFailed::class.simpleName.toString()) ||
                collectedEvent.startsWith(NodeExecutionCompleted::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, START_NODE_PREFIX)}, name: $START_NODE_PREFIX, input: $agentInput)",
            "${NodeExecutionCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, START_NODE_PREFIX)}, name: $START_NODE_PREFIX, input: $agentInput, output: $agentInput)",
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, dummyNodeName)}, name: $dummyNodeName, input: kotlin.Unit)",
            "${NodeExecutionCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, dummyNodeName)}, name: $dummyNodeName, input: kotlin.Unit, output: kotlin.Unit)",
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, FINISH_NODE_PREFIX)}, name: $FINISH_NODE_PREFIX, input: $agentResult)",
            "${NodeExecutionCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, FINISH_NODE_PREFIX)}, name: $FINISH_NODE_PREFIX, input: $agentResult, output: $agentResult)",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Hello World!"
        val agentResult = "Done"

        val strategyName = "test-interceptors-strategy"
        val nodeName = "Node with error"
        val testErrorMessage = "Test error"

        val strategy = strategy<String, String>(strategyName) {
            val nodeWithError by node<String, String>(nodeName) {
                throw IllegalStateException(testErrorMessage)
            }

            edge(nodeStart forwardTo nodeWithError)
            edge(nodeWithError forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            val throwable = assertFails { agent.run(agentInput) }
            assertEquals(testErrorMessage, throwable.message)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(NodeExecutionStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(NodeExecutionFailed::class.simpleName.toString()) ||
                collectedEvent.startsWith(NodeExecutionCompleted::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, START_NODE_PREFIX)}, name: $START_NODE_PREFIX, input: $agentInput)",
            "${NodeExecutionCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, START_NODE_PREFIX)}, name: $START_NODE_PREFIX, input: $agentInput, output: $agentInput)",
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeName)}, name: $nodeName, input: $agentInput)",
            "${NodeExecutionFailed::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeName)}, name: $nodeName, error: $testErrorMessage)",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test input"

        val strategyName = "test-node-with-error-cancellation"
        val nodeWithErrorName = "test-node-with-error"
        val testErrorMessage = "Test cancellation error"

        val strategy = strategy<String, String>(strategyName) {
            val nodeWithError by node<String, String>(nodeWithErrorName) {
                throw CancellationException(testErrorMessage)
            }
            nodeStart then nodeWithError then nodeFinish
        }

        val throwable = assertFailsWith<CancellationException> {
            createAgent(id = agentId, strategy = strategy) {
                install(TestFeature) {
                    events = interceptedEvents
                    runIds = interceptedRunIds
                }
            }.use { agent ->
                agent.run(agentInput)
            }
        }

        assertEquals(testErrorMessage, throwable.message)

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(NodeExecutionStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(NodeExecutionFailed::class.simpleName.toString()) ||
                collectedEvent.startsWith(NodeExecutionCompleted::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, START_NODE_PREFIX)}, name: $START_NODE_PREFIX, input: $agentInput)",
            "${NodeExecutionCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, START_NODE_PREFIX)}, name: $START_NODE_PREFIX, input: $agentInput, output: $agentInput)",
            "${NodeExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeWithErrorName)}, name: $nodeWithErrorName, input: $agentInput)",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test agent input"
        val subgraphOutput = "Test subgraph output"

        val strategyName = "test-interceptors-strategy"
        val subgraphName = "test-subgraph"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                edge(nodeStart forwardTo nodeFinish transformed { subgraphOutput })
            }
            nodeStart then subgraph then nodeFinish
        }

        createAgent(id = agentId, strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(SubgraphExecutionStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(SubgraphExecutionCompleted::class.simpleName.toString()) ||
                collectedEvent.startsWith(SubgraphExecutionFailed::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${SubgraphExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, subgraphName)}, name: $subgraphName, input: $agentInput)",
            "${SubgraphExecutionCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, subgraphName)}, name: $subgraphName, input: $agentInput, output: $subgraphOutput)",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test agent input"

        val strategyName = "test-interceptors-strategy"
        val subgraphName = "subgraph-with-error"
        val nodeWithErrorName = "subgraph-node-with-error"
        val testErrorMessage = "Test error"

        val strategy = strategy<String, String>(strategyName) {
            val subgraphWithError by subgraph<String, String>(subgraphName) {
                val nodeWithError by node<String, String>(nodeWithErrorName) {
                    throw IllegalStateException(testErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraphWithError then nodeFinish
        }

        val throwable =
            createAgent(id = agentId, strategy = strategy) {
                install(TestFeature) {
                    events = interceptedEvents
                    runIds = interceptedRunIds
                }
            }.use { agent ->
                assertFailsWith<IllegalStateException> {
                    agent.run(agentInput)
                }
            }

        assertEquals(testErrorMessage, throwable.message)

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(SubgraphExecutionStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(SubgraphExecutionCompleted::class.simpleName.toString()) ||
                collectedEvent.startsWith(SubgraphExecutionFailed::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${SubgraphExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, subgraphName)}, name: $subgraphName, input: $agentInput)",
            "${SubgraphExecutionFailed::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, subgraphName)}, name: $subgraphName, error: $testErrorMessage)",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test input"

        val strategyName = "test-subgraph-with-error-cancellation"
        val subgraphWithErrorName = "test-subgraph-with-error"
        val nodeWithErrorName = "subgraph-node-with-error"
        val testErrorMessage = "Test cancellation error"

        val strategy = strategy<String, String>(strategyName) {
            val subgraphWithError by subgraph<String, String>(subgraphWithErrorName) {
                val nodeWithError by node<String, String>(nodeWithErrorName) {
                    throw CancellationException(testErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraphWithError then nodeFinish
        }

        val throwable =
            createAgent(id = agentId, strategy = strategy) {
                install(TestFeature) {
                    events = interceptedEvents
                    runIds = interceptedRunIds
                }
            }.use { agent ->
                assertFailsWith<CancellationException> {
                    agent.run(agentInput)
                }
            }

        assertEquals(testErrorMessage, throwable.message)

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(SubgraphExecutionStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(SubgraphExecutionCompleted::class.simpleName.toString()) ||
                collectedEvent.startsWith(SubgraphExecutionFailed::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${SubgraphExecutionStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, subgraphWithErrorName)}, name: $subgraphWithErrorName, input: $agentInput)",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test user message"
        val agentOutput = "Done"

        val strategyName = "test-interceptors-strategy"
        val nodeLLMCallWithoutToolsName = "test-llm-node-without-tools"
        val nodeLLMCall = "test-llm-node"

        val testLLMResponse = "Test LLM call prompt"
        val llmCallWithToolsResponse = "Test LLM call with tools prompt"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallWithoutTools by nodeLLMRequest(nodeLLMCallWithoutToolsName, allowToolCalls = false)
            val llmCall by nodeLLMRequest(nodeLLMCall)

            edge(nodeStart forwardTo llmCallWithoutTools transformed { testLLMResponse })
            edge(llmCallWithoutTools forwardTo llmCall transformed { llmCallWithToolsResponse })
            edge(llmCall forwardTo nodeFinish transformed { agentOutput })
        }

        createAgent(id = agentId, strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(LLMCallStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(LLMCallCompleted::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${LLMCallStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeLLMCallWithoutToolsName)}, prompt: $testLLMResponse, tools: [])",
            "${LLMCallCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeLLMCallWithoutToolsName)}, responses: [${Role.Assistant.name}: $DEFAULT_ASSISTANT_RESPONSE])",
            "${LLMCallStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeLLMCall)}, prompt: $llmCallWithToolsResponse, tools: [${DummyTool().name}])",
            "${LLMCallCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeLLMCall)}, responses: [${Role.Assistant.name}: $DEFAULT_ASSISTANT_RESPONSE])",
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "add 2.2 and 2.2"

        val strategyName = "test-interceptors-strategy"
        val nodeToolCallName = "test-tool-node"

        val strategy = strategy(strategyName) {
            val nodeSendInput by nodeLLMRequest()
            val toolCallNode by nodeExecuteTool(nodeToolCallName)

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo toolCallNode onToolCall { true })
            edge(toolCallNode forwardTo nodeFinish transformed { it.content })
        }

        // Use custom tool registry with plus tool to be called
        val toolRegistry = ToolRegistry {
            tool(CalculatorTools.PlusTool)
        }

        createAgent(
            id = agentId,
            strategy = strategy,
            toolRegistry = toolRegistry,
            promptExecutor = CalculatorChatExecutor
        ) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(ToolCallStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(ToolCallCompleted::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${ToolCallStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeToolCallName)}, tool: ${CalculatorTools.PlusTool.name}, args: ${
                CalculatorTools.PlusTool.encodeArgs(CalculatorTools.CalculatorTool.Args(2.2F, 2.2F))
            })",
            "${ToolCallCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeToolCallName)}, tool: ${CalculatorTools.PlusTool.name}, result: ${
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

        val agentId = "test-agent-id"
        val agentInput = "Test agent input"
        val agentOutput = "Test agent output"

        val strategyName = "test-interceptors-strategy"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentOutput })
        }

        createAgent(id = agentId, strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(AgentStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(AgentCompleted::class.simpleName.toString()) ||
                collectedEvent.startsWith(AgentExecutionFailed::class.simpleName.toString()) ||
                collectedEvent.startsWith(AgentClosing::class.simpleName.toString())
        }

        val runId = interceptedRunIds.first()

        val expectedEvents = listOf(
            "${AgentStarting::class.simpleName} (path: ${agentExecutionPath(agentId)}, id: $agentId, run id: $runId)",
            "${AgentCompleted::class.simpleName} (path: ${agentExecutionPath(agentId)}, id: $agentId, run id: $runId, result: $agentOutput)",
            "${AgentClosing::class.simpleName} (path: ${agentExecutionPath(agentId)}, id: $agentId)"
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
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test agent input"
        val agentOutput = "Done"

        val strategyName = "test-interceptors-strategy"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentOutput })
        }

        createAgent(id = agentId, strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(StrategyStarting::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${StrategyStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName)}, strategy: $strategyName)",
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

        val runIds = interceptedRunIds.distinct()

        assertEquals(2, runIds.size)

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(AgentStarting::class.simpleName.toString())
        }

        val runId1 = runIds[0]
        val runId2 = runIds[1]

        val expectedEvents = listOf(
            "${AgentStarting::class.simpleName} (path: ${agentExecutionPath(agent1Id)}, id: $agent1Id, run id: $runId1)",
            "${AgentStarting::class.simpleName} (path: ${agentExecutionPath(agent2Id)}, id: $agent2Id, run id: $runId2)",
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
    fun `test filter llm call finish events`() = runTest(timeout = 10000.seconds) {
        val interceptedEvents = mutableListOf<String>()
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "Test user message"
        val agentOutput = "Done"

        val strategyName = "test-interceptors-strategy"
        val nodeLLMCallWithoutToolsName = "test-llm-node-without-tools"
        val nodeLLMCallName = "test-llm-node"

        val testLLMResponse = "Test LLM call prompt"
        val llmCallWithToolsResponse = "Test LLM call with tools prompt"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallWithoutTools by nodeLLMRequest(nodeLLMCallWithoutToolsName, allowToolCalls = false)
            val llmCall by nodeLLMRequest(nodeLLMCallName)

            edge(nodeStart forwardTo llmCallWithoutTools transformed { testLLMResponse })
            edge(llmCallWithoutTools forwardTo llmCall transformed { llmCallWithToolsResponse })
            edge(llmCall forwardTo nodeFinish transformed { agentOutput })
        }

        createAgent(strategy = strategy) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(LLMCallStarting::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${LLMCallStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeLLMCallWithoutToolsName)}, prompt: $testLLMResponse, tools: [])",
            "${LLMCallStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeLLMCallName)}, prompt: $llmCallWithToolsResponse, tools: [${DummyTool().name}])",
        )

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Miss intercepted events. Expected ${expectedEvents.size}, but received: ${actualEvents.size}"
        )

        assertContentEquals(expectedEvents, actualEvents)
    }

    //region Execution Info

    @Test
    fun `test AgentExecutionInfo for tool calls`() = runTest {
        val interceptedEvents = mutableListOf<String>()
        val interceptedRunIds = mutableListOf<String>()

        val agentId = "test-agent-id"
        val agentInput = "add 2.2 and 2.2"

        val strategyName = "test-strategy"
        val nodeToolCallName = "tool-call-node"

        val strategy = strategy(strategyName) {
            val nodeSendInput by nodeLLMRequest()
            val toolCallNode by nodeExecuteTool(nodeToolCallName)

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo toolCallNode onToolCall { true })
            edge(toolCallNode forwardTo nodeFinish transformed { it.content })
        }

        val toolRegistry = ToolRegistry {
            tool(CalculatorTools.PlusTool)
        }

        createAgent(
            strategy = strategy,
            toolRegistry = toolRegistry,
            promptExecutor = CalculatorChatExecutor
        ) {
            install(TestFeature) {
                events = interceptedEvents
                runIds = interceptedRunIds
            }
        }.use { agent ->
            agent.run(agentInput)
        }

        val actualEvents = interceptedEvents.filter { collectedEvent ->
            collectedEvent.startsWith(ToolCallStarting::class.simpleName.toString()) ||
                collectedEvent.startsWith(ToolCallCompleted::class.simpleName.toString()) ||
                collectedEvent.startsWith(ToolValidationFailed::class.simpleName.toString()) ||
                collectedEvent.startsWith(ToolCallFailed::class.simpleName.toString())
        }

        val expectedEvents = listOf(
            "${ToolCallStarting::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeToolCallName)}, tool: ${CalculatorTools.PlusTool.name}, args: ${
                CalculatorTools.PlusTool.encodeArgs(CalculatorTools.CalculatorTool.Args(2.2F, 2.2F))
            })",
            "${ToolCallCompleted::class.simpleName} (path: ${agentExecutionPath(agentId, strategyName, nodeToolCallName)}, tool: ${CalculatorTools.PlusTool.name}, result: ${
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

    //endregion Execution Info

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
            mockLLMAnswer(DEFAULT_ASSISTANT_RESPONSE).asDefaultResponse
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

    private fun agentExecutionPath(vararg parts: String) = parts.joinToString(AgentExecutionInfo.defaultPathSeparator)

    //endregion Private Methods
}
