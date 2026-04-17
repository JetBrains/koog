package ai.koog.agents.core.system.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.AIAgentFeatureTestAPI.testClock
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyStartingEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.system.feature.DebuggerTestAPI.HOST
import ai.koog.agents.core.system.feature.DebuggerTestAPI.defaultClientServerTimeout
import ai.koog.agents.core.system.feature.DebuggerTestAPI.mockLLModel
import ai.koog.agents.core.system.feature.DebuggerTestAPI.testBaseClient
import ai.koog.agents.core.system.mock.ClientEventsCollector
import ai.koog.agents.core.system.mock.TestAgentFactory.createFunctionalAgent
import ai.koog.agents.core.system.mock.TestAgentFactory.createGraphAgent
import ai.koog.agents.testing.feature.message.singleEvent
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import io.ktor.http.URLProtocol
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Disabled("Flaky, see #1124")
class DebuggerStrategyEventsTest {
    private val serializer = KotlinxSerializer()

    @Test
    @OptIn(InternalAgentsApi::class)
    fun testGraphStrategyStartingEvent() = runBlocking {
        // Agent Config
        val agentId = "test-graph-agent-id"
        val strategyName = "test-graph-strategy"
        val userPrompt = "Test graph strategy"

        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualFilteredEvents = mutableListOf<FeatureMessage>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createGraphAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
                model = mockLLModel,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)
                }
            }.use { agent ->
                agent.run(userPrompt, null)
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

                actualFilteredEvents.addAll(clientEventsCollector.collectedEvents)

                val actualStrategyStartingEvent = actualFilteredEvents.singleEvent<GraphStrategyStartingEvent>()

                assertNotNull(actualStrategyStartingEvent)
                assertEquals(strategyName, actualStrategyStartingEvent.strategyName)
                assertEquals(clientEventsCollector.runId, actualStrategyStartingEvent.runId)
                assertNotNull(actualStrategyStartingEvent.graph)
                assertTrue(actualStrategyStartingEvent.graph.nodes.isNotEmpty())
                assertTrue(actualStrategyStartingEvent.graph.edges.isNotEmpty())
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    @OptIn(InternalAgentsApi::class)
    fun testFunctionalStrategyStartingEvent() = runBlocking {
        // Agent Config
        val agentId = "test-functional-agent-id"
        val strategyName = "test-functional-strategy"
        val userPrompt = "test input"

        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualFilteredEvents = mutableListOf<FeatureMessage>()

        // Server
        val serverJob = launch {
            val mockExecutor = getMockExecutor(serializer, clock = testClock) { }

            createFunctionalAgent(
                agentId = agentId,
                promptExecutor = mockExecutor,
                userPrompt = userPrompt,
                strategy = functionalStrategy(strategyName) { input: String ->
                    input.uppercase()
                },

            )

            AIAgent(
                id = agentId,
                systemPrompt = "You are helpful",
                promptExecutor = mockExecutor,
                llmModel = mockLLModel,
                strategy = functionalStrategy(strategyName) { input: String ->
                    input.uppercase()
                }
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)
                }
            }.use { agent ->
                agent.run(userPrompt, null)
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

                actualFilteredEvents.addAll(clientEventsCollector.collectedEvents)

                val actualStrategyStartingEvent = actualFilteredEvents.singleEvent<StrategyStartingEvent>()

                assertNotNull(actualStrategyStartingEvent)
                assertEquals(strategyName, actualStrategyStartingEvent.strategyName)
                assertEquals(clientEventsCollector.runId, actualStrategyStartingEvent.runId)
                // FunctionalStrategyStartingEvent does not have a graph field
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }
}
