package ai.koog.agents.example.acp

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.client.ClientSupport
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

private val logger = KotlinLogging.logger {}

class TerminalClientSupport : ClientSupport {
    override suspend fun createClientSession(
        session: ClientSession,
        _sessionResponseMeta: JsonElement?
    ): ClientSessionOperations {
        return TerminalClientSessionOperations()
    }
}

class TerminalClientSessionOperations : ClientSessionOperations {
    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        println("Agent requested permissions for tool call: ${toolCall.title}. Choose one of the following options:")
        for ((i, permission) in permissions.withIndex()) {
            println("${i + 1}. ${permission.name}")
        }
        while (true) {
            val read = readln()
            val optionIndex = read.toIntOrNull()
            if (optionIndex != null && optionIndex in permissions.indices) {
                return RequestPermissionResponse(RequestPermissionOutcome.Selected(permissions[optionIndex].optionId), _meta)
            }
            println("Invalid option selected. Try again.")
        }
    }

    override suspend fun notify(
        notification: SessionUpdate,
        _meta: JsonElement?,
    ) {
        println("Agent sent notification:")
        notification.render()
    }
}

suspend fun CoroutineScope.runTerminalClient(transport: Transport) {
    // Create client-side connection
    val protocol = Protocol(this, transport)
    val client = Client(protocol, TerminalClientSupport())

    logger.info { "Starting agent process..." }

    // Connect to agent and start transport
    protocol.start()

    logger.info { "Connected to agent, initializing..." }

    val agentInfo = client.initialize(ClientInfo())
    println("Agent info: $agentInfo")

    println()

    // Create a session
    val session = client.newSession(
        SessionParameters(Paths.get("").absolutePathString(), emptyList())
    )

    println("=== Session created: ${session.sessionId} ===")
    println("Type your messages below. Use 'exit', 'quit', or Ctrl+C to stop.")
    println("=".repeat(60))
    println()

    try {
        // Start interactive chat loop
        while (true) {
            print("You: ")
            val userInput = readLine()

            // Check for exit conditions
            if (userInput == null || userInput.lowercase() in listOf("exit", "quit", "bye")) {
                println("\n=== Goodbye! ===")
                break
            }

            // Skip empty inputs
            if (userInput.isBlank()) {
                continue
            }

            try {
                session.prompt(listOf(ContentBlock.Text(userInput.trim()))).collect { event ->
                    when (event) {
                        is Event.SessionUpdateEvent -> {
                            event.update.render()
                        }

                        is Event.PromptResponseEvent -> {
                            when (event.response.stopReason) {
                                StopReason.END_TURN -> {
                                    // Normal completion - no action needed
                                    println("Success!")
                                }

                                StopReason.MAX_TOKENS -> {
                                    println("\n[Response truncated due to token limit]")
                                }

                                StopReason.MAX_TURN_REQUESTS -> {
                                    println("\n[Turn limit reached]")
                                }

                                StopReason.REFUSAL -> {
                                    println("\n[Agent declined to respond]")
                                }

                                StopReason.CANCELLED -> {
                                    println("\n[Response was cancelled]")
                                }
                            }
                        }
                    }
                }



                println() // Extra newline for readability

            } catch (e: Exception) {
                println("\n[Error: ${e.message}]")
                logger.error(e) { "Error during chat interaction" }
                println()
            }
        }

    } catch (e: Exception) {
        logger.error(e) { "Client error occurred" }
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        logger.info { "ACP client shutting down" }
    }
}
