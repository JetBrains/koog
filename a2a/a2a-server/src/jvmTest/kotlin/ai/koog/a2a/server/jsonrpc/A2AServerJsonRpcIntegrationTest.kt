package ai.koog.a2a.server.jsonrpc

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendConfiguration
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.Request
import io.kotest.inspectors.shouldForAll
import io.kotest.inspectors.shouldForAtLeastOne
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Integration test class for testing the JSON-RPC HTTP communication in the A2A server context.
 * This class ensures the proper functioning and correctness of the A2A protocol over HTTP
 * using the JSON-RPC standard.
 */
@OptIn(ExperimentalUuidApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class A2AServerJsonRpcIntegrationTest : BaseA2AServerJsonRpcTest() {
    override val testTimeout = 10.seconds

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @BeforeTest
    override fun initClient() {
        super.initClient()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    override fun `test get agent card`() =
        super.`test get agent card`()

    @Test
    override fun `test get authenticated extended agent card`() =
        super.`test get authenticated extended agent card`()

    @Test
    override fun `test send message`() =
        super.`test send message`()

    @Test
    override fun `test send message streaming`() =
        super.`test send message streaming`()

    @Test
    override fun `test get task`() =
        super.`test get task`()

    @Test
    override fun `test cancel task`() =
        super.`test cancel task`()

    @Test
    override fun `test resubscribe task`() =
        super.`test resubscribe task`()

    @Test
    override fun `test push notification configs`() =
        super.`test push notification configs`()

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
                        messageId = Uuid.Companion.random().toString(),
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
                    messageId = Uuid.Companion.random().toString(),
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
