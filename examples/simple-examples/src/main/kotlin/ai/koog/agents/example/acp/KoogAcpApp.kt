package ai.koog.agents.example.acp

import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.nio.channels.Channels
import java.nio.channels.Pipe

suspend fun main() = coroutineScope {
    val token = ApiKeyService.openAIApiKey

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
        agentTransport.close()
        clientTransport.close()
        promptExecutor.close()
    }
}
