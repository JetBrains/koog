package ai.koog.agents.example.acp

import ai.koog.agents.acp.AcpAIAgentSupport
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.PromptCapabilities
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.nio.channels.Channels
import java.nio.channels.Pipe
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

@Tool
private fun listFiles(directory: String): List<String> {
    return Path(directory).listDirectoryEntries().map { it.toString() }
}

suspend fun app() = coroutineScope {
    val token = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable is not set")

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

    val agentProtocol = Protocol(this, agentTransport)

    val koogAgent = AIAgent<Unit, Unit>(
        promptExecutor = simpleOpenAIExecutor(token),
        agentConfig = AIAgentConfig(
            prompt = prompt("acp") { system("You are agent.") },
            model = OpenAIModels.Chat.GPT4oMini,
            maxAgentIterations = 1000
        ),
        strategy = strategy<Unit, Unit>("acp-agent") {
            edge(nodeStart forwardTo nodeFinish)
        },
        toolRegistry = ToolRegistry {

        }
    ) {
        handleEvents {
            onToolCallStarting { eventContext ->
                println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            }

            onAgentExecutionFailed { eventContext ->
                println(
                    "An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}"
                )
            }

            onAgentCompleted { eventContext ->
                println("Result: ${eventContext.result}")
            }
        }
    }

    val acpAgentInfo = AgentInfo(
        protocolVersion = LATEST_PROTOCOL_VERSION,
        capabilities = AgentCapabilities(
            loadSession = false,
            promptCapabilities = PromptCapabilities(
                audio = false,
                image = false,
                embeddedContext = true
            )
        ),
        authMethods = emptyList() // No authentication required
    )

    Agent(
        agentProtocol,
        AcpAIAgentSupport(
            protocol = agentProtocol,
            agent = koogAgent,
            agentInfo = acpAgentInfo
        )
    )

    agentProtocol.start()

    runTerminalClient(clientTransport)
}

fun main() {
    runBlocking {
        app()
    }
}
