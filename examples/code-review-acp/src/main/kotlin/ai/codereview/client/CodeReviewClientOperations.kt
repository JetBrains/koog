package ai.codereview.client

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ClientCapabilities
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

class CodeReviewClientSessionOperations : ClientSessionOperations {
    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        notification.render()
    }

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        val choice = permissions.firstOrNull()
            ?: error("Agent requested permission for ${toolCall.title} with no options provided.")
        return RequestPermissionResponse(RequestPermissionOutcome.Selected(choice.optionId), null)
    }
}

/**
 * Runs a single code-review prompt against the agent and prints the streamed updates.
 *
 * @param baseRef git ref to compare the current branch against (e.g. `develop`, `main`, `HEAD~3`).
 */
suspend fun CoroutineScope.runCodeReviewClient(transport: Transport, baseRef: String) {
    val protocol = Protocol(this, transport)
    val client = Client(protocol)
    protocol.start()

    logger.info { "Connected to agent, initializing…" }

    client.initialize(ClientInfo(capabilities = ClientCapabilities()))

    val session = client.newSession(
        SessionCreationParameters(Paths.get("").absolutePathString(), emptyList())
    ) { _, _ -> CodeReviewClientSessionOperations() }

    val userPrompt = "Review the current branch against $baseRef"
    println("Code Review Agent ready.")
    println("Prompt: \"$userPrompt\"\n")

    session.prompt(listOf(ContentBlock.Text(userPrompt))).collect { event ->
        when (event) {
            is Event.SessionUpdateEvent -> event.update.render()
            is Event.PromptResponseEvent -> {
                println()
                when (event.response.stopReason) {
                    StopReason.END_TURN -> println("[Review complete]")
                    StopReason.MAX_TURN_REQUESTS -> println("[Turn limit reached]")
                    else -> println("[Done: ${event.response.stopReason}]")
                }
            }
        }
    }
}
