@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.example.a2a.simplejoke

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.agents.a2a.core.toKoogMessage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val BRIGHT_CYAN = "\u001B[1;36m"
private const val YELLOW = "\u001B[33m"
private const val BRIGHT_MAGENTA = "\u001B[1;35m"
private const val RED = "\u001B[31m"
private const val RESET = "\u001B[0m"

@OptIn(ExperimentalUuidApi::class)
suspend fun main() {
    println()
    println("${YELLOW}Starting Joke Generator A2A Client$RESET\n")

    // Set up the HTTP JSON-RPC transport
    val transport = HttpJSONRPCClientTransport(
        url = "http://localhost:9998$JOKE_GENERATOR_AGENT_PATH"
    )

    // Set up the agent card resolver
    val agentCardResolver = UrlAgentCardResolver(
        baseUrl = "http://localhost:9998",
        path = JOKE_GENERATOR_AGENT_CARD_PATH
    )

    // Create the A2A client
    val client = A2AClient(
        transport = transport,
        agentCardResolver = agentCardResolver
    )

    // Connect and fetch agent card
    client.connect()
    val agentCard = client.cachedAgentCard()
    println("${YELLOW}Connected to agent:$RESET\n${agentCard.name} (${agentCard.description})\n")

    // Read context ID
    println("${BRIGHT_CYAN}Context ID (which chat to start/continue):$RESET")
    val contextId = readln()
    println()

    // Start chat loop
    while (true) {
        println("${BRIGHT_CYAN}Request (/q to quit):$RESET")
        val request = readln()
        println()

        if (request == "/q") {
            break
        }

        val message = Message(
            messageId = Uuid.random().toString(),
            role = Role.User,
            parts = listOf(TextPart(request)),
            contextId = contextId
        )

        val response = client.sendMessage(
            Request(MessageSendParams(message = message))
        )

        val reply = (response.data as Message).toKoogMessage().content
        println("${BRIGHT_MAGENTA}Agent response:${RESET}\n$reply\n")
    }

    println("${RED}Conversation complete!$RESET")

    // Clean up
    transport.close()
}
