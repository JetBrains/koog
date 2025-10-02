package ai.koog.agents.example.a2a.advancedjoke

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

const val ADVANCED_JOKE_AGENT_PATH = "/advanced-joke-agent"
const val ADVANCED_JOKE_AGENT_CARD_PATH = "$ADVANCED_JOKE_AGENT_PATH/agent-card.json"

suspend fun main() {
    logger.info { "Starting Advanced Joke A2A Agent on http://localhost:9999" }

    // Create agent card with capabilities - this agent supports streaming and tasks
    val agentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "Advanced Joke Generator",
        description = "A sophisticated AI agent that generates jokes with clarifying questions and structured task flow",
        version = "1.0.0",
        url = "http://localhost:9999$ADVANCED_JOKE_AGENT_PATH",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = listOf(
            AgentInterface(
                url = "http://localhost:9999$ADVANCED_JOKE_AGENT_PATH",
                transport = TransportProtocol.JSONRPC,
            )
        ),
        capabilities = AgentCapabilities(
            streaming = true,  // Supports streaming responses
            pushNotifications = false,
            stateTransitionHistory = false,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "advanced_joke_generation",
                name = "Advanced Joke Generation",
                description = "Generates humorous jokes with interactive clarification and customization options",
                examples = listOf(
                    "Tell me a joke about programming",
                    "Generate a funny joke for teenagers",
                    "Make me laugh with a dad joke about cats"
                ),
                tags = listOf("humor", "jokes", "entertainment", "interactive")
            )
        ),
        supportsAuthenticatedExtendedCard = false
    )

    // Create agent executor
    val agentExecutor = JokeWriterAgentExecutor()

    // Create A2A server
    val a2aServer = A2AServer(
        agentExecutor = agentExecutor,
        agentCard = agentCard,
    )

    // Create and start server transport
    val serverTransport = HttpJSONRPCServerTransport(a2aServer)

    logger.info { "Advanced Joke Generator Agent ready at http://localhost:9999/$ADVANCED_JOKE_AGENT_PATH" }
    serverTransport.start(
        engineFactory = CIO,
        port = 9999,
        path = ADVANCED_JOKE_AGENT_PATH,
        wait = true, // Block until server stops
        agentCard = agentCard,
        agentCardPath = ADVANCED_JOKE_AGENT_CARD_PATH
    )
}
