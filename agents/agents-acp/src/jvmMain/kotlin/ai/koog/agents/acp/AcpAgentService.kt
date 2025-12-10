package ai.koog.agents.acp

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.AcpAgent
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionId
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

internal class AcpAIAgentSession(
    override val sessionId: SessionId,
    private val scope: CoroutineScope,
    private val agent: GraphAIAgent<Unit, Unit>
) : AgentSession {

    private lateinit var acpAgent: GraphAIAgent<Unit, Unit>
    private lateinit var acpAgentJob: Job
    private val acpEventsFlow = MutableSharedFlow<Event>()

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?
    ): Flow<Event> {
        if (::acpAgent.isInitialized) {
            // TODO: Support appending prompts to the agent
            throw IllegalStateException("Agent is already initialized")
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
                agent.installFeatures
                // Install Acp feature with flow to emit events
                install(AcpAgent) {
                    eventsFlow = acpEventsFlow
                }
            }
        )

        acpAgentJob = scope.launch { agent.run(Unit) }

        return acpEventsFlow
    }

    override suspend fun cancel() {
        // TODO: cancel the agent
        acpAgentJob.cancel()
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

internal class AcpAIAgentSupport(
    private val agent: GraphAIAgent<Unit, Unit>,
    private val agentInfo: AgentInfo
) : AgentSupport {

    private val acpScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val acpAgentSessions = atomic(persistentMapOf<SessionId, AcpAIAgentSession>())

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        return agentInfo
    }

    override suspend fun createSession(sessionParameters: SessionParameters): AgentSession {
        val sessionId = SessionId("session")
        return AcpAIAgentSession(sessionId, acpScope, agent).also { session ->
            acpAgentSessions.update { it.put(session.sessionId, session) }
        }
    }

    override suspend fun loadSession(
        sessionId: SessionId,
        sessionParameters: SessionParameters,
    ): AgentSession {
        // TODO: Add better error handling
        return acpAgentSessions.value[sessionId] ?: error("Session not found")
    }
}
