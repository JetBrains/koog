package ai.koog.a2a.server

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.test.BaseA2AProtocolTest
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.BeforeTest

/**
 * Integration test class for testing the JSON-RPC HTTP communication in the A2A server context.
 * This class ensures the proper functioning and correctness of the A2A protocol over HTTP
 * using the JSON-RPC standard.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A2AServerJsonRpcIntegrationTest : BaseA2AProtocolTest() {

    companion object {
        private const val TEST_PORT = 9999
        private const val TEST_PATH = "/a2a"
        private const val SERVER_URL = "http://localhost:$TEST_PORT$TEST_PATH"
    }

    private lateinit var serverTransport: HttpJSONRPCServerTransport
    private lateinit var clientTransport: HttpJSONRPCClientTransport
    private lateinit var httpClient: HttpClient

    override lateinit var client: A2AClient

    @BeforeAll
    fun setup(): Unit = runBlocking {
        // Create agent cards
        val agentCard = createAgentCard()
        val agentCardExtended = createExtendedAgentCard()

        // Create test agent executor
        val testAgentExecutor = TestAgentExecutor()

        // Create A2A server
        val a2aServer = A2AServer(
            agentExecutor = testAgentExecutor,
            agentCard = agentCard,
            agentCardExtended = agentCardExtended,
            pushConfigStorage = InMemoryPushNotificationConfigStorage()
        )

        // Create server transport
        serverTransport = HttpJSONRPCServerTransport(a2aServer)

        // Start server
        serverTransport.start(
            engineFactory = Netty,
            port = TEST_PORT,
            path = TEST_PATH,
            wait = false,
            agentCard = agentCard,
            agentCardPath = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
        )

        // Create client transport
        httpClient = HttpClient(CIO) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }

        clientTransport = HttpJSONRPCClientTransport(SERVER_URL, httpClient)

        client = A2AClient(
            transport = clientTransport,
            agentCardResolver = UrlAgentCardResolver(
                baseUrl = SERVER_URL,
                path = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH
            )
        )
    }

    @BeforeTest
    fun initClient(): Unit = runBlocking {
        client.connect()
    }

    @AfterAll
    fun tearDown(): Unit = runBlocking {
        clientTransport.close()
        serverTransport.stop()
    }

    private fun createAgentCard(): AgentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "Hello World Agent",
        description = "Just a hello world agent",
        url = "http://localhost:9999/",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = null,
        iconUrl = null,
        provider = null,
        version = "1.0.0",
        documentationUrl = null,
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = null,
            extensions = null
        ),
        securitySchemes = null,
        security = null,
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "hello_world",
                name = "Returns hello world",
                description = "just returns hello world",
                tags = listOf("hello world"),
                examples = listOf("hi", "hello world"),
                inputModes = null,
                outputModes = null,
                security = null
            )
        ),
        supportsAuthenticatedExtendedCard = true,
        signatures = null
    )

    private fun createExtendedAgentCard(): AgentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "Hello World Agent - Extended Edition",
        description = "The full-featured hello world agent for authenticated users.",
        url = "http://localhost:9999/",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = null,
        iconUrl = null,
        provider = null,
        version = "1.0.1",
        documentationUrl = null,
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = null,
            extensions = null
        ),
        securitySchemes = null,
        security = null,
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "hello_world",
                name = "Returns hello world",
                description = "just returns hello world",
                tags = listOf("hello world"),
                examples = listOf("hi", "hello world"),
                inputModes = null,
                outputModes = null,
                security = null
            ),
            AgentSkill(
                id = "super_hello_world",
                name = "Returns a SUPER Hello World",
                description = "A more enthusiastic greeting, only for authenticated users.",
                tags = listOf("hello world", "super", "extended"),
                examples = listOf("super hi", "give me a super hello"),
                inputModes = null,
                outputModes = null,
                security = null
            )
        ),
        supportsAuthenticatedExtendedCard = true,
        signatures = null
    )
}
