package ai.codereview.app

import ai.codereview.agent.CodeReviewAgentSupport
import ai.codereview.client.runCodeReviewClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.nio.channels.Channels
import java.nio.channels.Pipe
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * In-process code-review app: agent and client run in the same JVM, connected by
 * a pair of NIO pipes. Useful for development without spawning a subprocess.
 *
 * Usage:
 *   ./gradlew runCodeReviewPipeApp                  # reviews against develop
 *   ./gradlew runCodeReviewPipeApp --args="main"    # reviews against main
 */
suspend fun main(args: Array<String>): Unit = coroutineScope {
    logger.info { "Starting Code Review pipe app" }
    val baseRef = args.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "develop"

    val clientToAgent = Pipe.open()
    val agentToClient = Pipe.open()

    val clientTransport = StdioTransport(
        this, Dispatchers.IO,
        input = Channels.newInputStream(agentToClient.source()).asSource().buffered(),
        output = Channels.newOutputStream(clientToAgent.sink()).asSink().buffered(),
        "client",
    )
    val agentTransport = StdioTransport(
        this, Dispatchers.IO,
        input = Channels.newInputStream(clientToAgent.source()).asSource().buffered(),
        output = Channels.newOutputStream(agentToClient.sink()).asSink().buffered(),
        "agent",
    )

    val openAIApiKey = System.getenv("OPENAI_API_KEY")
    val anthropicApiKey = System.getenv("ANTHROPIC_API_KEY")
    require(openAIApiKey != null || anthropicApiKey != null) {
        "At least one of OPENAI_API_KEY or ANTHROPIC_API_KEY environment variables must be set"
    }
    val clients = buildList {
        if (openAIApiKey != null) add(OpenAILLMClient(openAIApiKey))
        if (anthropicApiKey != null) add(AnthropicLLMClient(anthropicApiKey))
    }
    val promptExecutor = RoutingLLMPromptExecutor(clients)

    try {
        val agentProtocol = Protocol(this, agentTransport)
        Agent(
            agentProtocol,
            CodeReviewAgentSupport(
                protocol = agentProtocol,
                promptExecutor = promptExecutor,
            ),
        )
        agentProtocol.start()

        runCodeReviewClient(clientTransport, baseRef)
    } finally {
        clientTransport.close()
        agentTransport.close()
        promptExecutor.close()
    }
    exitProcess(0)
}
