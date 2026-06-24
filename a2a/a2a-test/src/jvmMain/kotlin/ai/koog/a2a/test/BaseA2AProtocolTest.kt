package ai.koog.a2a.test

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.AuthenticationInfo
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.inspectors.shouldForAll
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Abstract base class containing transport-agnostic A2A protocol compliance tests.
 *
 * Concrete test classes should inherit from this class and provide the [client] property
 * to run the same test suite against different A2A implementations.
 *
 * @property client The A2A client instance to test against. Should be connected and ready to use.
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("FunctionName")
abstract class BaseA2AProtocolTest {
    protected abstract val testTimeout: Duration

    /**
     * The A2A client instance to test. Must be connected and ready to use.
     */
    protected abstract var client: A2AClient

    open fun `test get agent card`() = runTest(timeout = testTimeout) {
        val agentCard = client.card

        // Assert on the full AgentCard structure
        val expectedAgentCard = AgentCard(
            name = "Hello World Agent",
            description = "Just a hello world agent",
            supportedInterfaces = listOf(
                AgentInterface(
                    url = "http://localhost:9999/",
                    protocolBinding = TransportProtocol.JSONRPC,
                    protocolVersion = "1.0",
                )
            ),
            iconUrl = null,
            provider = null,
            version = "1.0.0",
            documentationUrl = null,
            capabilities = AgentCapabilities(
                streaming = true,
                pushNotifications = true,
                extensions = null,
                extendedAgentCard = true,
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
            signatures = null
        )

        agentCard shouldBe expectedAgentCard
    }

    open fun `test get extended agent card`() = runTest(timeout = testTimeout) {
        val request = GetExtendedAgentCardRequest()

        val response = client.getExtendedAgentCard(request)

        // Assert on the extended agent card structure
        val expectedExtendedAgentCard = AgentCard(
            name = "Hello World Agent - Extended Edition",
            description = "The full-featured hello world agent for authenticated users.",
            supportedInterfaces = listOf(
                AgentInterface(
                    url = "http://localhost:9999/",
                    protocolBinding = TransportProtocol.JSONRPC,
                    protocolVersion = "1.0",
                )
            ),
            iconUrl = null,
            provider = null,
            version = "1.0.1",
            documentationUrl = null,
            capabilities = AgentCapabilities(
                streaming = true,
                pushNotifications = true,
                extensions = null,
                extendedAgentCard = true,
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
            signatures = null
        )

        response shouldBe expectedExtendedAgentCard
    }

    open fun `test send message`() = runTest(timeout = testTimeout) {
        val request = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("hello world"),
                ),
                contextId = "test-context"
            ),
        )

        val response = client.sendMessage(request)

