package ai.koog.agents.core.system.feature

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter
import ai.koog.agents.core.system.feature.DebuggerTestAPI.HOST
import ai.koog.agents.core.system.feature.DebuggerTestAPI.connectWithRetry
import ai.koog.agents.core.system.feature.DebuggerTestAPI.defaultClientServerTimeout
import ai.koog.agents.core.system.feature.DebuggerTestAPI.mockLLModel
import ai.koog.agents.core.system.feature.DebuggerTestAPI.testBaseClient
import ai.koog.agents.core.system.mock.ClientEventsCollector
import ai.koog.agents.core.system.mock.createAgent
import ai.koog.agents.core.system.mock.testClock
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.utils.io.use
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebuggerSubgraphTest {

    @Test
    fun `test debugger collect subgraph events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"

        val userPrompt = "Test input"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        // Tools
        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Test Data
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()

        val subgraphName = "test-subgraph"
        val subgraphNodeOutput = "$userPrompt (subgraph)"

        var expectedClientEvents = emptyList<FeatureMessage>()
        var actualClientEvents = emptyList<FeatureMessage>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>("test-strategy") {
                val nodeSubgraph by subgraph<String, String>(subgraphName) {
                    edge(nodeStart forwardTo nodeFinish transformed { subgraphNodeOutput })
                }
                nodeStart then nodeSubgraph then nodeFinish
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
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
                    ClientEventsCollector(client = client, expectedEventsCount = 14)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                val encodedUserInput = @OptIn(InternalAgentsApi::class)
                SerializationUtils.encodeDataToJsonElement(userPrompt, typeOf<String>())

                val encodedSubgraphOutput = @OptIn(InternalAgentsApi::class)
                SerializationUtils.encodeDataToJsonElement(subgraphNodeOutput, typeOf<String>())

                // Correct run id will be set after the 'collect events job' is finished.
                expectedClientEvents = listOf(
                    SubgraphExecutionStartingEvent(
                        runId = clientEventsCollector.runId,
                        subgraphName = subgraphName,
                        input = encodedUserInput,
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    SubgraphExecutionCompletedEvent(
                        runId = clientEventsCollector.runId,
                        subgraphName = subgraphName,
                        input = encodedUserInput,
                        output = encodedSubgraphOutput,
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                )

                actualClientEvents = clientEventsCollector.collectedEvents.filter { event ->
                    event is SubgraphExecutionStartingEvent ||
                        event is SubgraphExecutionCompletedEvent ||
                        event is SubgraphExecutionFailedEvent
                }

                isClientFinished.complete(true)
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
    fun `test debugger collect subgraph failed events`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"

        val userPrompt = "Test input"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        // Tools
        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Test Data
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()

        val subgraphName = "test-subgraph"
        val nodeSubgraphErrorName = "node-subgraph-error"

        val nodeSubgraphErrorMessage = "Test error in subgraph"

        var expectedClientEvents = emptyList<FeatureMessage>()
        var actualClientEvents = emptyList<FeatureMessage>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>("test-strategy") {
                val nodeSubgraph by subgraph<String, String>(subgraphName) {
                    val nodeSubgraphError by node<String, String>(nodeSubgraphErrorName) {
                        throw IllegalStateException(nodeSubgraphErrorMessage)
                    }
                    nodeStart then nodeSubgraphError then nodeFinish
                }
                nodeStart then nodeSubgraph then nodeFinish
            }

            val throwable = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
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
                val throwable = assertFailsWith<IllegalStateException> {
                    agent.run(userPrompt)
                }
                isClientFinished.await()
                throwable
            }
            assertEquals(nodeSubgraphErrorMessage, throwable.message)
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->

                val clientEventsCollector =
                    ClientEventsCollector(client = client, expectedEventsCount = 11)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connectWithRetry(defaultClientServerTimeout)
                collectEventsJob.join()

                val encodedUserInput = @OptIn(InternalAgentsApi::class)
                SerializationUtils.encodeDataToJsonElement(userPrompt, typeOf<String>())

                actualClientEvents = clientEventsCollector.collectedEvents.filter { event ->
                    event is SubgraphExecutionStartingEvent ||
                        event is SubgraphExecutionCompletedEvent ||
                        event is SubgraphExecutionFailedEvent
                }

                // Correct run id will be set after the 'collect events job' is finished.

                // Get error stack trace from an actual event since we currently do not have a way
                // to collect the same stack trace on a server side
                val actualFailedEvent = clientEventsCollector.collectedEvents.filterIsInstance<SubgraphExecutionFailedEvent>().firstOrNull()
                assertNotNull(actualFailedEvent, "Expected SubgraphExecutionFailedEvent event to be captured")

                expectedClientEvents = listOf(
                    SubgraphExecutionStartingEvent(
                        runId = clientEventsCollector.runId,
                        subgraphName = subgraphName,
                        input = encodedUserInput,
                        timestamp = testClock.now().toEpochMilliseconds(),
                    ),
                    SubgraphExecutionFailedEvent(
                        runId = clientEventsCollector.runId,
                        subgraphName = subgraphName,
                        input = encodedUserInput,
                        error = AIAgentError(
                            message = nodeSubgraphErrorMessage,
                            stackTrace = actualFailedEvent.error.stackTrace,
                            cause = actualFailedEvent.error.cause,
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertEquals(expectedClientEvents.size, actualClientEvents.size)
        assertContentEquals(expectedClientEvents, actualClientEvents)

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }
}
