package ai.codereview.agent

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.prompt.executor.model.PromptExecutor
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.PromptCapabilities
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CodeReviewAgentSession(
    override val sessionId: SessionId,
    private val promptExecutor: PromptExecutor,
    private val protocol: Protocol,
    private val workingDir: File,
) : AgentSession {

    private var agentJob: Job? = null

    override suspend fun prompt(
        content: List<ContentBlock>,
        @Suppress("LocalVariableName") _meta: JsonElement?,
    ): Flow<Event> = channelFlow {
        val gitUtils = GitUtils(workingDir)

        val agent = createCodeReviewAgent(
            promptExecutor = promptExecutor,
            protocol = protocol,
            sessionId = sessionId.value,
            eventsProducer = this@channelFlow,
            gitUtils = gitUtils,
        )

        // Store the channelFlow's own Job so cancel() can cancel the entire flow
        agentJob = coroutineContext[Job]

        try {
            agent.run(content)
        } catch (e: AIAgentMaxNumberOfIterationsReachedException) {
            send(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentMessageChunk(
                        ContentBlock.Text(
                            "\n[Review aborted — agent exceeded its iteration budget. ${e.message}]\n"
                        )
                    )
                )
            )
        }
    }

    override suspend fun cancel() {
        agentJob?.cancelAndJoin()
    }
}

class CodeReviewAgentSupport(
    private val promptExecutor: PromptExecutor,
    private val protocol: Protocol,
) : AgentSupport {

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo = AgentInfo(
        protocolVersion = LATEST_PROTOCOL_VERSION,
        capabilities = AgentCapabilities(
            loadSession = false,
            promptCapabilities = PromptCapabilities(
                audio = false,
                image = false,
                embeddedContext = false,
            )
        ),
        authMethods = emptyList(),
    )

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession =
        CodeReviewAgentSession(
            sessionId = SessionId(Uuid.random().toString()),
            promptExecutor = promptExecutor,
            protocol = protocol,
            workingDir = File(sessionParameters.cwd),
        )

    override suspend fun loadSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession = throw UnsupportedOperationException("Session loading not supported")
}
