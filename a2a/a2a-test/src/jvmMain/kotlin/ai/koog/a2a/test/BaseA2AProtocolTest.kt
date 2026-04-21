package ai.koog.a2a.test

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendConfiguration
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.PushNotificationAuthenticationInfo
import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.transport.Request
import ai.koog.test.utils.untilAsserted
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
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
        val agentCard = client.getAgentCard()

        // Assert on the full AgentCard structure
        val expectedAgentCard = AgentCard(
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

        agentCard shouldBe expectedAgentCard
    }

    open fun `test get authenticated extended agent card`() = runTest(timeout = testTimeout) {
        val request = Request(data = null)

        val response = client.getAuthenticatedExtendedAgentCard(request)

        // Assert on the extended agent card structure
        val expectedExtendedAgentCard = AgentCard(
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

        response.data shouldBe expectedExtendedAgentCard
    }

    open fun `test send message`() = runTest(timeout = testTimeout) {
        val request = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("hello world"),
                    ),
                    contextId = "test-context"
                ),
            )
        )

        val response = client.sendMessage(request)

        response should { response ->
            response.id shouldBe request.id

            response.data.shouldBeInstanceOf<Message> {
                it.role shouldBe Role.Agent
                it.parts shouldBe listOf(TextPart("Hello World"))
                it.contextId shouldBe "test-context"
            }
        }
    }

    open fun `test send message streaming`() = runTest(timeout = testTimeout) {
        val createTaskRequest = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("do task"),
                    ),
                    contextId = "test-context"
                ),
            ),
        )

        val events: List<Event> = await.untilAsserted(this) {
            val list = client
                .sendMessageStreaming(createTaskRequest)
                .toList()
                .map { it.data }

            list shouldHaveSize 3
            return@untilAsserted list
        }!!

        events[0].shouldBeInstanceOf<Task> { task ->
            task.contextId shouldBe "test-context"
            task.status should {
                it.state shouldBe TaskState.Submitted
            }

            task.history shouldNotBeNull {
                this shouldHaveSize 1

                this[0] should {
                    it.role shouldBe Role.User
                    it.parts shouldBe listOf(TextPart("do task"))
                }
            }
        }

        events[1].shouldBeInstanceOf<TaskStatusUpdateEvent> {
            it.contextId shouldBe "test-context"

            it.status should {
                it.state shouldBe TaskState.Working
                it.message shouldNotBeNull {
                    role shouldBe Role.Agent
                    parts shouldBe listOf(TextPart("Working on task"))
                }
            }
        }

        events[2].shouldBeInstanceOf<TaskStatusUpdateEvent> {
            it.contextId shouldBe "test-context"

            it.status should {
                it.state shouldBe TaskState.Completed
                it.message shouldNotBeNull {
                    role shouldBe Role.Agent
                    parts shouldBe listOf(TextPart("Task completed"))
                }
            }
        }
    }

    open fun `test get task`() = runTest(timeout = testTimeout) {
        val createTaskRequest = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("do task"),
                    ),
                    contextId = "test-context"
                ),
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest).data as Task).id

        val getTaskRequest = Request(
            data = TaskQueryParams(
                id = taskId,
                historyLength = 1
            )
        )

        await
            .ignoreExceptions()
            .untilAsserted(this) {
                val response = client.getTask(getTaskRequest)

                response.data should { task ->
                    task.id shouldBe taskId
                    task.contextId shouldBe "test-context"
                    task.status should { status ->
                        status.state shouldBe TaskState.Completed
                    }
                }
            }
    }

    open fun `test cancel task`() = runTest(timeout = testTimeout) {
        val createTaskRequest = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("do cancelable task"),
                    ),
                    contextId = "test-context"
                ),
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest).data as Task).id

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

    open fun `test resubscribe task`() = runTest(timeout = testTimeout) {
        val createTaskRequest = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("do long-running task"),
                    ),
                    contextId = "test-context"
                ),
                configuration = MessageSendConfiguration(
                    blocking = false
                )
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest).data as Task).id

        val resubscribeTaskRequest = Request(
            data = TaskIdParams(
                id = taskId,
            )
        )

        val events =
            await.ignoreExceptions().untilAsserted(this) {
                val list = client
                    .resubscribeTask(resubscribeTaskRequest)
                    .toList()
                    .map { it.data }
                list.shouldNotBeEmpty()
                return@untilAsserted list
            }!!

        events.shouldForAll {
            it.shouldBeInstanceOf<TaskStatusUpdateEvent> {
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

    open fun `test push notification configs`() = runTest(timeout = testTimeout) {
        val createTaskRequest = Request(
            data = MessageSendParams(
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.User,
                    parts = listOf(
                        TextPart("do long-running task"),
                    ),
                    contextId = "test-context"
                ),
            ),
        )

        val taskId = (client.sendMessage(createTaskRequest).data as Task).id

        val pushConfig = TaskPushNotificationConfig(
            taskId = taskId,
            pushNotificationConfig = PushNotificationConfig(
                id = "push-id",
                url = "https://localhost:3000",
                token = "push-token",
                authentication = PushNotificationAuthenticationInfo(
                    schemes = listOf("bearer"),
                    credentials = "very-secret-credential"
                )
            )
        )

        val request = Request(
            data = pushConfig
        )

        val setPushConfigResponse = client.setTaskPushNotificationConfig(request)
        setPushConfigResponse.data shouldBe pushConfig

        val getPushConfigRequest = Request(
            data = TaskPushNotificationConfigParams(
                id = taskId,
                pushNotificationConfigId = pushConfig.pushNotificationConfig.id,
            )
        )

        await.untilAsserted(this) {
            val response = client.getTaskPushNotificationConfig(getPushConfigRequest)
            response.data shouldBe pushConfig
        }

        val listPushConfigRequest = Request(
            data = TaskIdParams(
                id = taskId,
            )
        )

        await.untilAsserted(this) {
            val listPushConfigResponse = client.listTaskPushNotificationConfig(listPushConfigRequest)
            listPushConfigResponse.data shouldBe listOf(pushConfig)
        }

        val deletePushConfigRequest = Request(
            data = TaskPushNotificationConfigParams(
                id = taskId,
                pushNotificationConfigId = pushConfig.pushNotificationConfig.id,
            )
        )

        client.deleteTaskPushNotificationConfig(deletePushConfigRequest)

        await.untilAsserted(this) {
            shouldThrowExactly<A2AInternalErrorException> {
                client.getTaskPushNotificationConfig(getPushConfigRequest)
            }
        }
    }
}
