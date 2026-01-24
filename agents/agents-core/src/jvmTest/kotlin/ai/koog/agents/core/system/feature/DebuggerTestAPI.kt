package ai.koog.agents.core.system.feature

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.AIAgentFeatureTestAPI.testClock
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
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
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.testing.agent.agentExecutionInfo
import ai.koog.agents.testing.feature.message.singleEvent
import ai.koog.agents.testing.feature.message.singleNodeEvent
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.use
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.URLProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        val expectedFilteredEvents = mutableListOf<FeatureMessage>()
        val actualFilteredEvents = mutableListOf<FeatureMessage>()

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

                val startGraphNode = StrategyEventGraphNode(id = START_NODE_PREFIX, name = START_NODE_PREFIX)
                val finishGraphNode = StrategyEventGraphNode(id = FINISH_NODE_PREFIX, name = FINISH_NODE_PREFIX)

                // Expected events
                actualFilteredEvents.addAll(clientEventsCollector.collectedEvents)

                val actualAgentClosingEvent = actualFilteredEvents.singleEvent<AgentClosingEvent>()
                val actualAgentStartingEvent = actualFilteredEvents.singleEvent<AgentStartingEvent>()
                val actualStrategyStartingEvent = actualFilteredEvents.singleEvent<GraphStrategyStartingEvent>()
                val actualNodeStartEvent = actualFilteredEvents.singleNodeEvent(START_NODE_PREFIX)
                val actualNodeFinishEvent = actualFilteredEvents.singleNodeEvent(FINISH_NODE_PREFIX)

                // Correct run id will be set after the 'collect events job' is finished.
                expectedFilteredEvents.addAll(
                    listOf(
                        AgentStartingEvent(
                            eventId = actualAgentStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId),
                            agentId = agentId,
                            runId = clientEventsCollector.runId,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        GraphStrategyStartingEvent(
                            eventId = actualStrategyStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName),
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
                            eventId = actualNodeStartEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, START_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = START_NODE_PREFIX,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeStartEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, START_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = START_NODE_PREFIX,
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
                            eventId = actualNodeStartEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, FINISH_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = FINISH_NODE_PREFIX,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeStartEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, FINISH_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = FINISH_NODE_PREFIX,
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
                            eventId = actualStrategyStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName),
                            runId = clientEventsCollector.runId,
                            strategyName = strategyName,
                            result = userPrompt,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        AgentCompletedEvent(
                            eventId = actualAgentStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId),
                            agentId = agentId,
                            runId = clientEventsCollector.runId,
                            result = userPrompt,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        AgentClosingEvent(
                            eventId = actualAgentClosingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId),
                            agentId = agentId,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                    )
                )
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertEquals(
            expectedFilteredEvents.size,
            actualFilteredEvents.size,
            "expectedEventsCount variable in the test need to be updated"
        )

        assertContentEquals(
            expectedFilteredEvents,
            actualFilteredEvents,
            "Mismatch between collected events.\n" +
                "Expected:\n${expectedFilteredEvents.joinToString("\n") { event ->
                    event as DefinedFeatureEvent
                    " - ${event::class.simpleName} (part: ${event.executionInfo.partName}, parent part: ${event.executionInfo.parent?.partName})"
                }}\n" +
                "Actual:\n${actualFilteredEvents.joinToString("\n") { event ->
                    event as DefinedFeatureEvent
                    " - ${event::class.simpleName} (part: ${event.executionInfo.partName}, parent part: ${event.executionInfo.parent?.partName})"
                }}"
        )
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
