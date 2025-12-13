package ai.koog.agents.features.acp

import ai.koog.agents.core.agent.GraphAIAgent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.protocol.Protocol
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Support class for the ACP agent, providing initialization and session management.
 *
 * @param protocol The protocol instance to use for sending requests and notifications to ACP Client.
 * @param agent The agent instance to use for executing agent actions.
 * @param agentInfo The agent information to return to the client.
 */
public class AcpAIAgentSupport(
    private val protocol: Protocol,
    private val agent: GraphAIAgent<Unit, Unit>,
    private val agentInfo: AgentInfo
) : AgentSupport {

    private val acpAgentsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // TODO: How to clean sessions from here? Implement using callback
    private val acpAgentSessions = atomic(persistentMapOf<SessionId, AcpAIAgentSession>())

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        return agentInfo
    }

    override suspend fun createSession(sessionParameters: SessionParameters): AgentSession {
        val sessionId = SessionId("session")
        return AcpAIAgentSession(sessionId, protocol, acpAgentsScope, agent).also { session ->
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
