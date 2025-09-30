package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyEventGraph
import ai.koog.agents.core.feature.model.events.StrategyEventGraphEdge
import ai.koog.agents.core.feature.model.events.StrategyEventGraphNode
import ai.koog.agents.core.feature.model.events.ToolExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolExecutionStartingEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.eventString
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.MockLLMProvider
import ai.koog.agents.features.tracing.mock.TestFeatureMessageWriter
import ai.koog.agents.features.tracing.mock.assistantMessage
import ai.koog.agents.features.tracing.mock.createAgent
import ai.koog.agents.features.tracing.mock.systemMessage
import ai.koog.agents.features.tracing.mock.testClock
import ai.koog.agents.features.tracing.mock.toolCallMessage
import ai.koog.agents.features.tracing.mock.toolResult
import ai.koog.agents.features.tracing.mock.userMessage
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TraceFeatureMessageRemoteWriterTest {

    companion object {
        private val logger = KotlinLogging.logger { }
        private val defaultClientServerTimeout = 30.seconds
        private const val HOST = "127.0.0.1"
    }

    @Test
    fun `test health check on agent run`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isServerStarted = CompletableDeferred<Boolean>()
        val isClientFinished = CompletableDeferred<Boolean>()

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                isServerStarted.await()
                client.connect()
                client.healthCheck()

                isClientFinished.complete(true)
            }
        }

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy<String, String>("tracing-test-strategy") {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                createAgent(strategy = strategy) {
                    install(Tracing) {
                        addMessageProcessor(writer)
                    }
                }.use { agent ->
                    agent.run("")
                    isServerStarted.complete(true)
                    isClientFinished.await()
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer collect events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val nodeSendLLMCallName = "test-llm-call"
        val nodeExecuteToolName = "test-tool-call"
        val nodeSendToolResultName = "test-node-llm-send-tool-result"

        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        val mockResponse = "Return test result"

        // Tools
        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Model
        val testModel = LLModel(
            provider = MockLLMProvider(),
            id = "test-llm-id",
            capabilities = emptyList(),
            contextLength = 1_000,
        )

        // Prompt
        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(content = userPrompt)
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy(strategyName) {
                    val nodeSendInput by nodeLLMRequest("test-llm-call")
                    val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                    val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                    edge(nodeStart forwardTo nodeSendInput)
                    edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                    edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                    edge(nodeExecuteTool forwardTo nodeSendToolResult)
                    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                }

                val mockExecutor = getMockExecutor(clock = testClock) {
                    mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals
                        userPrompt
                    mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
                }

                createAgent(
                    agentId = agentId,
                    strategy = strategy,
                    promptId = promptId,
                    model = testModel,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    assistantPrompt = assistantPrompt,
                    toolRegistry = toolRegistry,
                    promptExecutor = mockExecutor
                ) {
                    install(Tracing) {
                        addMessageProcessor(writer)
                    }
                }.use { agent ->
                    agent.run(userPrompt)
                    isServerStarted.complete(true)
                    isClientFinished.await()
                }
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                var runId = ""
                val expectedEventsCount = 20

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        if (event is AgentStartingEvent) {
                            runId = event.runId
                        }

                        actualEvents.add(event as DefinedFeatureEvent)
                        logger.info { "[${actualEvents.size}/$expectedEventsCount] Received event: $event" }

                        if (actualEvents.size >= expectedEventsCount) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                collectEventsJob.join()

                val llmCallGraphNode = StrategyEventGraphNode(id = nodeSendLLMCallName, name = nodeSendLLMCallName)
                val executeToolGraphNode = StrategyEventGraphNode(id = nodeExecuteToolName, name = nodeExecuteToolName)
                val sendToolResultGraphNode = StrategyEventGraphNode(id = nodeSendToolResultName, name = nodeSendToolResultName)

                val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
                val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    AgentStartingEvent(
                        agentId = agentId,
                        runId = runId,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    GraphStrategyStartingEvent(
                        runId = runId,
                        strategyName = strategyName,
                        graph = StrategyEventGraph(
                            nodes = listOf(
                                startGraphNode,
                                llmCallGraphNode,
                                executeToolGraphNode,
                                sendToolResultGraphNode,
                                finishGraphNode,
                            ),
                            edges = listOf(
                                StrategyEventGraphEdge(sourceNode = startGraphNode, targetNode = llmCallGraphNode),
                                StrategyEventGraphEdge(sourceNode = llmCallGraphNode, targetNode = executeToolGraphNode),
                                StrategyEventGraphEdge(sourceNode = llmCallGraphNode, targetNode = finishGraphNode),
                                StrategyEventGraphEdge(
                                    sourceNode = executeToolGraphNode,
                                    targetNode = sendToolResultGraphNode
                                ),
                                StrategyEventGraphEdge(
                                    sourceNode = sendToolResultGraphNode,
                                    targetNode = finishGraphNode
                                ),
                                StrategyEventGraphEdge(
                                    sourceNode = sendToolResultGraphNode,
                                    targetNode = executeToolGraphNode
                                )
                            )
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "__start__",
                        input = userPrompt,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "__start__",
                        input = userPrompt,
                        output = userPrompt,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "test-llm-call",
                        input = userPrompt,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "test-llm-call",
                        input = userPrompt,
                        output = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""").toString(),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "test-tool-call",
                        input = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""").toString(),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    ToolExecutionStartingEvent(
                        runId = runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    ToolExecutionCompletedEvent(
                        runId = runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                        result = dummyTool.result,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "test-tool-call",
                        input = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""").toString(),
                        output = toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toString(),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "test-node-llm-send-tool-result",
                        input = toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toString(),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage(mockResponse)),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "test-node-llm-send-tool-result",
                        input = toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toString(),
                        output = assistantMessage(mockResponse).toString(),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "__finish__",
                        input = mockResponse,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "__finish__",
                        input = mockResponse,
                        output = mockResponse,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    StrategyCompletedEvent(
                        runId = runId,
                        strategyName = strategyName,
                        result = mockResponse,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    AgentCompletedEvent(
                        agentId = agentId,
                        runId = runId,
                        result = mockResponse,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )

                // The 'runId' is updated when the agent is finished.
                // We cannot simplify that and move the expected events list before the job is finished
                // and relay on the number of elements in the list.
                assertEquals(
                    expectedEventsCount,
                    expectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, actualEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer is not set`() = runBlocking {
        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<FeatureMessage>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use {
                TestFeatureMessageWriter().use { testWriter ->

                    val strategy = strategy<String, String>(strategyName) {
                        val llmCallNode by nodeLLMRequest("test LLM call")
                        val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                        edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                        edge(
                            llmCallNode forwardTo llmCallWithToolsNode transformed {
                                "Test LLM call with tools prompt"
                            }
                        )
                        edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                    }

                    createAgent(strategy = strategy) {
                        install(Tracing) {
                            addMessageProcessor(testWriter)
                        }
                    }.use { agent ->
                        agent.run("")
                        isServerStarted.complete(true)
                        isClientFinished.await()
                    }
                }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { message: FeatureMessage ->
                        logger.debug { "Client received message: $message" }
                        actualEvents.add(message)
                    }
                }

                logger.debug { "Client waits for server to start" }
                isServerStarted.await()

                val throwable = assertFailsWith<SSEClientException> {
                    client.connect()
                }

                logger.debug { "Client sends finish event to a server" }
                isClientFinished.complete(true)

                collectEventsJob.cancelAndJoin()

                val actualErrorMessage = throwable.message
                assertNotNull(actualErrorMessage)
                assertTrue(actualErrorMessage.contains("Connection refused"))

                assertEquals(0, actualEvents.size)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer filter`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"

        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        val mockResponse = "Return test result"

        // Tools
        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Model
        val testModel = LLModel(
            provider = MockLLMProvider(),
            id = "test-llm-id",
            capabilities = emptyList(),
            contextLength = 1_000,
        )

        // Prompt
        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(content = userPrompt)
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy(strategyName) {
                    val nodeSendInput by nodeLLMRequest("test-llm-call")
                    val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                    val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                    edge(nodeStart forwardTo nodeSendInput)
                    edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                    edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                    edge(nodeExecuteTool forwardTo nodeSendToolResult)
                    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                }

                val mockExecutor = getMockExecutor(clock = testClock) {
                    mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals
                        userPrompt
                    mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
                }

                createAgent(
                    agentId = agentId,
                    strategy = strategy,
                    promptId = promptId,
                    model = testModel,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    assistantPrompt = assistantPrompt,
                    toolRegistry = toolRegistry,
                    promptExecutor = mockExecutor
                ) {
                    install(Tracing) {
                        writer.setMessageFilter { message ->
                            message is LLMCallStartingEvent || message is LLMCallCompletedEvent
                        }
                        addMessageProcessor(writer)
                    }
                }.use { agent ->
                    agent.run(userPrompt)
                    isServerStarted.complete(true)
                    isClientFinished.await()
                }
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                var runId = ""
                val expectedEventsCount = 4

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        if (event is LLMCallStartingEvent) {
                            runId = event.runId
                        }

                        actualEvents.add(event as DefinedFeatureEvent)
                        logger.info { "[${actualEvents.size}/$expectedEventsCount] Received event: $event" }

                        if (actualEvents.size >= expectedEventsCount) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    LLMCallStartingEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage(mockResponse)),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )

                // The 'runId' is updated when the agent is finished.
                // We cannot simplify that and move the expected events list before the job is finished
                // and relay on the number of elements in the list.
                assertEquals(
                    expectedEventsCount,
                    expectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, actualEvents)

                assertEquals(expectedEvents.size, actualEvents.size)
                assertContentEquals(expectedEvents, actualEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }
}
