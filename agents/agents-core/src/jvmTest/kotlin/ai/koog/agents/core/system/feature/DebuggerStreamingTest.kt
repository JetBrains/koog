package ai.koog.agents.core.system.feature

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter
import ai.koog.agents.core.system.feature.DebuggerTestAPI.HOST
import ai.koog.agents.core.system.feature.DebuggerTestAPI.connectWithRetry
import ai.koog.agents.core.system.feature.DebuggerTestAPI.defaultClientServerTimeout
import ai.koog.agents.core.system.feature.DebuggerTestAPI.mockLLModel
import ai.koog.agents.core.system.feature.DebuggerTestAPI.testBaseClient
import ai.koog.agents.core.system.mock.ClientEventsCollector
import ai.koog.agents.core.system.mock.MockLLMProvider
import ai.koog.agents.core.system.mock.assistantMessage
import ai.koog.agents.core.system.mock.createAgent
import ai.koog.agents.core.system.mock.systemMessage
import ai.koog.agents.core.system.mock.testClock
import ai.koog.agents.core.system.mock.userMessage
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.io.use
import io.ktor.http.URLProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

class DebuggerStreamingTest {

    @Test
    fun `test debugger collect streaming success events on agent run`() = runBlocking {
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

        var expectedClientEvents = emptyList<FeatureMessage>()
        var actualClientEvents = emptyList<FeatureMessage>()

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
                model = mockLLModel,
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
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector = ClientEventsCollector(client = client)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                val callIds = clientEventsCollector.collectedEvents.filterIsInstance<LLMStreamingStartingEvent>().map { it.callId }
                assertEquals(
                    1,
                    callIds.size,
                    "Expected 1 LLMCallStartingEvent, got ${callIds.size}"
                )

                // Correct run id will be set after the 'collect events job' is finished.
                expectedClientEvents = listOf(
                    LLMStreamingStartingEvent(
                        runId = clientEventsCollector.runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = mockLLModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    LLMStreamingFrameReceivedEvent(
                        runId = clientEventsCollector.runId,
                        callId = callIds[0],
                        frame = StreamFrame.Append(testLLMResponse),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    LLMStreamingCompletedEvent(
                        runId = clientEventsCollector.runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = mockLLModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    )
                )

                actualClientEvents = clientEventsCollector.collectedEvents.filter { event ->
                    event is LLMStreamingStartingEvent ||
                        event is LLMStreamingFrameReceivedEvent ||
                        event is LLMStreamingFailedEvent ||
                        event is LLMStreamingCompletedEvent
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertEquals(expectedClientEvents.size, actualClientEvents.size)
        assertContentEquals(expectedClientEvents, actualClientEvents)
    }

    @Test
    fun `test debugger collect streaming failed events on agent run`() = runBlocking {
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

        var expectedClientEvents = emptyList<FeatureMessage>()
        var actualClientEvents = emptyList<FeatureMessage>()

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

            val throwable = createAgent(
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
                assertFailsWith<IllegalStateException> {
                    agent.run(userPrompt)
                }
            }

            assertEquals(testStreamingErrorMessage, throwable.message)
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector = ClientEventsCollector(client = client)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                val callIds = clientEventsCollector.collectedEvents.filterIsInstance<LLMStreamingStartingEvent>().map { it.callId }
                assertEquals(
                    1,
                    callIds.size,
                    "Expected 1 LLMCallStartingEvent, got ${callIds.size}"
                )

                // Correct run id will be set after the 'collect events job' is finished.
                expectedClientEvents = listOf(
                    LLMStreamingStartingEvent(
                        runId = clientEventsCollector.runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    LLMStreamingFailedEvent(
                        runId = clientEventsCollector.runId,
                        callId = callIds[0],
                        error = AIAgentError(testStreamingErrorMessage, testStreamingStackTrace),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingCompletedEvent(
                        runId = clientEventsCollector.runId,
                        callId = callIds[0],
                        prompt = expectedLLMCallPrompt,
                        model = testModel.toModelInfo(),
                        tools = listOf(dummyTool.name),
                        timestamp = testClock.now().toEpochMilliseconds(),
                    )
                )

                actualClientEvents = clientEventsCollector.collectedEvents.filter { event ->
                    event is LLMStreamingStartingEvent ||
                        event is LLMStreamingFrameReceivedEvent ||
                        event is LLMStreamingFailedEvent ||
                        event is LLMStreamingCompletedEvent
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertEquals(expectedClientEvents.size, actualClientEvents.size)
        assertContentEquals(expectedClientEvents, actualClientEvents)
    }
}
