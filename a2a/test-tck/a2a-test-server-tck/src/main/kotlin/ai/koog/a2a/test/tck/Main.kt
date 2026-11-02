package ai.koog.a2a.test.tck

import ai.koog.a2a.consts.A2APaths
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.Netty

private val logger = KotlinLogging.logger {}

private const val PORT = 9999

suspend fun main() {
    logger.info { "Starting TCK A2A Agent on http://localhost:$PORT" }

    val agentCard = AgentCard(
        name = "TCK A2A Agent",
        description = "A2A agent for validating the Koog A2A SDK with the A2A Technology Compatibility Kit (TCK)",
        version = "1.0.0",
        supportedInterfaces = listOf(
            // The TCK client posts to the base URL with a trailing slash, so JSON-RPC is served at the root path.
            AgentInterface(
                url = "http://localhost:$PORT",
                protocolBinding = TransportProtocol.JSONRPC,
                protocolVersion = "1.0",
            )
        ),
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = false,
            extendedAgentCard = true,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "tck",
                name = "TCK Agent",
                description = "Behaves according to the message id prefix sent by the TCK",
                tags = listOf("tck"),
            )
        ),
    )

    val agentCardExtended = agentCard.copy(
        name = "TCK A2A Agent - Extended",
        description = "The extended agent card of the TCK A2A agent",
    )

    val a2aServer = A2AServer(
        agentExecutor = TckAgentExecutor(),
        agentCard = agentCard,
        agentCardExtended = agentCardExtended,
    )

    HttpJSONRPCServerTransport(a2aServer).start(
        engineFactory = Netty,
        port = PORT,
        path = "/",
        wait = true,
        agentCard = agentCard,
        agentCardPath = A2APaths.AGENT_CARD_WELL_KNOWN_PATH,
    )
}
