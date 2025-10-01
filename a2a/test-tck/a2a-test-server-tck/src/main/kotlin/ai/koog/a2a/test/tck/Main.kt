package ai.koog.a2a.test.tck

import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.AuthorizationCodeOAuthFlow
import ai.koog.a2a.model.HTTPAuthSecurityScheme
import ai.koog.a2a.model.OAuth2SecurityScheme
import ai.koog.a2a.model.OAuthFlows
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.Netty

private val logger = KotlinLogging.logger {}

suspend fun main() {
    logger.info { "Starting TCK A2A Agent on http://localhost:9999" }

    // Define security schemes
    val httpBearerScheme = HTTPAuthSecurityScheme(
        scheme = "bearer",
        description = "HTTP Bearer token authentication"
    )

    val oauth2Scheme = OAuth2SecurityScheme(
        flows = OAuthFlows(
            authorizationCode = AuthorizationCodeOAuthFlow(
                authorizationUrl = "https://auth.example.com/oauth/authorize",
                tokenUrl = "https://auth.example.com/oauth/token",
                scopes = mapOf(
                    "read" to "Read access",
                    "write" to "Write access"
                )
            )
        ),
        description = "OAuth 2.0 authentication"
    )

    val securitySchemes = mapOf(
        "bearerAuth" to httpBearerScheme,
        "oauth2" to oauth2Scheme
    )

    // Create agent card with capabilities and security
    val agentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "TCK A2A Agent",
        description = "A complete A2A agent implementation designed specifically for testing with the A2A Technology Compatibility Kit (TCK)",
        version = "1.0.0",
        url = "http://localhost:9999/a2a",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = listOf(
            AgentInterface(
                url = "http://localhost:9999/a2a",
                transport = TransportProtocol.JSONRPC,
            )
        ),
        capabilities = AgentCapabilities(
            streaming = true,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "tck_agent",
                name = "TCK Agent",
                description = "A complete A2A agent implementation designed for TCK testing",
                examples = listOf("hi", "hello world", "how are you", "goodbye"),
                tags = listOf("tck", "testing", "core", "complete")
            )
        ),
        securitySchemes = securitySchemes,
        security = listOf(
            mapOf("bearerAuth" to emptyList()),
            mapOf("oauth2" to listOf("read", "write"))
        ),
        supportsAuthenticatedExtendedCard = false
    )

    // Create extended agent card (same as basic for testing purposes)
    val agentCardExtended = agentCard.copy(
        name = "TCK A2A Agent - Extended Edition",
        description = "The full-featured A2A agent for authenticated users."
    )

    // Create agent executor
    val agentExecutor = TckAgentExecutor()

    // Create A2A server
    val a2aServer = A2AServer(
        agentExecutor = agentExecutor,
        agentCard = agentCard,
        agentCardExtended = agentCardExtended,
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
    )

    // Create and start server transport
    val serverTransport = HttpJSONRPCServerTransport(a2aServer)

    logger.info { "Authentication tests will document SDK gaps with expected failures" }
    serverTransport.start(
        engineFactory = Netty,
        port = 9999,
        path = "/a2a",
        wait = true, // Block until server stops
        agentCard = agentCard,
        agentCardPath = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH
    )
}
