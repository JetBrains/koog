package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.core.feature.remote.client.config.AIAgentFeatureClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.AIAgentFeatureServerConnectionConfig
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.common.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.features.tracing.NetUtil.findAvailablePort
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.utils.use
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TraceFeatureMessageRemoteWriterTest {

    companion object {
        private val logger = KotlinLogging.logger {  }
        private val defaultClientServerTimeout = 5.seconds
    }

    private class TestFeatureMessageWriter : FeatureMessageProcessor() {

        val processedMessages = mutableListOf<FeatureMessage>()

        override suspend fun processMessage(message: FeatureMessage) {
            processedMessages.add(message)
        }

        override suspend fun close() {}
    }

    @Test
    fun `test health check on agent run`() = runBlocking {

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

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

                val strategy = strategy("tracing-test-strategy") {
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

        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val expectedEvents = listOf(
            AIAgentStartedEvent(strategyName = strategyName),
            AIAgentStrategyStartEvent(strategyName = strategyName),
            AIAgentNodeExecutionStartEvent(nodeName = "__start__", input = ""),
            AIAgentNodeExecutionEndEvent(
                nodeName = "__start__",
                input = "",
                output = ""
            ),
            AIAgentNodeExecutionStartEvent(nodeName = "test LLM call", input = "Test LLM call prompt"),
            LLMCallWithToolsStartEvent(prompt = "Test user message", tools = listOf("dummy")),
            LLMCallWithToolsEndEvent(
                responses = listOf("Default test response"),
                tools = listOf("dummy")
            ),
            AIAgentNodeExecutionEndEvent(
                nodeName = "test LLM call",
                input = "Test LLM call prompt",
                output = "Assistant(content=Default test response, finishReason=null)"
            ),
            AIAgentNodeExecutionStartEvent(
                nodeName = "test LLM call with tools",
                input = "Test LLM call with tools prompt"
            ),
            LLMCallWithToolsStartEvent(prompt = "Test user message", tools = listOf("dummy")),
            LLMCallWithToolsEndEvent(
                responses = listOf("Default test response"),
                tools = listOf("dummy")
            ),
            AIAgentNodeExecutionEndEvent(
                nodeName = "test LLM call with tools",
                input = "Test LLM call with tools prompt",
                output = "Assistant(content=Default test response, finishReason=null)"
            ),
            AIAgentStrategyFinishedEvent(strategyName = strategyName, result = "Done"),
            AIAgentFinishedEvent(strategyName = strategyName, result = "Done"),
        )

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy(strategyName) {
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
        val serverConfig = AIAgentFeatureServerConnectionConfig(port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<FeatureMessage>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->
                TestFeatureMessageWriter().use { fakeWriter ->

                    val strategy = strategy(strategyName) {
                        val llmCallNode by nodeLLMRequest("test LLM call")
                        val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                        edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                        edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                        edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                    }

                    createAgent(strategy = strategy) {
                        install(Tracing) {
                            messageFilter = { true }
                            addMessageProcessor(fakeWriter)
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
        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = AIAgentFeatureServerConnectionConfig(port = port)
        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val expectedEvents = listOf(
            LLMCallWithToolsStartEvent("Test user message", listOf("dummy")),
            LLMCallWithToolsEndEvent(listOf("Default test response"), listOf("dummy")),
            LLMCallWithToolsStartEvent("Test user message", listOf("dummy")),
            LLMCallWithToolsEndEvent(listOf("Default test response"), listOf("dummy")),
        )

        val actualEvents = mutableListOf<DefinedFeatureEvent>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val strategy = strategy(strategyName) {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                createAgent(strategy = strategy) {
                    install(Tracing) {
                        messageFilter = { message ->
                            message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
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
