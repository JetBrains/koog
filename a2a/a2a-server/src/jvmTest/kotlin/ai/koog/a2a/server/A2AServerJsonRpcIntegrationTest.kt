package ai.koog.a2a.server

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendConfiguration
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.test.BaseA2AProtocolTest
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.kotest.inspectors.shouldForAll
import io.kotest.inspectors.shouldForAtLeastOne
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.net.ServerSocket
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Integration test class for testing the JSON-RPC HTTP communication in the A2A server context.
 * This class ensures the proper functioning and correctness of the A2A protocol over HTTP
 * using the JSON-RPC standard.
 */
@OptIn(ExperimentalUuidApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD, reason = "Working with the same instance of test server.")
class A2AServerJsonRpcIntegrationTest : BaseA2AProtocolTest() {
    override val testTimeout = 2.minutes

    private var testPort: Int? = null
    private val testPath = "/a2a"
    private lateinit var serverUrl: String

    private lateinit var serverTransport: HttpJSONRPCServerTransport
    private lateinit var clientTransport: HttpJSONRPCClientTransport
    private lateinit var httpClient: HttpClient

    override lateinit var client: A2AClient

    @BeforeAll
    fun setup(): Unit = runBlocking {
        // Discover and take any free port
        testPort = ServerSocket(0).use { it.localPort }
        serverUrl = "http://localhost:$testPort$testPath"

        // Create agent cards
        val agentCard = createAgentCard()
        val agentCardExtended = createExtendedAgentCard()

        // Create test agent executor
        val testAgentExecutor = TestAgentExecutor()

        // Create A2A server
        val a2aServer = A2AServer(
            agentExecutor = testAgentExecutor,
            agentCard = agentCard,
            agentCardExtended = agentCardExtended,
            pushConfigStorage = InMemoryPushNotificationConfigStorage()
        )

        // Create server transport
        serverTransport = HttpJSONRPCServerTransport(a2aServer)

        // Start server
        serverTransport.start(
            engineFactory = Netty,
            port = testPort!!,
            path = testPath,
            wait = false,
            agentCard = agentCard,
            agentCardPath = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
        )

        // Create client transport
        httpClient = HttpClient(CIO) {
            install(Logging) {
                level = LogLevel.ALL
            }

            install(HttpTimeout) {
                requestTimeoutMillis = testTimeout.inWholeMilliseconds
            }
        }

        clientTransport = HttpJSONRPCClientTransport(serverUrl, httpClient)

        client = A2AClient(
            transport = clientTransport,
            agentCardResolver = UrlAgentCardResolver(
                baseUrl = serverUrl,
                path = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
                baseHttpClient = httpClient,
            )
        )
    }

    @BeforeTest
    fun initClient(): Unit = runBlocking {
        client.connect()
    }

    @AfterAll
    fun tearDown(): Unit = runBlocking {
        clientTransport.close()
        serverTransport.stop()
    }