        response.shouldBeInstanceOf<Message> {
            it.role shouldBe Role.ROLE_AGENT
            it.parts shouldBe listOf(TextPart("Hello World"))
            it.contextId shouldBe "test-context"
        }
    }

    open fun `test send message streaming`() = runTest(timeout = testTimeout) {
        val createTaskRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do task"),
                ),
                contextId = "test-context"
            ),
        )

        val events: List<Event> = client
            .sendMessageStreaming(createTaskRequest)
            .toList()

        events shouldHaveSize 3

        events[0].shouldBeInstanceOf<Task> { task ->
            task.contextId shouldBe "test-context"
            task.status should {
                it.state shouldBe TaskState.TASK_STATE_SUBMITTED
            }

            task.history shouldNotBeNull {
                this shouldHaveSize 1

                this[0] should {
                    it.role shouldBe Role.ROLE_USER
                    it.parts shouldBe listOf(TextPart("do task"))
                }
            }
        }

        events[1].shouldBeInstanceOf<TaskStatusUpdateEvent> {
            it.contextId shouldBe "test-context"

            it.status should {
                it.state shouldBe TaskState.TASK_STATE_WORKING
                it.message shouldNotBeNull {
                    role shouldBe Role.ROLE_AGENT
                    parts shouldBe listOf(TextPart("Working on task"))
                }
            }
        }

        events[2].shouldBeInstanceOf<TaskStatusUpdateEvent> {
            it.contextId shouldBe "test-context"

            it.status should {
                it.state shouldBe TaskState.TASK_STATE_COMPLETED
                it.message shouldNotBeNull {
                    role shouldBe Role.ROLE_AGENT
                    parts shouldBe listOf(TextPart("Task completed"))
                }
            }
        }
    }

    open fun `test get task`() = runTest(timeout = testTimeout) {
        val createTaskRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do task"),
                ),
                contextId = "test-context"
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest) as Task).id

        val getTaskRequest = GetTaskRequest(
            id = taskId,
            historyLength = 1
        )

        val response = client.getTask(getTaskRequest)

        response should { task ->
            task.id shouldBe taskId
            task.contextId shouldBe "test-context"
            task.status should { status ->
                status.state shouldBe TaskState.TASK_STATE_COMPLETED
            }
        }
    }

    open fun `test list tasks`() = runTest(timeout = testTimeout) {
        val createTaskRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do task"),
                ),
                contextId = "test-context"
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest) as Task).id

        val listTasksRequest = ListTasksRequest(
            contextId = "test-context"
        )

        val response = client.listTasks(listTasksRequest)

        response.tasks.shouldNotBeEmpty()

        response.tasks.shouldForAll {
            it.contextId shouldBe "test-context"
        }

        // The task created above should be present in the listing
        response.tasks.shouldForAny { it.id shouldBe taskId }
    }

    open fun `test cancel task`() = runTest(timeout = testTimeout) {
        val createTaskRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do cancelable task"),
                ),
                contextId = "test-context"
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest) as Task).id

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

    open fun `test subscribe to task`() = runTest(timeout = testTimeout) {
        val createTaskRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do long-running task"),
                ),
                contextId = "test-context"
            ),
            configuration = SendMessageConfiguration(
                blocking = false
            )
        )

        val taskId = (client.sendMessage(createTaskRequest) as Task).id

        val resubscribeTaskRequest = SubscribeToTaskRequest(
            id = taskId,
        )

        val events = client
            .subscribeToTask(resubscribeTaskRequest)
            .toList()

        events.shouldNotBeEmpty()

        events.shouldForAll {
            it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
                it.taskId shouldBe taskId
                it.contextId shouldBe "test-context"

                it.status should {
                    it.state shouldBe TaskState.TASK_STATE_WORKING
                    it.message shouldNotBeNull {
                        role shouldBe Role.ROLE_AGENT

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

    open fun `test push notification configs`() = runTest(timeout = testTimeout) {
        val createTaskRequest = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(
                    TextPart("do long-running task"),
                ),
                contextId = "test-context"
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest) as Task).id

        val pushConfigId = "push-id"

        val pushConfig = TaskPushNotificationConfig(
            taskId = taskId,
            id = pushConfigId,
            url = "https://localhost:3000",
            token = "push-token",
            authentication = AuthenticationInfo(
                schemes = listOf("bearer"),
                credentials = "very-secret-credential"
            )
        )

        val setPushConfigResponse = client.createTaskPushNotificationConfig(pushConfig)
        setPushConfigResponse shouldBe pushConfig

        val getPushConfigRequest = GetTaskPushNotificationConfigRequest(
            taskId = taskId,
            id = pushConfigId,
        )

        val response = client.getTaskPushNotificationConfig(getPushConfigRequest)
        response shouldBe pushConfig

        val listPushConfigRequest = ListTaskPushNotificationConfigsRequest(
            taskId = taskId,
        )

        val listPushConfigResponse = client.listTaskPushNotificationConfigs(listPushConfigRequest)
        listPushConfigResponse.configs shouldBe listOf(pushConfig)

        val deletePushConfigRequest = DeleteTaskPushNotificationConfigRequest(
            taskId = taskId,
            id = pushConfigId,
        )

        client.deleteTaskPushNotificationConfig(deletePushConfigRequest)

        shouldThrowExactly<A2AInternalErrorException> {
            client.getTaskPushNotificationConfig(getPushConfigRequest)
        }
    }
}
