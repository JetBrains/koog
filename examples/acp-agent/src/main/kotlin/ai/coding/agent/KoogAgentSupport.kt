package ai.coding.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.acp.AcpAgent
import ai.koog.agents.features.acp.toKoogMessage
import ai.koog.agents.features.acp.withAcpAgent
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a session for managing the lifecycle and interaction with a Koog AI agent that uses the ACP protocol.
 *
 * @property sessionId Unique identifier for the session.
 * @param promptExecutor Instance of the prompt executor used to execute prompts in the session.
 * @param protocol Instance of the protocol used for communication and interaction with the ACP agent.
 * @param clock Clock instance used for generating timestamps for messages sent to the agent.
 */
class KoogAgentSession(
    override val sessionId: SessionId,
    private val promptExecutor: PromptExecutor,
    private val protocol: Protocol,
    private val clock: Clock,
) : AgentSession {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private var agentJob: Deferred<Unit>? = null
    private val agentMutex = Mutex()

    override suspend fun prompt(
        content: List<ContentBlock>,
        @Suppress("LocalVariableName")
        _meta: JsonElement?
    ): Flow<Event> = channelFlow {
        val agentConfig = AIAgentConfig(
            prompt = prompt("acp") {
                system("You are coding agent.")
            }.appendPrompt(content),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 1000
        )

        val toolRegistry = ToolRegistry {
            tool(::listDirectory.asTool())
            tool(::readFile.asTool())
            tool(::editFile.asTool())
        }

        val strategy = strategy<Unit, Unit>("acp-agent") {
            val executePlan by subgraphWithTask<Unit, Unit> {
                "Execute the task."
            }
            nodeStart then executePlan then nodeFinish
        }

        val agent = AIAgent<Unit, Unit>(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
        ) {
            install(AcpAgent) {
                this.sessionId = this@KoogAgentSession.sessionId.value
                this.protocol = this@KoogAgentSession.protocol
                this.eventsProducer = this@channelFlow
                this.setDefaultNotifications = true
            }
        }

        agentMutex.withLock {
            agentJob = async { agent.run(Unit) }
            agentJob?.await()
        }
    }

    override suspend fun cancel() {
        logger.info { "Canceling ACP agent" }
        agentJob?.cancelAndJoin()
    }

    private fun simpleStrategy() = strategy<Unit, Unit>("acp-agent") {
        val executePlan by subgraphWithTask<Unit, Unit> {
            "Execute the task."
        }
        nodeStart then executePlan then nodeFinish
    }

    private fun customStrategy() = strategy<Unit, Unit>("acp-agent") {
        val nodePlanPrompt by node<Unit, String>("plan") {
            "You have a task! Create a plan for it."
        }
        val nodeCreatePlan by nodeLLMRequestStructured<KoogPlan>()

        val nodeSendPlan by node<KoogPlan, Unit> { plan ->
            withAcpAgent {
                sendEvent(
                    Event.SessionUpdateEvent(
                        SessionUpdate.PlanUpdate(plan.toAcpPlan().entries)
                    )
                )
            }
        }

        val executePlan by subgraphWithTask<Unit, Unit> {
            "Execute the plan using provided tools."
        }

        edge(nodeStart forwardTo nodePlanPrompt)
        edge(nodePlanPrompt forwardTo nodeCreatePlan)
        edge(nodeCreatePlan forwardTo nodeSendPlan onCondition { it.isSuccess } transformed { it.getOrThrow().data })
        edge(nodeSendPlan forwardTo executePlan)
        edge(executePlan forwardTo nodeFinish)
    }

    private fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
        return withMessages { messages ->
            messages + content.toKoogMessage(clock)
        }
    }
}

/**
 * Support class for the Koog AI agent, providing initialization and session management.
 *
 * @param promptExecutor The prompt executor instance to use for executing prompts.
 * @param clock Clock instance to use for generating timestamps for messages sent to the agent.
 * @param protocol The protocol instance to use for sending requests and notifications to ACP Client.
 */
class KoogAgentSupport(
    private val promptExecutor: PromptExecutor,
    private val clock: Clock,
    private val protocol: Protocol,
) : AgentSupport {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        logger.info { "Initializing ACP agent for client with capabilities: ${clientInfo.capabilities}" }

        return AgentInfo(
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
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        val sessionId = SessionId(Uuid.random().toString())
        logger.info { "Creating new session with ID: $sessionId" }

        return KoogAgentSession(
            sessionId = sessionId,
            promptExecutor = promptExecutor,
            protocol = protocol,
            clock = clock,
        )
    }

    override suspend fun loadSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession {
        logger.info { "Loading session with ID: $sessionId" }

        return KoogAgentSession(
            sessionId = sessionId,
            promptExecutor = promptExecutor,
            protocol = protocol,
            clock = clock,
        )
    }
}
