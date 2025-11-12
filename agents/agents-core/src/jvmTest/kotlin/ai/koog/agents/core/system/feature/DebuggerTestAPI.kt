package ai.koog.agents.core.system.feature

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyEventGraph
import ai.koog.agents.core.feature.model.events.StrategyEventGraphEdge
import ai.koog.agents.core.feature.model.events.StrategyEventGraphNode
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.system.mock.ClientEventsCollector
import ai.koog.agents.core.system.mock.MockLLMProvider
import ai.koog.agents.core.system.mock.createAgent
import ai.koog.agents.core.system.mock.testClock
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.use
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlin.reflect.typeOf
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

internal object DebuggerTestAPI {

    internal const val HOST = "127.0.0.1"

    internal val defaultClientServerTimeout = 30.seconds

    internal suspend fun FeatureMessageRemoteClient.connectWithRetry(timeout: Duration) {
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

    internal val testBaseClient: HttpClient
        get() = HttpClient {
            install(HttpRequestRetry) {
                retryOnExceptionIf(maxRetries = 10) { _, cause ->
                    cause is IOException
                }
            }
        }

    internal val mockLLModel = LLModel(
        provider = MockLLMProvider(),
        id = "test-llm-id",
        capabilities = emptyList(),
        contextLength = 1_000,
    )

    internal suspend fun runAgentPortConfigThroughSystemVariablesTest(port: Int) = withContext(Dispatchers.Default) {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        val clientConfig = DefaultClientConnectionConfig(
            host = HOST,
            port = port,
            protocol = URLProtocol.HTTP
        )

        var expectedClientEvents = emptyList<FeatureMessage>()
        var actualClientEvents = emptyList<FeatureMessage>()

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
                val collectEventsJob = clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connect()
                collectEventsJob.join()

                val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
                val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

                actualClientEvents = clientEventsCollector.collectedEvents

                // Correct run id will be set after the 'collect events job' is finished.
                expectedClientEvents = listOf(
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
                    AgentClosingEvent(
                        agentId = agentId,
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertEquals(
            expectedClientEvents.size,
            actualClientEvents.size,
            "expectedEventsCount variable in the test need to be updated"
        )

        assertContentEquals(expectedClientEvents, actualClientEvents)
    }

    internal suspend fun runAgentConnectionWaitConfigThroughSystemVariablesTest(timeout: Duration) = withContext(Dispatchers.Default) {
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
}
