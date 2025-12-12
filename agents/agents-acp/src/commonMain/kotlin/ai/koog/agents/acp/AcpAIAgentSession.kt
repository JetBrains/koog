package ai.koog.agents.acp

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.AcpAgent
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.protocol.Protocol
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Represents a session for managing the lifecycle and interaction with an AI agent that uses the ACP protocol.
 *
 * @property sessionId Unique identifier for the session.
 * @property acpProtocol Instance of the protocol used for communication and interaction with the ACP agent.
 * @property acpAgentsScope Coroutine scope in which the agent's jobs run.
 * @property agent The base AI agent used to create and execute prompts in the session.
 */
public class AcpAIAgentSession(
    override val sessionId: SessionId,
    private val acpProtocol: Protocol,
    private val acpAgentsScope: CoroutineScope,
    private val agent: GraphAIAgent<Unit, Unit>
) : AgentSession {
    private lateinit var acpAgent: GraphAIAgent<Unit, Unit>
    private lateinit var acpAgentJob: Job
    private val acpEventsFlow = MutableSharedFlow<Event>()

    private val logger = logger {}

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?
    ): Flow<Event> = flow {
        if (::acpAgent.isInitialized) {
            // TODO: Support appending prompts to the agent
            logger.error { "Agent is already initialized, does not support appending prompts." }
            throw IllegalStateException("Acp agent in session ${sessionId.value} is already initialized")
        }
        acpAgent = GraphAIAgent(
            inputType = agent.inputType,
            outputType = agent.outputType,
            promptExecutor = agent.promptExecutor,
            agentConfig = AIAgentConfig(
                prompt = concatenatePrompts(agent.agentConfig.prompt, content),
                model = agent.agentConfig.model,
                maxAgentIterations = agent.agentConfig.maxAgentIterations
            ),
            strategy = agent.strategy,
            toolRegistry = agent.toolRegistry,
            installFeatures = {
                @OptIn(InternalAgentsApi::class)
                agent.installFeatures(this)
                // Install Acp feature with flow to emit events
                install(AcpAgent) {
                    sessionIdValue = sessionId.value
                    eventsFlow = this@flow
                    protocol = acpProtocol
                }
            }
        )

        acpAgentJob = acpAgentsScope.launch {
            logger.info { "Starting ACP agent" }
            acpAgent.run(Unit)
        }
    }

    override suspend fun cancel() {
        logger.info { "Canceling ACP agent" }
        acpAgentJob.cancelAndJoin()
    }

    private fun concatenatePrompts(initialPrompt: Prompt, content: List<ContentBlock>): Prompt {
        return initialPrompt.withMessages { messages ->
            messages + listOf(
                Message.User(
                    parts = content.mapNotNull {
                        when (it) {
                            is ContentBlock.Text -> ContentPart.Text(it.text)
                            else -> null // TODO: implement
                        }
                    },
                    metaInfo = RequestMetaInfo(agent.clock.now())
                )
            )
        }
    }
}
