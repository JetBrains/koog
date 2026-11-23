package ai.codereview.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

private val logger = KotlinLogging.logger {}

/**
 * Stdio entry point for the code-review ACP agent.
 *
 * Designed to be launched as a subprocess by an ACP host (IntelliJ, Zed, …)
 * or by [ai.codereview.app] runners. Reads JSON-RPC from
 * `System.in` and writes to `System.out`.
 */
suspend fun main(): Unit = coroutineScope {
    logger.info { "Starting Code Review ACP Agent" }

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

    val transport = StdioTransport(
        this, Dispatchers.IO,
        input = BufferedInputStream(System.`in`).asSource().buffered(),
        output = BufferedOutputStream(System.out).asSink().buffered(),
        name = "agent",
    )

    try {
        val agentJob = launch {
            val protocol = Protocol(this, transport)
            Agent(
                protocol,
                CodeReviewAgentSupport(
                    protocol = protocol,
                    promptExecutor = promptExecutor,
                ),
            )
            logger.info { "Agent initialized, starting protocol" }
            protocol.start()
        }
        agentJob.join()
        logger.info { "Agent job completed" }
    } finally {
        transport.close()
        promptExecutor.close()
    }
}
