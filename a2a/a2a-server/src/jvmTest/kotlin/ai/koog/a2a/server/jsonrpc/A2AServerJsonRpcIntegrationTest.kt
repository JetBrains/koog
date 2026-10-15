package ai.koog.a2a.server.jsonrpc

import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.shouldForAll
import io.kotest.inspectors.shouldForAtLeastOne
import io.kotest.matchers.collections.shouldHaveSize
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

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    override fun `test get extended agent card`() =
        super.`test get extended agent card`()

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
    override fun `test list tasks`() =
        super.`test list tasks`()

    @Test
    override fun `test cancel task`() =
        super.`test cancel task`()

    @Test
    override fun `test subscribe to task`() =
        super.`test subscribe to task`()

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
            val createTaskRequest = SendMessageRequest(
                message = Message(
                    messageId = Uuid.Companion.random().toString(),
                    role = Role.ROLE_USER,
                    parts = listOf(
                        TextPart("do long-running task"),
                    ),
                    contextId = "test-context",
                ),
                configuration = SendMessageConfiguration(
                    returnImmediately = true,
                ),
            )

            val taskId = (client.sendMessage(createTaskRequest) as Task).id

            joinAll(
                launch {
                    val subscribeToTaskRequest = SubscribeToTaskRequest(
                        id = taskId,
                    )

                    val events = client
                        .subscribeToTask(subscribeToTaskRequest)
                        .toList()

                    // The first event is the snapshot of the task
                    events.first().shouldBeInstanceOf<Task> {
                        it.id shouldBe taskId
                        it.contextId shouldBe "test-context"
                    }

                    // Then updates of the same task and context
                    events.drop(1).shouldForAll {
                        it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
                            it.taskId shouldBe taskId
                            it.contextId shouldBe "test-context"
                        }
                    }

                    // Has events from `execute` - task is working
                    events.shouldForAtLeastOne {
                        it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
                            it.status.state shouldBe TaskState.TASK_STATE_WORKING
                            it.status.message shouldNotBeNull {
                                role shouldBe Role.ROLE_AGENT

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
                            it.status.state shouldBe TaskState.TASK_STATE_CANCELED
                            it.status.message shouldNotBeNull {
                                role shouldBe Role.ROLE_AGENT
                                parts shouldBe listOf(TextPart("Task canceled"))
                            }
                        }
                    }
                },
                launch {
                    // Let the task run for a while
                    delay(400)

                    val cancelTaskRequest = CancelTaskRequest(
                        id = taskId,
                    )

                    val response = client.cancelTask(cancelTaskRequest)
                    response should {
                        it.id shouldBe taskId
                        it.contextId shouldBe "test-context"
                        it.status should {
                            it.state shouldBe TaskState.TASK_STATE_CANCELED
                            it.message shouldNotBeNull {
                                role shouldBe Role.ROLE_AGENT
                                parts shouldBe listOf(TextPart("Task canceled"))
                            }
                        }
                    }
                }
            )
        }
    }

    /**
     * Follow-up messages to a running task wait until the running turn ends. Here that turn completes the task, so
     * the follow-up is rejected: a terminal task can't accept messages.
     */
    @Test
    fun `test task send follow-up message to running task`() = runTest(timeout = testTimeout) {
        fun createRequest(
            taskId: String?,
            returnImmediately: Boolean,
        ) = SendMessageRequest(
            message = Message(
                messageId = Uuid.Companion.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do long-running task"),
                ),
                taskId = taskId,
                contextId = "test-context"
            ),
            configuration = SendMessageConfiguration(
                returnImmediately = returnImmediately
            )
        )

        // Create a long-running task and return without waiting
        val initialRequest = createRequest(taskId = null, returnImmediately = true)
        val initialResponse = client.sendMessage(initialRequest)

        val taskId = initialResponse.shouldBeInstanceOf<Task>().id

        // Immediately send a follow-up message to the same task: it waits for the running turn, which completes the task
        val followupRequest = createRequest(taskId = taskId, returnImmediately = false)

        shouldThrow<A2AUnsupportedOperationException> {
            client.sendMessage(followupRequest)
        }

        client.getTask(GetTaskRequest(id = taskId)).status.state shouldBe TaskState.TASK_STATE_COMPLETED
    }

    /**
     * A follow-up message to a task waiting for input continues the task in the task's context. The stream of the
     * follow-up begins with the task, then carries the agent's updates.
     */
    @Test
    fun `test task send follow-up message to input-required task`() = runTest(timeout = testTimeout) {
        val initialRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.Companion.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(TextPart("do input-required task")),
                contextId = "test-context"
            ),
        )

        // Blocking: returns once the task waits for input
        val task = client.sendMessage(initialRequest).shouldBeInstanceOf<Task> {
            it.contextId shouldBe "test-context"
            it.status.state shouldBe TaskState.TASK_STATE_INPUT_REQUIRED
            it.history shouldBe listOf(initialRequest.message)
        }

        // The follow-up only references the task; its context is inferred
        val followupRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.Companion.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(TextPart("provide input")),
                taskId = task.id,
            ),
        )

        val events = client.sendMessageStreaming(followupRequest).toList()

        events shouldHaveSize 2

        // The agents of these tests don't persist user messages: the server does, moving the agent's prompt in between.
        val expectedHistory = listOf(initialRequest.message, task.status.message!!, followupRequest.message)

        events[0].shouldBeInstanceOf<Task> {
            it.id shouldBe task.id
            it.contextId shouldBe "test-context"
            it.status.state shouldBe TaskState.TASK_STATE_INPUT_REQUIRED
            it.status.message shouldBe null
            it.history shouldBe expectedHistory
        }

        events[1].shouldBeInstanceOf<TaskStatusUpdateEvent> {
            it.taskId shouldBe task.id
            it.contextId shouldBe "test-context"
            it.status should {
                it.state shouldBe TaskState.TASK_STATE_COMPLETED
                it.message shouldNotBeNull {
                    role shouldBe Role.ROLE_AGENT
                    parts shouldBe listOf(TextPart("Input received"))
                }
            }
        }

        client.getTask(GetTaskRequest(id = task.id)).history shouldBe expectedHistory
    }
}
