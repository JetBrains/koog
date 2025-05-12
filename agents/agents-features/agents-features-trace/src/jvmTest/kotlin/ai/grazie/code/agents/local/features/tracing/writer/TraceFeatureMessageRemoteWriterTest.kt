package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.core.dsl.extension.nodeLLMRequest
import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.core.feature.remote.client.config.AgentFeatureClientConnectionConfig
import ai.grazie.code.agents.core.feature.remote.server.config.AgentFeatureServerConnectionConfig
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureMessageProcessor
import ai.grazie.code.agents.local.features.common.message.use
import ai.grazie.code.agents.local.features.common.remote.client.FeatureMessageRemoteClient
import ai.grazie.code.agents.local.features.tracing.NetUtil.findAvailablePort
import ai.grazie.code.agents.local.features.tracing.feature.TraceFeature
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.create
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TraceFeatureMessageRemoteWriterTest {

    companion object {
        private val logger = LoggerFactory.create(TraceFeatureMessageRemoteWriterTest::class)
        private val defaultClientServerTimeout = 5.seconds
    }

    private class TestFeatureMessageWriter : FeatureMessageProcessor() {

        val processedMessages = mutableListOf<FeatureMessage>()

        override suspend fun processMessage(message: FeatureMessage) {
            processedMessages.add(message)
        }

        override suspend fun close() { }
    }

    @Test
    fun `test health check on agent run`() = runBlocking {

        val port = findAvailablePort()
        val serverConfig = AgentFeatureServerConnectionConfig(port = port)
        val clientConfig = AgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = HttpProtocolVersion.HTTP_2_0.name)

        val isServerStarted = CompletableDeferred<Boolean>()
        val isClientFinished = CompletableDeferred<Boolean>()
        val isClientConnected = CompletableDeferred<Boolean>()

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                isServerStarted.await()
                client.connect()
                isClientConnected.complete(true)

                client.healthCheck()

                isClientFinished.complete(true)
            }
        }

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                launch {
                    writer.isReady.await()
                    isServerStarted.complete(true)
                }

                val strategy = simpleStrategy("tracing-test-strategy") {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                val agent = createAgent(strategy = strategy, scope = this) {
                    install(TraceFeature) {
                        messageFilter = { true }
                        addMessageProcessor(writer)
                    }
                }

                isClientConnected.await()
                agent.run("")

                isClientFinished.await()
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer collect events on agent run`() = runBlocking {

        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AgentFeatureServerConnectionConfig(port = port)
        val clientConfig = AgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = HttpProtocolVersion.HTTP_2_0.name)

        val expectedEvents = listOf(
            AgentCreateEvent(strategyName = strategyName),
            AgentStartedEvent(strategyName = strategyName),
            StrategyStartEvent(strategyName = strategyName),
            NodeExecutionStartEvent(nodeName = "__start__", stageName = "default", input = Unit::class.qualifiedName.toString()),
            NodeExecutionEndEvent(nodeName = "__start__", stageName = "default", input = Unit::class.qualifiedName.toString(), output = Unit::class.qualifiedName.toString()),
            NodeExecutionStartEvent(nodeName = "test LLM call", stageName = "default", input = "Test LLM call prompt"),
            LLMCallWithToolsStartEvent(prompt = "Test user message", tools = listOf("dummy", "__tools_list__")),
            LLMCallWithToolsEndEvent(responses = listOf("Default test response"), tools = listOf("dummy", "__tools_list__")),
            NodeExecutionEndEvent(nodeName = "test LLM call", stageName = "default", input = "Test LLM call prompt", output = "Assistant(content=Default test response)"),
            NodeExecutionStartEvent(nodeName = "test LLM call with tools", stageName = "default", input = "Test LLM call with tools prompt"),
            LLMCallWithToolsStartEvent(prompt = "Test user message", tools = listOf("dummy", "__tools_list__")),
            LLMCallWithToolsEndEvent(responses = listOf("Default test response"), tools = listOf("dummy", "__tools_list__")),
            NodeExecutionEndEvent(nodeName = "test LLM call with tools", stageName = "default", input = "Test LLM call with tools prompt", output = "Assistant(content=Default test response)"),
            StrategyFinishedEvent(strategyName = strategyName, result = "Done"),
            AgentFinishedEvent(strategyName = strategyName, result = "Done"),
        )

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isClientConnected = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                launch {
                    writer.isReady.await()
                    isServerStarted.complete(true)
                }

                val strategy = simpleStrategy(strategyName) {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                val agent = createAgent(
                    strategy = strategy,
                    scope = this,
                ) {
                    install(TraceFeature) {
                        messageFilter = { true }
                        addMessageProcessor(writer)
                    }
                }

                isClientConnected.await()
                agent.run("")

                isClientFinished.await()
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        actualEvents.add(event as DefinedFeatureEvent)
                        if (actualEvents.size == expectedEvents.size) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                isClientConnected.complete(true)

                collectEventsJob.join()

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
        val serverConfig = AgentFeatureServerConnectionConfig(port = port)
        val clientConfig = AgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = HttpProtocolVersion.HTTP_2_0.name)

        val actualEvents = mutableListOf<FeatureMessage>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->
                TestFeatureMessageWriter().use { fakeWriter ->

                    launch {
                        fakeWriter.isReady.await()
                        isServerStarted.complete(true)
                    }

                    val strategy = simpleStrategy(strategyName) {
                        val llmCallNode by nodeLLMRequest("test LLM call")
                        val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                        edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                        edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                        edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                    }

                    val agent = createAgent(
                        strategy = strategy,
                        scope = this,
                    ) {
                        install(TraceFeature) {
                            messageFilter = { true }
                            addMessageProcessor(fakeWriter)
                        }
                    }

                    agent.run("")

                    isClientFinished.await()
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
        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AgentFeatureServerConnectionConfig(port = port)
        val clientConfig = AgentFeatureClientConnectionConfig(
            host = "127.0.0.1",
            port = port,
            protocol = HttpProtocolVersion.HTTP_2_0.name
        )

        val expectedEvents = listOf(
            LLMCallWithToolsStartEvent("Test user message", listOf("dummy", "__tools_list__")),
            LLMCallWithToolsEndEvent(listOf("Default test response"), listOf("dummy", "__tools_list__")),
            LLMCallWithToolsStartEvent("Test user message", listOf("dummy", "__tools_list__")),
            LLMCallWithToolsEndEvent(listOf("Default test response"), listOf("dummy", "__tools_list__")),
        )

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isClientConnected = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                launch {
                    writer.isReady.await()
                    isServerStarted.complete(true)
                }

                val strategy = simpleStrategy(strategyName) {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                val agent = createAgent(
                    strategy = strategy,
                    scope = this,
                ) {
                    install(TraceFeature) {
                        messageFilter = { message ->
                            message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
                        }
                        addMessageProcessor(writer)
                    }
                }

                isClientConnected.await()
                agent.run("")

                isClientFinished.await()
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        actualEvents.add(event as DefinedFeatureEvent)
                        if (actualEvents.size >= expectedEvents.size) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                isClientConnected.complete(true)
                collectEventsJob.join()

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
