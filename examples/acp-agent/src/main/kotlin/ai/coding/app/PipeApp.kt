package ai.coding.app

import ai.coding.agent.KoogAgentSupport
import ai.coding.client.runTerminalClient
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.nio.channels.Channels
import java.nio.channels.Pipe

private val logger = KotlinLogging.logger {}

suspend fun main() = coroutineScope {
    logger.info { "Starting ACP App" }

    val token = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY env variable is not set")

    val clientToAgent = Pipe.open()
    val agentToClient = Pipe.open()

    val clientTransport = StdioTransport(
        this, Dispatchers.IO,
        input = Channels.newInputStream(agentToClient.source()).asSource().buffered(),
        output = Channels.newOutputStream(clientToAgent.sink()).asSink().buffered(),
        "client"
    )

    val agentTransport = StdioTransport(
        this, Dispatchers.IO,
        input = Channels.newInputStream(clientToAgent.source()).asSource().buffered(),
        output = Channels.newOutputStream(agentToClient.sink()).asSink().buffered(),
        "agent"
    )

    val promptExecutor = simpleOpenAIExecutor(token)

    try {
        val agentProtocol = Protocol(this, agentTransport)

        Agent(
            agentProtocol,
            KoogAgentSupport(
                protocol = agentProtocol,
                promptExecutor = promptExecutor,
                clock = Clock.System,
            )
        )

        agentProtocol.start()
        runTerminalClient(clientTransport)

    } finally {
        clientTransport.close()
        agentTransport.close()
        promptExecutor.close()
    }
}