    private fun createAgentCard(): AgentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "Hello World Agent",
        description = "Just a hello world agent",
        url = "http://localhost:9999/",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = null,
        iconUrl = null,
        provider = null,
        version = "1.0.0",
        documentationUrl = null,
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = null,
            extensions = null
        ),
        securitySchemes = null,
        security = null,
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "hello_world",
                name = "Returns hello world",
                description = "just returns hello world",
                tags = listOf("hello world"),
                examples = listOf("hi", "hello world"),
                inputModes = null,
                outputModes = null,
                security = null
            )
        ),
        supportsAuthenticatedExtendedCard = true,
        signatures = null
    )

    private fun createExtendedAgentCard(): AgentCard = AgentCard(
        protocolVersion = "0.3.0",
        name = "Hello World Agent - Extended Edition",
        description = "The full-featured hello world agent for authenticated users.",
        url = "http://localhost:9999/",
        preferredTransport = TransportProtocol.JSONRPC,
        additionalInterfaces = null,
        iconUrl = null,
        provider = null,
        version = "1.0.1",
        documentationUrl = null,
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = null,
            extensions = null
        ),
        securitySchemes = null,
        security = null,
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "hello_world",
                name = "Returns hello world",
                description = "just returns hello world",
                tags = listOf("hello world"),
                examples = listOf("hi", "hello world"),
                inputModes = null,
                outputModes = null,
                security = null
            ),
            AgentSkill(
                id = "super_hello_world",
                name = "Returns a SUPER Hello World",
                description = "A more enthusiastic greeting, only for authenticated users.",
                tags = listOf("hello world", "super", "extended"),
                examples = listOf("super hi", "give me a super hello"),
                inputModes = null,
                outputModes = null,
                security = null
            )
        ),
        supportsAuthenticatedExtendedCard = true,
        signatures = null
    )

    /**
     * Extended test that wouldn't work with Python A2A SDK server, because their implementation has some problems.
     * It doesn't send events emitted in the `cancel` method in AgentExecutor to the subscribers of message/stream or tasks/resubscribe.
     * But our server implementation should handle it properly.
     */
    @Test
    fun `test cancel task cancellation events received`() = runTest(timeout = testTimeout) {
        // Need real time for this test
        withContext(Dispatchers.Default) {
            val createTaskRequest = Request(
                data = MessageSendParams(
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.User,
                        parts = listOf(
                            TextPart("do long-running task"),
                        ),
                        contextId = "test-context",
                    ),
                ),
            )

            val taskId = (client.sendMessage(createTaskRequest).data as Task).id

            joinAll(
                launch {
                    val resubscribeTaskRequest = Request(
                        data = TaskIdParams(
                            id = taskId,
                        )
                    )

                    val events = client
                        .resubscribeTask(resubscribeTaskRequest)
                        .toList()
                        .map { it.data }

                    // All the same task and context
                    events.shouldForAll {
                        it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
                            it.taskId shouldBe taskId
                            it.contextId shouldBe "test-context"
                        }
                    }

                    // Has events from `execute` - task is working
                    events.shouldForAtLeastOne {
                        it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
                            it.status.state shouldBe TaskState.Working
                            it.status.message shouldNotBeNull {
                                role shouldBe Role.Agent

                                parts.shouldForAll {
                                    it.shouldBeInstanceOf<TextPart> {
                                        it.text shouldStartWith "Still working"
                                    }
                                }
                            }
                        }
                    }

                    // Has events from `cancel` - task is canceled
                    events.shouldForAtLeastOne {
                        it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
                            it.status.state shouldBe TaskState.Canceled
                            it.status.message shouldNotBeNull {
                                role shouldBe Role.Agent
                                parts shouldBe listOf(TextPart("Task canceled"))
                            }
                        }
                    }
                },
                launch {
                    // Let the task run for a while
                    delay(400)

                    val cancelTaskRequest = Request(
                        data = TaskIdParams(
                            id = taskId,
                        )
                    )

                    val response = client.cancelTask(cancelTaskRequest)
                    response.data should {
                        it.id shouldBe taskId
                        it.contextId shouldBe "test-context"
                        it.status should {
                            it.state shouldBe TaskState.Canceled
                            it.message shouldNotBeNull {
                                role shouldBe Role.Agent
                                parts shouldBe listOf(TextPart("Task canceled"))
                            }
                        }
                    }
                }
            )
        }
    }

    /**
     * Another test that doesn't work with Python A2A SDK server because of its implementation problems.
     * It's taken from TCK. Follow-up messages to the running task should be supported.
     * In case the task is still running, request should wait for a chance to be processed when the task is done.
     */
    @Test
    fun `test task send follow-up message`() = runTest(timeout = testTimeout) {
        fun createRequest(
            taskId: String?,
            blocking: Boolean,
        ) = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("do long-running task"),
                    ),
                    taskId = taskId,
                    contextId = "test-context"
                ),
                configuration = MessageSendConfiguration(
                    blocking = blocking
                )
            )
        )

        // Create a long-running task and return without waiting
        val initialRequest = createRequest(taskId = null, blocking = false)
        val initialResponse = client.sendMessage(initialRequest)

        val taskId = initialResponse.data.shouldBeInstanceOf<Task>().taskId

        // Immediately send a follow-up message to the same task and wait for the response
        val followupRequest = createRequest(taskId = taskId, blocking = true)
        val followupResponse = client.sendMessage(followupRequest)

        followupResponse.data.shouldBeInstanceOf<Task> {
            it.taskId shouldBe taskId
            it.contextId shouldBe "test-context"

            it.status should {
                it.state shouldBe TaskState.Working
                it.message shouldNotBeNull {
                    role shouldBe Role.Agent

                    parts.shouldForAll {
                        it.shouldBeInstanceOf<TextPart> {
                            it.text shouldStartWith "Still working"
                        }
                    }
                }
            }
        }
    }
}
