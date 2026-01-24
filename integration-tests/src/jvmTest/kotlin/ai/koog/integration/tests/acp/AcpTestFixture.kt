package ai.koog.integration.tests.acp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.acp.AcpAgent
import ai.koog.agents.features.acp.toKoogMessage
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AcpMethod
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateRequest
import com.agentclientprotocol.model.AuthenticateResponse
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PromptCapabilities
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.sendRequest
import com.agentclientprotocol.transport.StdioTransport
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import java.nio.channels.Channels
import java.nio.channels.Pipe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AcpClientSetup(
    val client: Client,
    val session: ClientSession?,
    val agentSupport: TestKoogAgentSupport,
    val clientOperations: TestClientSessionOperations,
    val agentInfo: AgentInfo,
    val cleanup: () -> Unit
)

/**
 *
 * @param scope CoroutineScope for the test
 * @param promptExecutor PromptExecutor for agent execution
 * @param model LLM model to use
 * @param randomNumberTool Tool for testing tool calls
 * @param agentSupport Optional custom AgentSupport implementation
 * @param loadSession Whether to support session loading
 * @param audio Whether to support audio content
 * @param image Whether to support image content
 * @param authMethods List of authentication methods
 * @param authenticate Whether to authenticate after initialization
 * @param createSession Whether to create a session after initialization
 * @return AcpClientSetup with client, session, and cleanup function
 */
suspend fun setupAcpClient(
    scope: CoroutineScope,
    promptExecutor: PromptExecutor,
    model: LLModel,
    randomNumberTool: RandomNumberTool,
    agentSupport: TestKoogAgentSupport? = null,
    loadSession: Boolean = true,
    audio: Boolean = true,
    image: Boolean = true,
    authMethods: List<AuthMethod> = emptyList(),
    authenticate: Boolean = false,
    createSession: Boolean = true
): AcpClientSetup {
    val clientToAgent = withContext(Dispatchers.IO) { Pipe.open() }
    val agentToClient = withContext(Dispatchers.IO) { Pipe.open() }

    val clientTransport = StdioTransport(
        scope,
        Dispatchers.IO,
        input = Channels.newInputStream(agentToClient.source()).asSource().buffered(),
        output = Channels.newOutputStream(clientToAgent.sink()).asSink().buffered(),
        "client"
    )

    val agentTransport = StdioTransport(
        scope,
        Dispatchers.IO,
        input = Channels.newInputStream(clientToAgent.source()).asSource().buffered(),
        output = Channels.newOutputStream(agentToClient.sink()).asSink().buffered(),
        "agent"
    )

    val protocolScope = CoroutineScope(scope.coroutineContext + Job())

    val agentProtocol = Protocol(protocolScope, agentTransport)
    val support = agentSupport ?: TestKoogAgentSupport(
        promptExecutor = promptExecutor,
        protocol = agentProtocol,
        clock = Clock.System,
        randomNumberTool = randomNumberTool,
        model = model,
        loadSession = loadSession,
        audio = audio,
        image = image,
        authMethods = authMethods
    )

    Agent(agentProtocol, support)
    agentProtocol.start()

    val clientProtocol = Protocol(protocolScope, clientTransport)
    val client = Client(clientProtocol)
    val testClientSessionOperations = TestClientSessionOperations()
    clientProtocol.start()

    val agentInfo = client.initialize(ClientInfo())
    agentInfo.capabilities.loadSession shouldBe loadSession
    agentInfo.capabilities.promptCapabilities.audio shouldBe audio
    agentInfo.capabilities.promptCapabilities.image shouldBe image
    agentInfo.authMethods shouldBe authMethods

    if (authenticate && authMethods.isNotEmpty()) {
        clientProtocol.sendRequest<AuthenticateRequest, AuthenticateResponse>(
            AcpMethod.AgentMethods.Authenticate,
            AuthenticateRequest(authMethods.first().id)
        )
    }

    val session = if (createSession) {
        client.newSession(
            SessionCreationParameters(java.nio.file.Paths.get("").toAbsolutePath().toString(), emptyList())
        ) { _, _ -> testClientSessionOperations }
    } else {
        null
    }

    return AcpClientSetup(client, session, support, testClientSessionOperations, agentInfo) {
        agentTransport.close()
        clientTransport.close()
        clientToAgent.sink().close()
        clientToAgent.source().close()
        agentToClient.sink().close()
        agentToClient.source().close()
        protocolScope.cancel()
    }
}

