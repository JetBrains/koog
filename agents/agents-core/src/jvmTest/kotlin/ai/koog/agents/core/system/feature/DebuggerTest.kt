package ai.koog.agents.core.system.feature

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
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
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter
import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.core.system.getVMOptionOrNull
import ai.koog.agents.core.system.mock.ClientEventsCollector
import ai.koog.agents.core.system.mock.MockLLMProvider
import ai.koog.agents.core.system.mock.assistantMessage
import ai.koog.agents.core.system.mock.createAgent
import ai.koog.agents.core.system.mock.systemMessage
import ai.koog.agents.core.system.mock.testClock
import ai.koog.agents.core.system.mock.toolCallMessage
import ai.koog.agents.core.system.mock.toolResultMessage
import ai.koog.agents.core.system.mock.userMessage
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.testing.network.NetUtil
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.io.use
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

// System Properties set inside this test class affects other tests
// Isolate the environment by @Isolated annotation for these tests and make sure they are running without the parallelism.
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DebuggerTest {

    companion object {
        private val defaultClientServerTimeout = 30.seconds
        private const val HOST = "127.0.0.1"
    }

    private suspend fun FeatureMessageRemoteClient.connectWithRetry(timeout: Duration) {
        withTimeout(timeout) {
            while (true) {
                try {
                    connect()
                    return@withTimeout
                } catch (exception: Exception) {
                    if (exception is CancellationException) {
                        throw exception
                    }
                    delay(100)
                }
            }
        }
    }

    private val testBaseClient: HttpClient
        get() = HttpClient {
            install(HttpRequestRetry) {
                retryOnExceptionIf(maxRetries = 10) { _, cause ->
                    cause is IOException
                }
            }
        }

    @AfterEach
    fun cleanup() {
        // Clean up system properties user in tests to not affect other test runs
        @OptIn(ExperimentalAgentsApi::class)
        System.clearProperty(Debugger.KOOG_DEBUGGER_PORT_VM_OPTION)
        @OptIn(ExperimentalAgentsApi::class)
        System.clearProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION)
    }

    @Test
    fun `test feature message remote writer collect events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val nodeSendLLMCallName = "test-llm-call"
        val nodeExecuteToolName = "test-tool-call"
        val nodeSendToolResultName = "test-node-llm-send-tool-result"

        val dummyTool = DummyTool()
        val requestedDummyToolArgs = "test"

        val userPrompt = "Call the dummy tool with argument: $requestedDummyToolArgs"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"

        val mockResponse = "Return test result"

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Model
        val modelId = "test-llm-id"
        val testModel = LLModel(
            provider = MockLLMProvider(),
            id = modelId,
            capabilities = emptyList(),
            contextLength = 1_000,
        )

        // Prompt
        val promptId = "Test prompt id"
        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(
                content = userPrompt
            )
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(
                    toolName = dummyTool.name,
                    content = """{"dummy":"$requestedDummyToolArgs"}"""
                ),
                toolResultMessage(
                    toolCallId = "0",
                    toolName = dummyTool.name,
                    content = dummyTool.encodeResultToString(dummyTool.result),
                    metaInfo = RequestMetaInfo.create(testClock)
                )
            )
        )

        // Test Data
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            val strategy = strategy(strategyName) {
                val nodeSendInput by nodeLLMRequest(nodeSendLLMCallName)
                val nodeExecuteTool by nodeExecuteTool(nodeExecuteToolName)
                val nodeSendToolResult by nodeLLMSendToolResult(nodeSendToolResultName)

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
                    args = DummyTool.Args(requestedDummyToolArgs),
                    toolCallId = "0"
                ) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = testModel,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)

                    launch {
                        val messageProcessor = messageProcessors.single() as FeatureMessageRemoteWriter
                        val isServerStartedCheck = withTimeoutOrNull(defaultClientServerTimeout) {
                            messageProcessor.isOpen.first { it }
                        } != null

                        assertTrue(isServerStartedCheck, "Server did not start in time")
                    }
                }
            }.use { agent ->
                agent.run(userPrompt)
                isClientFinished.await()
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector =
                    ClientEventsCollector(client = client, expectedEventsCount = 20)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val llmCallGraphNode = StrategyEventGraphNode(id = nodeSendLLMCallName, name = nodeSendLLMCallName)
                val executeToolGraphNode = StrategyEventGraphNode(id = nodeExecuteToolName, name = nodeExecuteToolName)
                val sendToolResultGraphNode =
                    StrategyEventGraphNode(id = nodeSendToolResultName, name = nodeSendToolResultName)

                val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
                val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

                val expectedEvents = listOf(
                    AgentStartingEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    GraphStrategyStartingEvent(
                        runId = clientEventsCollector.runId,
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
                                StrategyEventGraphEdge(
                                    sourceNode = llmCallGraphNode,
                                    targetNode = executeToolGraphNode,
                                ),
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
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
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
                        runId = clientEventsCollector.runId,
                        nodeName = "test-llm-call",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"$requestedDummyToolArgs"}""")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-llm-call",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        output = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = toolCallMessage(dummyTool.name, content = """{"dummy":"$requestedDummyToolArgs"}"""),
                            dataType = typeOf<Message>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-tool-call",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = toolCallMessage(dummyTool.name, content = """{"dummy":"$requestedDummyToolArgs"}"""),
                            dataType = typeOf<Message.Tool.Call>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    ToolCallStartingEvent(
                        runId = clientEventsCollector.runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    ToolCallCompletedEvent(
                        runId = clientEventsCollector.runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                        result = dummyTool.result,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-tool-call",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = toolCallMessage(toolName = dummyTool.name, content = """{"dummy":"$requestedDummyToolArgs"}"""),
                            dataType = typeOf<Message.Tool.Call>()
                        ),
                        // TODO: KG-485. Update to include serialized [ReceivedToolResult] when it became a serializable type.
                        output = JsonPrimitive(dummyTool.result),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-node-llm-send-tool-result",
                        // TODO: KG-485. Update to include serialized [ReceivedToolResult] when it became a serializable type.
                        input = JsonPrimitive(dummyTool.result),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallStartingEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallCompletedEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.toModelInfo(),
                        responses = listOf(assistantMessage(mockResponse)),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-node-llm-send-tool-result",
                        // TODO: KG-485. Update to include serialized [ReceivedToolResult] when it became a serializable type.
                        input = JsonPrimitive(dummyTool.result),
                        output = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = assistantMessage(mockResponse),
                            dataType = typeOf<Message>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__finish__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = mockResponse,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
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
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName,
                        result = mockResponse,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    AgentCompletedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        result = mockResponse,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )

                assertEquals(
                    expectedEvents.size,
                    clientEventsCollector.collectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, clientEventsCollector.collectedEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer collect streaming success events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"

        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

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
            messages = expectedPrompt.messages
        )

        // Executor
        val testLLMResponse = "Default test response"

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(testLLMResponse).asDefaultResponse onUserRequestEquals userPrompt
        }

        // Test Data
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>("tracing-streaming-success") {
                val streamAndCollect by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

                edge(nodeStart forwardTo streamAndCollect)
                edge(
                    streamAndCollect forwardTo nodeFinish transformed { messages ->
                        messages.firstOrNull()?.content ?: ""
                    }
                )
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = testModel,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)

                    launch {
                        val messageProcessor = messageProcessors.single() as FeatureMessageRemoteWriter
                        val isServerStartedCheck = withTimeoutOrNull(defaultClientServerTimeout) {
                            messageProcessor.isOpen.first { it }
                        } != null

                        assertTrue(isServerStartedCheck, "Server did not start in time")
                    }
                }
            }.use { agent ->
                agent.run(userPrompt)
                isClientFinished.await()
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector =
                    ClientEventsCollector(client = client, expectedEventsCount = 13)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    LLMStreamingStartingEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo().modelIdentifierName,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    LLMStreamingFrameReceivedEvent(
                        runId = clientEventsCollector.runId,
                        frame = StreamFrame.Append(testLLMResponse),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    LLMStreamingCompletedEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo().modelIdentifierName,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    )
                )

                val actualEvents = clientEventsCollector.collectedEvents.filter { event ->
                    event is LLMStreamingStartingEvent ||
                        event is LLMStreamingFrameReceivedEvent ||
                        event is LLMStreamingFailedEvent ||
                        event is LLMStreamingCompletedEvent
                }

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

    @Test
    fun `test feature message remote writer collect streaming failed events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"

        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

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
            messages = expectedPrompt.messages
        )

        // Executor
        val testStreamingErrorMessage = "Test streaming error"
        var testStreamingStackTrace = ""

        val testStreamingExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> = emptyList()

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                val testException = IllegalStateException(testStreamingErrorMessage)
                testStreamingStackTrace = testException.stackTraceToString()
                throw testException
            }

            override suspend fun moderate(
                prompt: Prompt,
                model: LLModel
            ): ModerationResult {
                throw UnsupportedOperationException("Not used in test")
            }

            override fun close() {}
        }

        // Test Data
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>("tracing-streaming-success") {
                val streamAndCollect by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

                edge(nodeStart forwardTo streamAndCollect)
                edge(
                    streamAndCollect forwardTo nodeFinish transformed { messages ->
                        messages.firstOrNull()?.content ?: ""
                    }
                )
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = testStreamingExecutor,
                model = testModel,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)

                    launch {
                        val messageProcessor = messageProcessors.single() as FeatureMessageRemoteWriter
                        val isServerStartedCheck = withTimeoutOrNull(defaultClientServerTimeout) {
                            messageProcessor.isOpen.first { it }
                        } != null

                        assertTrue(isServerStartedCheck, "Server did not start in time")
                    }
                }
            }.use { agent ->
                val throwable = assertFails {
                    agent.run(userPrompt)
                }

                isClientFinished.await()

                assertTrue(throwable is IllegalStateException)
                assertEquals(testStreamingErrorMessage, throwable.message)
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector =
                    ClientEventsCollector(client = client, expectedEventsCount = 9)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    LLMStreamingStartingEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo().modelIdentifierName,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    LLMStreamingFailedEvent(
                        runId = clientEventsCollector.runId,
                        error = AIAgentError(testStreamingErrorMessage, testStreamingStackTrace),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingCompletedEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo().modelIdentifierName,
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    )
                )

                val actualEvents = clientEventsCollector.collectedEvents.filter { event ->
                    event is LLMStreamingStartingEvent ||
                        event is LLMStreamingFrameReceivedEvent ||
                        event is LLMStreamingFailedEvent ||
                        event is LLMStreamingCompletedEvent
                }

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

    //region Port

    @Test
    @Disabled(
        """
        '${@OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_ENV_VAR}' environment variable need to be set for a particular test via test framework.
        Currently, test framework that is used for Koog tests does not have ability to set env variables.
        Setting env variable in Gradle task does not work either, because there are tests that verify both
        cases when env variable is set and when it is not set.
        Disable test for now. Need to be enabled when we can set env variables in tests.
    """
    )
    fun `test read port from env variable`() = runBlocking {
        @OptIn(ExperimentalAgentsApi::class)
        val portEnvVar = getEnvironmentVariableOrNull(Debugger.KOOG_DEBUGGER_PORT_ENV_VAR)

        @OptIn(ExperimentalAgentsApi::class)
        assertNotNull(portEnvVar, "'${Debugger.KOOG_DEBUGGER_PORT_ENV_VAR}' env variable is not set")

        runAgentPortConfigThroughSystemVariablesTest(portEnvVar.toInt())
    }

    @Test
    fun `test read port from vm option`() = runBlocking {
        // Set VM option
        val port = 56712
        val portVmOptionName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_VM_OPTION
        val portEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_ENV_VAR
        System.setProperty(portVmOptionName, port.toString())

        val portEnvVar = getEnvironmentVariableOrNull(name = portEnvVarName)
        assertNull(
            portEnvVar,
            "Expected '$portEnvVarName' env variable is not set, but it is defined with value: <$portEnvVar>"
        )

        val portVMOption = getVMOptionOrNull(name = portVmOptionName)
        assertNotNull(
            portVMOption,
            "Expected '$portVmOptionName' VM option is not set"
        )

        runAgentPortConfigThroughSystemVariablesTest(port = portVMOption.toInt())
    }

    @Test
    fun `test read default port when not set by property or env variable or vm option`() = runBlocking {
        val portVmOptionName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_VM_OPTION
        val portEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_ENV_VAR

        val portEnvVar = getEnvironmentVariableOrNull(portEnvVarName)
        assertNull(portEnvVar, "Expected '$portEnvVarName' env variable is not set, but it exists with value: $portEnvVar")

        val portVMOption = getEnvironmentVariableOrNull(portEnvVarName)
        assertNull(portVMOption, "Expected '$portVmOptionName' VM option is not set, but it exists with value: $portVMOption")

        // Check default port available
        val isDefaultPortAvailable = NetUtil.isPortAvailable(DefaultServerConnectionConfig.DEFAULT_PORT)
        assertTrue(
            isDefaultPortAvailable,
            "Default port ${DefaultServerConnectionConfig.DEFAULT_PORT} is not available"
        )

        runAgentPortConfigThroughSystemVariablesTest(port = DefaultServerConnectionConfig.DEFAULT_PORT)
    }

    //endregion Port

    //region Client Connection Wait Timeout

    @Test
    fun `test client connection waiting timeout is set by property`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        // Test Data
        val port = findAvailablePort()
        val clientConnectionWaitTimeout = 1.seconds
        var actualAgentRunTime = Duration.ZERO

        // Server
        // The server will read the env variable or VM option to get a port value.
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)
                    // Set connection awaiting timeout
                    setAwaitInitialConnectionTimeout(clientConnectionWaitTimeout)
                }
            }.use { agent ->
                actualAgentRunTime = measureTime {
                    withTimeoutOrNull(defaultClientServerTimeout) {
                        agent.run(userPrompt)
                    }
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            serverJob.join()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertTrue(
            actualAgentRunTime in clientConnectionWaitTimeout..<defaultClientServerTimeout,
            "Expected actual agent run time is over <$clientConnectionWaitTimeout>, but got: <$actualAgentRunTime>"
        )
    }

    @Disabled(
        """
        '${@OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR}' environment variable need to be set for a particular test via test framework.
        Currently, test framework that is used for Koog tests does not have ability to set env variables.
        Setting env variable in Gradle task does not work either, because there are tests that verify both 
        cases when env variable is set and when it is not set.
        Disable test for now. Need to be enabled when we can set env variables in tests.
    """
    )
    @Test
    fun `test client connection waiting timeout is set by env variable`() = runBlocking {
        val portWaitConnectionMsEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR

        val connectionWaitTimeoutMsEnvVar = getEnvironmentVariableOrNull(portWaitConnectionMsEnvVarName)
        assertNotNull(connectionWaitTimeoutMsEnvVar, "'$portWaitConnectionMsEnvVarName' env variable is not set")

        runAgentConnectionWaitConfigThroughSystemVariablesTest(
            timeout = connectionWaitTimeoutMsEnvVar.toLong().toDuration(DurationUnit.MILLISECONDS)
        )
    }

    @Test
    fun `test client connection waiting timeout is set by vm option`() = runBlocking {
        val portWaitConnectionMsVmOptionName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION
        val portWaitConnectionMsEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR

        // Set VM option
        val timeout = 1.seconds
        System.setProperty(portWaitConnectionMsVmOptionName, timeout.inWholeMilliseconds.toString())

        val connectionWaitTimeoutEnvVar = getEnvironmentVariableOrNull(name = portWaitConnectionMsEnvVarName)

        assertNull(
            connectionWaitTimeoutEnvVar,
            "Expected '$portWaitConnectionMsEnvVarName' env variable is not set, " +
                "but it is defined with value: <$connectionWaitTimeoutEnvVar>"
        )

        val connectionWaitTimeoutMsVMOption = getVMOptionOrNull(name = portWaitConnectionMsVmOptionName)

        assertNotNull(
            connectionWaitTimeoutMsVMOption,
            "Expected '$portWaitConnectionMsVmOptionName' VM option is not set"
        )

        runAgentConnectionWaitConfigThroughSystemVariablesTest(
            timeout = connectionWaitTimeoutMsVMOption.toLong().toDuration(DurationUnit.MILLISECONDS)
        )
    }

    //endregion Client Connection Wait Timeout

    //region Private Methods

    private suspend fun runAgentPortConfigThroughSystemVariablesTest(port: Int) = withContext(Dispatchers.Default) {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        val clientConfig = DefaultClientConnectionConfig(
            host = HOST,
            port = port,
            protocol = URLProtocol.HTTP
        )

        val isClientFinished = CompletableDeferred<Boolean>()

        // Server
        // The server will read the env variable or VM option to get a port value.
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    // Do not set the port value explicitly through parameter.
                    // Use System env var 'KOOG_DEBUGGER_PORT' or VM option 'koog.debugger.port'
                }
            }.use { agent ->
                agent.run(userPrompt)
                isClientFinished.await()
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector = ClientEventsCollector(client = client, expectedEventsCount = 8)
                val collectEventsJob = clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connect()
                collectEventsJob.join()

                val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
                val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    AgentStartingEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    GraphStrategyStartingEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName,
                        graph = StrategyEventGraph(
                            nodes = listOf(
                                startGraphNode,
                                finishGraphNode
                            ),
                            edges = listOf(
                                StrategyEventGraphEdge(startGraphNode, finishGraphNode)
                            )
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionStartingEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
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
                        runId = clientEventsCollector.runId,
                        nodeName = "__finish__",
                        input = @OptIn(InternalAgentsApi::class)
                        SerializationUtils.encodeDataToJsonElementOrNull(
                            data = userPrompt,
                            dataType = typeOf<String>()
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    NodeExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__finish__",
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
                    StrategyCompletedEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName,
                        result = userPrompt,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    AgentCompletedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        result = userPrompt,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )

                assertEquals(
                    expectedEvents.size,
                    clientEventsCollector.collectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, clientEventsCollector.collectedEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    private suspend fun runAgentConnectionWaitConfigThroughSystemVariablesTest(timeout: Duration) = withContext(Dispatchers.Default) {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        // Test Data
        val port = findAvailablePort()
        var actualAgentRunTime = Duration.ZERO

        // Server
        // The server will read the env variable or VM option to get a port value.
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)
                    // Do not set the connection awaiting timeout explicitly through parameter.
                    // Use System env var 'KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR' or VM option 'koog.debugger.wait.connection.ms'
                }
            }.use { agent ->
                actualAgentRunTime = measureTime {
                    withTimeoutOrNull(defaultClientServerTimeout) {
                        agent.run(userPrompt)
                    }
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            serverJob.join()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertTrue(
            actualAgentRunTime in timeout..<defaultClientServerTimeout,
            "Expected actual agent run time is over <$timeout>, but got: <$actualAgentRunTime>"
        )
    }

    //endregion Private Methods
}
