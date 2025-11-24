package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
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
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.MockLLMProvider
import ai.koog.agents.features.tracing.mock.TestFeatureMessageWriter
import ai.koog.agents.features.tracing.mock.assistantMessage
import ai.koog.agents.features.tracing.mock.createAgent
import ai.koog.agents.features.tracing.mock.receivedToolResult
import ai.koog.agents.features.tracing.mock.systemMessage
import ai.koog.agents.features.tracing.mock.testClock
import ai.koog.agents.features.tracing.mock.toolCallMessage
import ai.koog.agents.features.tracing.mock.userMessage
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.utils.io.use
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.http.URLProtocol
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class TraceFeatureMessageRemoteWriterTest {

    companion object {
        private val logger = KotlinLogging.logger { }
        private val defaultClientServerTimeout = 30.seconds
        private const val HOST = "127.0.0.1"
    }

    private val testBaseClient: HttpClient
        get() = HttpClient {
            install(HttpRequestRetry) {
                retryOnExceptionIf(maxRetries = 10) { _, cause ->
                    cause is IOException
                }
            }
        }

    @Test
    fun `test health check on agent run`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port, awaitInitialConnection = true, awaitInitialConnectionTimeout = defaultClientServerTimeout)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = MutableStateFlow(false)

        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                scope = this,
                baseClient = testBaseClient
            ).use { client ->
                client.connect()
                client.healthCheck()
                isClientFinished.value = true
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
                        // Install the test writer that will suspend until a client finishes the health check
                        addMessageProcessor(
                            TestFeatureMessageWriter {
                                withTimeoutOrNull(defaultClientServerTimeout) {
                                    isClientFinished.first { it }
                                }
                            }
                        )
                    }
                }.use { agent ->
                    agent.run("")
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
                receivedToolResult("0", dummyTool.name, dummyTool.result, dummyTool.encodeResult(dummyTool.result)).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port, awaitInitialConnection = true, awaitInitialConnectionTimeout = defaultClientServerTimeout)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

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
                    mockLLMToolCall(
                        tool = dummyTool,
                        args = DummyTool.Args("test"),
                        toolCallId = "0"
                    ) onRequestEquals userPrompt
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
                }
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                scope = this,
                baseClient = testBaseClient
            ).use { client ->

                var runId = ""
                val expectedEventsCount = 21

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

                client.connect()
                collectEventsJob.join()

                val llmCallGraphNode = StrategyEventGraphNode(id = nodeSendLLMCallName, name = nodeSendLLMCallName)
                val executeToolGraphNode = StrategyEventGraphNode(id = nodeExecuteToolName, name = nodeExecuteToolName)
                val sendToolResultGraphNode = StrategyEventGraphNode(id = nodeSendToolResultName, name = nodeSendToolResultName)

                val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
                val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

                val callIds = actualEvents.filterIsInstance<LLMCallStartingEvent>().map { it.callId }
                assertEquals(
                    2,
                    callIds.size,
                    "Expected 2 LLMCallStartingEvent, got ${callIds.size}"
                )

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
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "__start__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        output = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "test-llm-call",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "test-llm-call",
                        // TODO: KG-485. Update to include serialized [ReceivedToolResult] when it became a serializable type.
                        input = JsonPrimitive(userPrompt),
                        output = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            dataType = typeOf<Message>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "test-tool-call",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            dataType = typeOf<Message.Tool.Call>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    ToolCallStartingEvent(
                        runId = runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    ToolCallCompletedEvent(
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
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            dataType = typeOf<Message.Tool.Call>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds(),
                        // Tool result is wrapped into an object with id, tool, content, and result fields
                        output = kotlinx.serialization.json.JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("0"),
                                "tool" to JsonPrimitive(dummyTool.name),
                                "content" to JsonPrimitive(dummyTool.result),
                                "result" to dummyTool.encodeResult(dummyTool.result)
                            )
                        )
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "test-node-llm-send-tool-result",
                        input = kotlinx.serialization.json.JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("0"),
                                "tool" to JsonPrimitive(dummyTool.name),
                                "content" to JsonPrimitive(dummyTool.result),
                                "result" to dummyTool.encodeResult(dummyTool.result)
                            )
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = runId,
                        callId = callIds[1],
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        callId = callIds[1],
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.toModelInfo(),
                        responses = listOf(assistantMessage(mockResponse)),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "test-node-llm-send-tool-result",
                        input = kotlinx.serialization.json.JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("0"),
                                "tool" to JsonPrimitive(dummyTool.name),
                                "content" to JsonPrimitive(dummyTool.result),
                                "result" to dummyTool.encodeResult(dummyTool.result)
                            )
                        ),
                        output = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = assistantMessage(mockResponse),
                            dataType = typeOf<Message>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = runId,
                        nodeName = "__finish__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = mockResponse,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = runId,
                        nodeName = "__finish__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = mockResponse,
                            dataType = typeOf<String>()
                        ),
                        output = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = mockResponse,
                            dataType = typeOf<String>()
                        ),
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
                    AgentClosingEvent(
                        agentId = agentId,
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
        val isServerStarted = MutableStateFlow(false)
        val isClientFinished = MutableStateFlow(false)

        val serverJob = launch {
            // Create the test feature message writer that will suspend until a client finishes execution
            val testFeatureWriter = TestFeatureMessageWriter(onClose = {
                withTimeoutOrNull(defaultClientServerTimeout) {
                    isClientFinished.first { it }
                }
            })

            // Launch a job to signal about the agent start event
            launch {
                withTimeoutOrNull(defaultClientServerTimeout) {
                    testFeatureWriter.isOpen.first { it }
                    isServerStarted.value = true
                }
            }

            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { remoteWriter ->
                testFeatureWriter.use { testWriter ->

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
                    }
                }
            }
        }

        val clientJob = launch {
            // Do not use a testBaseClient that perform reconnection because the server job (an agent)
            // is not expected to launch a server in that test. Instead, the client should wait for an agent
            // to start execution and perform a single connection attempt that should fail.
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                scope = this,
            ).use { client ->

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { message: FeatureMessage ->
                        logger.debug { "Client received message: $message" }
                        actualEvents.add(message)
                    }
                }

                logger.debug { "Client waits for server to start" }
                val isServerJobStarted = withTimeoutOrNull(defaultClientServerTimeout) {
                    isServerStarted.first { it }
                } != null

                assertTrue(isServerJobStarted, "Server job did not start in time")

                val throwable = assertFailsWith<SSEClientException> {
                    client.connect()
                }

                logger.debug { "Client sends finish event to a server" }
                isClientFinished.value = true

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
                receivedToolResult("0", dummyTool.name, dummyTool.result, dummyTool.encodeResult(dummyTool.result)).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port, awaitInitialConnectionTimeout = defaultClientServerTimeout)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

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
                }
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                scope = this,
                baseClient = testBaseClient
            ).use { client ->

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

                client.connect()
                collectEventsJob.join()

                val callIds = actualEvents.filterIsInstance<LLMCallStartingEvent>().map { it.callId }
                assertEquals(
                    2,
                    callIds.size,
                    "Expected 2 LLMCallStartingEvent, got ${callIds.size}"
                )

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    LLMCallStartingEvent(
                        runId = runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = runId,
                        callId = callIds[1],
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = runId,
                        callId = callIds[1],
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.toModelInfo(),
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
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }
}
