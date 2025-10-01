package ai.koog.a2a.server.jsonrpc

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.TestAgentExecutor
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.test.BaseA2AProtocolTest
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket

abstract class BaseA2AServerJsonRpcTest : BaseA2AProtocolTest() {
    protected var testPort: Int? = null
    protected val testPath = "/a2a"
    protected lateinit var serverUrl: String

    protected lateinit var serverTransport: HttpJSONRPCServerTransport
    protected lateinit var clientTransport: HttpJSONRPCClientTransport
    protected lateinit var httpClient: HttpClient

    override lateinit var client: A2AClient

    open fun setup(): Unit = runBlocking {
        // Discover and take any free port
        testPort = ServerSocket(0).use { it.localPort }
        serverUrl = "http://localhost:$testPort$testPath"

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
            port = testPort!!,
            path = testPath,
            wait = false,
            agentCard = agentCard,
            agentCardPath = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
        )

        // Create client transport
        httpClient = HttpClient(CIO) {
            install(Logging) {
                level = LogLevel.ALL
            }

            install(HttpTimeout) {
                requestTimeoutMillis = testTimeout.inWholeMilliseconds
            }
        }

        clientTransport = HttpJSONRPCClientTransport(serverUrl, httpClient)

        client = A2AClient(
            transport = clientTransport,
            agentCardResolver = UrlAgentCardResolver(
                baseUrl = serverUrl,
                path = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
                baseHttpClient = httpClient,
            )
        )
    }

    open fun initClient(): Unit = runBlocking {
        client.connect()
    }

    open fun tearDown() = runBlocking {
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
        preferredTransport = TransportProtocol.Companion.JSONRPC,
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
