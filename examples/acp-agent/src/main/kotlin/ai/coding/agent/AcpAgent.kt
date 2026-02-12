package ai.coding.agent

import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

val logger = KotlinLogging.logger {}

suspend fun main() = coroutineScope {
    logger.info { "Starting ACP Agent" }
    val token = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY env variable is not set")

    val agentTransport = StdioTransport(
        this, Dispatchers.IO,
        input = BufferedInputStream(System.`in`).asSource().buffered(),
        output = BufferedOutputStream(System.out).asSink().buffered(),
        name = "agent"
    )

    val promptExecutor = simpleOpenAIExecutor(token)

    try {
        // Launch the entire agent in a job
        val agentJob = launch {
            val agentProtocol = Protocol(this, agentTransport)

            Agent(
                agentProtocol,
                KoogAgentSupport(
                    protocol = agentProtocol,
                    promptExecutor = promptExecutor,
                    clock = Clock.System,
                )
            )

            logger.info { "Agent initialized, starting protocol" }
            agentProtocol.start()
        }

        // Wait for the agent job to complete
        agentJob.join()
        logger.info { "Agent job completed" }

    } finally {
        agentTransport.close()
        promptExecutor.close()
    }
}