class TestKoogAgentSession(
    override val sessionId: SessionId,
    private val promptExecutor: PromptExecutor,
    private val protocol: Protocol,
    private val clock: Clock,
    private val randomNumberTool: RandomNumberTool,
    private val model: LLModel,
    private val audioSupported: Boolean = true,
    private val imageSupported: Boolean = true
) : AgentSession {

    private var agentJob: Deferred<Unit>? = null
    private val agentMutex = Mutex()

    override suspend fun prompt(
        content: List<ContentBlock>,
        @Suppress("LocalVariableName") _meta: JsonElement?
    ): Flow<Event> = channelFlow {
        if (!audioSupported && content.any { it is ContentBlock.Audio }) {
            throw IllegalArgumentException("Audio not supported")
        }
        if (!imageSupported && content.any { it is ContentBlock.Image }) {
            throw IllegalArgumentException("Image not supported")
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("acp") {
                system("You are a test agent.")
            }.appendPrompt(content),
            model = model,
            maxAgentIterations = 10
        )

        val toolRegistry = ToolRegistry {
            tool(randomNumberTool)
        }

        agentMutex.withLock {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = singleRunStrategy(),
                toolRegistry = toolRegistry,
            ) {
                install(AcpAgent) {
                    this.sessionId = this@TestKoogAgentSession.sessionId.value
                    this.protocol = this@TestKoogAgentSession.protocol
                    this.eventsProducer = this@channelFlow
                    this.setDefaultNotifications = true
                }
            }

            agentJob = async { agent.run("") }
            agentJob?.await()
        }
    }

    override suspend fun cancel() {
        agentJob?.cancelAndJoin()
    }

    private fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
        return withMessages { messages ->
            messages + content.toKoogMessage(clock)
        }
    }
}

class TestKoogAgentSupport(
    private val promptExecutor: PromptExecutor,
    private val clock: Clock,
    val protocol: Protocol,
    private val randomNumberTool: RandomNumberTool,
    private val model: LLModel,
    private val loadSession: Boolean = true,
    private val audio: Boolean = true,
    private val image: Boolean = true,
    private val authMethods: List<AuthMethod> = emptyList()
) : AgentSupport {
    private val sessions = mutableMapOf<SessionId, AgentSession>()
    private var authenticated = false

    override suspend fun authenticate(methodId: AuthMethodId, _meta: JsonElement?): AuthenticateResponse {
        authenticated = true
        return AuthenticateResponse()
    }

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        return AgentInfo(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = AgentCapabilities(
                loadSession = loadSession,
                promptCapabilities = PromptCapabilities(
                    audio = audio,
                    image = image,
                    embeddedContext = true
                )
            ),
            authMethods = authMethods
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        if (authMethods.isNotEmpty() && !authenticated) {
            throw IllegalStateException("Authentication required")
        }
        val sessionId = SessionId(Uuid.random().toString())

        return TestKoogAgentSession(
            sessionId = sessionId,
            promptExecutor = promptExecutor,
            protocol = protocol,
            clock = clock,
            randomNumberTool = randomNumberTool,
            model = model,
            audioSupported = audio,
            imageSupported = image
        ).also { sessions[sessionId] = it }
    }

    override suspend fun loadSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession {
        if (authMethods.isNotEmpty() && !authenticated) {
            throw IllegalStateException("Authentication required")
        }
        if (!loadSession) {
            throw UnsupportedOperationException("Session loading is not supported")
        }
        return sessions[sessionId] ?: throw IllegalArgumentException("Session $sessionId not found")
    }
}

class TestClientSessionOperations : ClientSessionOperations {
    val notifications = mutableListOf<SessionUpdate>()

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        // Allowing all permissions in tests
        return RequestPermissionResponse(
            RequestPermissionOutcome.Selected(permissions.firstOrNull()?.optionId ?: PermissionOptionId("allow")),
            _meta
        )
    }

    override suspend fun notify(
        notification: SessionUpdate,
        _meta: JsonElement?,
    ) {
        notifications.add(notification)
    }
}
