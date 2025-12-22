package ai.koog.integration.tests.acp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.acp.AcpAgent
import ai.koog.agents.features.acp.toKoogMessage
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.client.ClientSupport
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.AgentCapabilities
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
import com.agentclientprotocol.transport.StdioTransport
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AcpServerTest {

    companion object {
        @JvmStatic
        fun getModels() = listOf(
            OpenAIModels.Chat.GPT5_2,
            AnthropicModels.Haiku_4_5,
            GoogleModels.Gemini2_5Pro,
        )
    }

    @ParameterizedTest
    @MethodSource("getModels")
    fun integration_testAcpServerWithStdioTransport(model: LLModel) = runTest(timeout = 1.minutes) {
        val promptExecutor = SingleLLMPromptExecutor(getLLMClientForProvider(model.provider))

        promptExecutor.use { promptExecutor ->
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(30.seconds) {
                    val result = runAcpAgent(promptExecutor, model)
                    result.shouldNotBeBlank()
                    result.shouldContain("random")
                }
            }
        }
    }

    private suspend fun runAcpAgent(
        promptExecutor: PromptExecutor,
        model: LLModel
    ): String = coroutineScope {
        val randomNumberTool = RandomNumberTool()

        val clientToAgent = Pipe.open()
        val agentToClient = Pipe.open()

        val clientTransport = StdioTransport(
            this,
            Dispatchers.IO,
            input = Channels.newInputStream(agentToClient.source()).asSource().buffered(),
            output = Channels.newOutputStream(clientToAgent.sink()).asSink().buffered(),
            "client"
        )

        val agentTransport = StdioTransport(
            this,
            Dispatchers.IO,
            input = Channels.newInputStream(clientToAgent.source()).asSource().buffered(),
            output = Channels.newOutputStream(agentToClient.sink()).asSink().buffered(),
            "agent"
        )

        val protocolScope = CoroutineScope(this.coroutineContext + Job())

        try {
            val agentProtocol = Protocol(protocolScope, agentTransport)

            Agent(
                agentProtocol,
                TestKoogAgentSupport(
                    promptExecutor = promptExecutor,
                    protocol = agentProtocol,
                    clock = Clock.System,
                    randomNumberTool = randomNumberTool,
                    model = model
                )
            )

            agentProtocol.start()

            val clientProtocol = Protocol(protocolScope, clientTransport)
            val client = Client(clientProtocol, TestClientSupport())
            clientProtocol.start()

            val sessionParameters = SessionParameters(
                Paths.get("").absolutePathString(),
                emptyList()
            )
            val session = client.newSession(sessionParameters)

            val promptContent = listOf(
                ContentBlock.Text("Provide a random number using the ${randomNumberTool.name} tool. YOU MUST USE TOOLS!")
            )

            session.prompt(promptContent).collect { }

            "Tool ${randomNumberTool.name} generated: ${randomNumberTool.last}"
        } finally {
            agentTransport.close()
            clientTransport.close()
            clientToAgent.sink().close()
            clientToAgent.source().close()
            agentToClient.sink().close()
            agentToClient.source().close()
            protocolScope.cancel()
        }
    }

    private class TestKoogAgentSession(
        override val sessionId: SessionId,
        private val promptExecutor: PromptExecutor,
        private val protocol: Protocol,
        private val clock: Clock,
        private val randomNumberTool: RandomNumberTool,
        private val model: LLModel,
    ) : AgentSession {

        private var agentJob: Deferred<Unit>? = null
        private val agentMutex = Mutex()

        override suspend fun prompt(
            content: List<ContentBlock>,
            @Suppress("LocalVariableName") _meta: JsonElement?
        ): Flow<Event> = channelFlow {
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

    private class TestKoogAgentSupport(
        private val promptExecutor: PromptExecutor,
        private val clock: Clock,
        private val protocol: Protocol,
        private val randomNumberTool: RandomNumberTool,
        private val model: LLModel,
    ) : AgentSupport {

        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
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
                authMethods = emptyList()
            )
        }

        @OptIn(ExperimentalUuidApi::class)
        override suspend fun createSession(sessionParameters: SessionParameters): AgentSession {
            val sessionId = SessionId(Uuid.random().toString())

            return TestKoogAgentSession(
                sessionId = sessionId,
                promptExecutor = promptExecutor,
                protocol = protocol,
                clock = clock,
                randomNumberTool = randomNumberTool,
                model = model,
            )
        }

        override suspend fun loadSession(
            sessionId: SessionId,
            sessionParameters: SessionParameters,
        ): AgentSession {
            throw UnsupportedOperationException("Loading sessions is not supported in the test yet.")
        }
    }

    private class TestClientSupport : ClientSupport {
        override suspend fun createClientSession(
            session: ClientSession,
            _sessionResponseMeta: JsonElement?
        ): ClientSessionOperations {
            return TestClientSessionOperations()
        }
    }

    private class TestClientSessionOperations : ClientSessionOperations {
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
        }
    }
}
