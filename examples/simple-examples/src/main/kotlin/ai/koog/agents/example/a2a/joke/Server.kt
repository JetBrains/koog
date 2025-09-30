package ai.koog.agents.example.a2a.joke

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.cio.CIO

private val logger = KotlinLogging.logger {}

const val JOKE_GENERATOR_AGENT_PATH = "/joke-generator-agent"
const val JOKE_GENERATOR_AGENT_CARD_PATH = "$JOKE_GENERATOR_AGENT_PATH/agent-card.json"

suspend fun main() {
    logger.info { "Starting Joke A2A Agent on http://localhost:9998" }

    // Create agent card with capabilities
    val agentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "Joke Generator 3000",
        description = "A helpful AI agent that generates jokes based on user requests",
        version = "1.0.0",
        url = "http://localhost:9998$JOKE_GENERATOR_AGENT_PATH",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = listOf(
            AgentInterface(
                url = "http://localhost:9998$JOKE_GENERATOR_AGENT_PATH",
                transport = TransportProtocol.JSONRPC,
            )
        ),
        capabilities = AgentCapabilities(
            streaming = false,
            pushNotifications = false,
            stateTransitionHistory = false,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "joke_generation",
                name = "Joke Generation",
                description = "Generates humorous jokes on various topics",
                examples = listOf(
                    "Tell me a joke",
                    "Generate a funny joke about programming",
                    "Make me laugh with a dad joke"
                ),
                tags = listOf("humor", "jokes", "entertainment")
            )
        ),
        supportsAuthenticatedExtendedCard = false
    )

    // Create agent executor
    val agentExecutor = JokeAgentExecutor()

    // Create A2A server
    val a2aServer = A2AServer(
        agentExecutor = agentExecutor,
        agentCard = agentCard,
    )

    // Create and start server transport
    val serverTransport = HttpJSONRPCServerTransport(a2aServer)

    logger.info { "Joke Generator Agent ready at http://localhost:9998/$JOKE_GENERATOR_AGENT_PATH" }
    serverTransport.start(
        engineFactory = CIO,
        port = 9998,
        path = JOKE_GENERATOR_AGENT_PATH,
        wait = true, // Block until server stops
        agentCard = agentCard,
        agentCardPath = JOKE_GENERATOR_AGENT_CARD_PATH
    )
}
