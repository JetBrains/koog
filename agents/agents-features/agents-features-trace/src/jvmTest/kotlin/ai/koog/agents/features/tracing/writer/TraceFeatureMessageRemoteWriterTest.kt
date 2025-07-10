package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.core.feature.remote.client.config.AIAgentFeatureClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.AIAgentFeatureServerConnectionConfig
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.features.tracing.*
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TraceFeatureMessageRemoteWriterTest {

    companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriterTest")
        private val defaultClientServerTimeout = 20.seconds
        private val host = "127.0.0.1"
    }

    @Test
    fun `test health check on agent run`() = runBlocking {

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(host = host, port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = host, port = port, protocol = URLProtocol.HTTP)

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
                        messageFilter = { true }
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

        val agentId = "test-agent-id"
        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(host = host, port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = host, port = port, protocol = URLProtocol.HTTP)

        val userPrompt = "Test user prompt"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
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
            messages = expectedPrompt.messages + userMessage(content = "Test LLM call prompt")
        )

        val testModel = LLModel(
            provider = TestLLMProvider(),
            id = "test-llm-id",
            capabilities = emptyList()
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = "Test LLM call prompt"),
                assistantMessage(content = "Default test response"),
                userMessage(content = "Test LLM call with tools prompt")
            )
        )

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy<String, String>(strategyName) {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                createAgent(
                    strategy = strategy,
                    promptId = promptId,
                    model = testModel,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    assistantPrompt = assistantPrompt,
                ) {
                    install(Tracing) {
                        messageFilter = { true }
                        addMessageProcessor(writer)
                    }
                }.use { agent ->

                    agent.run("")
                    isServerStarted.complete(true)
                    isClientFinished.await()
                }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                var runId = ""

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        if (event is AIAgentStartedEvent) {
                            runId = event.runId
                        }

                        actualEvents.add(event as DefinedFeatureEvent)

                        if (actualEvents.size >= 14) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                collectEventsJob.join()

                val expectedEvents = listOf(
                    AIAgentStartedEvent(
                        agentId = agentId,
                        runId = runId,
                        strategyName = strategyName
                    ),
                    AIAgentStrategyStartEvent(
                        runId = runId,
                        strategyName = strategyName
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = runId,
                        nodeName = "__start__",
                        input = ""
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = runId,
                        nodeName = "__start__",
                        input = "",
                        output = ""
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = runId,
                        nodeName = "test LLM call",
                        input = "Test LLM call prompt"
                    ),
                    BeforeLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        tools = listOf("dummy")
                    ),
                    AfterLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage("Default test response"))
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = runId,
                        nodeName = "test LLM call",
                        input = "Test LLM call prompt",
                        output = assistantMessage("Default test response").toString()
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = runId,
                        nodeName = "test LLM call with tools",
                        input = "Test LLM call with tools prompt"
                    ),
                    BeforeLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        tools = listOf("dummy")
                    ),
                    AfterLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage("Default test response")),
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = runId,
                        nodeName = "test LLM call with tools",
                        input = "Test LLM call with tools prompt",
                        output = assistantMessage("Default test response").toString()
                    ),
                    AIAgentStrategyFinishedEvent(
                        runId = runId,
                        strategyName = strategyName,
                        result = "Done"
                    ),
                    AIAgentFinishedEvent(
                        agentId = agentId,
                        runId = runId,
                        result = "Done"
                    ),
                )

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
    fun `test feature message remote writer is not set`() = runBlocking {

        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(host = host, port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = host, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<FeatureMessage>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { remoteWriter ->
                TestFeatureMessageWriter().use { testWriter ->

                    val strategy = strategy<String, String>(strategyName) {
                        val llmCallNode by nodeLLMRequest("test LLM call")
                        val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                        edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                        edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                        edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                    }

                    createAgent(strategy = strategy) {
                        install(Tracing) {
                            messageFilter = { true }
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
        val agentId = "test-agent-id"
        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(host = host, port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = host, port = port, protocol = URLProtocol.HTTP)

        val userPrompt = "Test user prompt"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        val testModel = LLModel(
            provider = TestLLMProvider(),
            id = "test-llm-id",
            capabilities = emptyList()
        )

        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(content = "Test LLM call prompt")
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = "Test LLM call prompt"),
                assistantMessage(content = "Default test response"),
                userMessage(content = "Test LLM call with tools prompt")
            )
        )

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy<String, String>(strategyName) {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                createAgent(
                    agentId = agentId,
                    strategy = strategy,
                    promptId = promptId,
                    model = testModel,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    assistantPrompt = assistantPrompt,
                ) {
                    install(Tracing) {
                        messageFilter = { message ->
                            message is BeforeLLMCallEvent || message is AfterLLMCallEvent
                        }
                        addMessageProcessor(writer)
                    }
                }.use { agent ->
                    agent.run("")
                    isServerStarted.complete(true)
                    isClientFinished.await()
                }
            }
        }

        val clientJob = launch {
            var runId = ""

            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        if (event is BeforeLLMCallEvent) {
                            runId = event.runId
                        }

                        actualEvents.add(event as DefinedFeatureEvent)

                        if (actualEvents.size >= 4) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()
                client.connect()
                collectEventsJob.join()

                val expectedEvents = listOf(
                    BeforeLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        tools = listOf("dummy")
                    ),
                    AfterLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage("Default test response"))
                    ),
                    BeforeLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        tools = listOf("dummy")
                    ),
                    AfterLLMCallEvent(
                        runId = runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage("Default test response"))
                    ),
                )

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
