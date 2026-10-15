package ai.koog.a2a.server.jsonrpc

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.consts.A2APaths
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
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
            agentCardPath = A2APaths.AGENT_CARD_WELL_KNOWN_PATH,
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

        val resolvedAgentCard = UrlAgentCardResolver(
            baseUrl = serverUrl,
            path = A2APaths.AGENT_CARD_WELL_KNOWN_PATH,
            baseHttpClient = httpClient,
        ).resolve()

        client = A2AClient(
            card = resolvedAgentCard,
            transport = clientTransport,
        )
    }

    open fun tearDown() = runBlocking {
        clientTransport.close()
        serverTransport.stop()
    }

    private fun createAgentCard(): AgentCard = AgentCard(
        name = "Hello World Agent",
        description = "Just a hello world agent",
        supportedInterfaces = listOf(
            AgentInterface(
                url = "http://localhost:9999/",
                protocolBinding = TransportProtocol.JSONRPC,
                protocolVersion = "1.0",
            )
        ),
        version = "1.0.0",
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            extendedAgentCard = true,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "hello_world",
                name = "Returns hello world",
                description = "just returns hello world",
                tags = listOf("hello world"),
                examples = listOf("hi", "hello world"),
            )
        ),
    )

    private fun createExtendedAgentCard(): AgentCard = createAgentCard().let { card ->
        card.copy(
            name = "Hello World Agent - Extended Edition",
            description = "The full-featured hello world agent for authenticated users.",
            version = "1.0.1",
            skills = card.skills + AgentSkill(
                id = "super_hello_world",
                name = "Returns a SUPER Hello World",
                description = "A more enthusiastic greeting, only for authenticated users.",
                tags = listOf("hello world", "super", "extended"),
                examples = listOf("super hi", "give me a super hello"),
            ),
        )
    }
}
