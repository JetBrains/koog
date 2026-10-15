package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AExtendedAgentCardNotConfiguredException
import ai.koog.a2a.exceptions.A2AExtensionSupportRequiredException
import ai.koog.a2a.exceptions.A2APushNotificationNotSupportedException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentExtension
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Capability gating of [A2AServer]: operations of a capability the agent card doesn't declare are rejected
 * with the protocol's error before any work is done (and before the first event of a stream).
 */
class A2AServerCapabilitiesTest {
    private companion object {
        private const val TASK_ID = "task-1"
        private const val CONTEXT_ID = "context-1"
        private const val REQUIRED_EXTENSION = "urn:example:required"
    }

    private val ctx = ServerCallContext()

    private val message = Message(
        messageId = "m1",
        role = Role.ROLE_USER,
        parts = listOf(TextPart("hi")),
        contextId = CONTEXT_ID,
    )
    private val reply = Message(
        messageId = "reply",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("ok")),
        contextId = CONTEXT_ID,
    )
    private val workingTask = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
    )
    private val pushConfig = TaskPushNotificationConfig(
        url = "https://example.com/webhook",
        taskId = TASK_ID,
        id = "config-1",
    )

    private fun agentCard(
        capabilities: AgentCapabilities,
        name: String = "Test agent",
    ) = AgentCard(
        name = name,
        description = "Test agent",
        supportedInterfaces = listOf(
            AgentInterface(
                url = "http://localhost/",
                protocolBinding = TransportProtocol.JSONRPC,
                protocolVersion = "1.0"
            )
        ),
        version = "1.0.0",
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = emptyList(),
        capabilities = capabilities,
    )

    private val extendedCard = agentCard(AgentCapabilities(extendedAgentCard = true), name = "Extended agent")

    private suspend fun createServer(
        capabilities: AgentCapabilities,
        agentCardExtended: AgentCard? = null,
    ): A2AServer {
        val taskStorage = InMemoryTaskStorage()
        taskStorage.update(workingTask)

        val pushStorage = InMemoryPushNotificationConfigStorage()
        pushStorage.save(pushConfig, ctx)

        return A2AServer(
            agentExecutor = object : AgentExecutor {
                override suspend fun execute(
                    context: RequestContext<SendMessageRequest>,
                    eventProcessor: SessionEventProcessor
                ) {
                    eventProcessor.sendMessage(reply)
                }
            },
            agentCard = agentCard(capabilities),
            agentCardExtended = agentCardExtended,
            taskStorage = taskStorage,
            pushConfigStorage = pushStorage,
        )
    }

    private data class Operation(
        val name: String,
        val capability: String,
        val expectedError: Int,
        val call: suspend (A2AServer, ServerCallContext) -> Any?,
    )

    private val streamingOperations = listOf(
        Operation("SendStreamingMessage", "streaming", A2AErrorCodes.UNSUPPORTED_OPERATION) { server, c ->
            server.onSendMessageStreaming(SendMessageRequest(message), c).toList()
        },
        Operation("SubscribeToTask", "streaming", A2AErrorCodes.UNSUPPORTED_OPERATION) { server, c ->
            // The stream of a working task lasts until the task is terminal: take the snapshot only.
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), c).take(1).toList()
        },
    )

    private val pushOperations = listOf(
        Operation("CreateTaskPushNotificationConfig", "pushNotifications", A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED) { server, c ->
            server.onCreateTaskPushNotificationConfig(pushConfig.copy(id = "config-2"), c)
        },
        Operation("GetTaskPushNotificationConfig", "pushNotifications", A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED) { server, c ->
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest(TASK_ID, "config-1"), c)
        },
        Operation("ListTaskPushNotificationConfigs", "pushNotifications", A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED) { server, c ->
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest(TASK_ID), c)
        },
        Operation("DeleteTaskPushNotificationConfig", "pushNotifications", A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED) { server, c ->
            server.onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigRequest(TASK_ID, "config-1"), c)
        },
    )

    private val extendedCardOperation =
        Operation("GetExtendedAgentCard", "extendedAgentCard", A2AErrorCodes.UNSUPPORTED_OPERATION) { server, c ->
            server.onGetExtendedAgentCard(GetExtendedAgentCardRequest(), c)
        }

    private fun disabledCapabilities(capability: String): List<AgentCapabilities> = when (capability) {
        "streaming" -> listOf(AgentCapabilities(), AgentCapabilities(streaming = false))
        "pushNotifications" -> listOf(AgentCapabilities(), AgentCapabilities(pushNotifications = false))
        "extendedAgentCard" -> listOf(AgentCapabilities(), AgentCapabilities(extendedAgentCard = false))
        else -> error("Unknown capability $capability")
    }

    private fun enabledCapabilities(capability: String): AgentCapabilities = when (capability) {
        "streaming" -> AgentCapabilities(streaming = true)
        "pushNotifications" -> AgentCapabilities(pushNotifications = true)
        "extendedAgentCard" -> AgentCapabilities(extendedAgentCard = true)
        else -> error("Unknown capability $capability")
    }

    @Test
    fun testOperationsOfDisabledCapabilityFailWithExpectedErrors() = runTest {
        (streamingOperations + pushOperations).forEach { operation ->
            disabledCapabilities(operation.capability).forEach { capabilities ->
                val label = "${operation.name} with $capabilities"
                val server = createServer(capabilities, agentCardExtended = extendedCard)

                val failure = assertFailsWith<A2AException>(label) { operation.call(server, ctx) }

                assertEquals(operation.expectedError, failure.errorCode, label)
            }
        }
    }

    @Test
    fun testDisabledCapabilityErrorTypes() = runTest {
        val noCapabilities = createServer(AgentCapabilities())

        assertFailsWith<A2AUnsupportedOperationException> { streamingOperations[0].call(noCapabilities, ctx) }
        assertFailsWith<A2AUnsupportedOperationException> { streamingOperations[1].call(noCapabilities, ctx) }
        pushOperations.forEach {
            assertFailsWith<A2APushNotificationNotSupportedException>(it.name) { it.call(noCapabilities, ctx) }
        }
        assertFailsWith<A2AUnsupportedOperationException> { extendedCardOperation.call(noCapabilities, ctx) }
    }

    @Test
    fun testStreamingErrorIsThrownBeforeFirstEvent() = runTest {
        val server = createServer(AgentCapabilities(streaming = false))
        val received = mutableListOf<Event>()

        val sendFailure = runCatching {
            server.onSendMessageStreaming(SendMessageRequest(message), ctx).collect { received += it }
        }.exceptionOrNull()
        val subscribeFailure = runCatching {
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collect { received += it }
        }.exceptionOrNull()

        assertEquals(A2AUnsupportedOperationException::class, sendFailure?.let { it::class })
        assertEquals(A2AUnsupportedOperationException::class, subscribeFailure?.let { it::class })
        assertEquals(emptyList(), received)
    }

    @Test
    fun testStreamingDisabledDoesNotAffectBlockingSendMessage() = runTest {
        val server = createServer(AgentCapabilities())

        assertEquals(reply, server.onSendMessage(SendMessageRequest(message), ctx))
    }

    @Test
    fun testEnabledStreamingAndPushOperationsSucceed() = runTest {
        val streaming = createServer(enabledCapabilities("streaming"))
        assertEquals(listOf<Event>(reply), streaming.onSendMessageStreaming(SendMessageRequest(message), ctx).toList())
        assertEquals(
            listOf<Event>(workingTask),
            streaming.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).take(1).toList()
        )

        val push = createServer(enabledCapabilities("pushNotifications"))
        val created = pushConfig.copy(id = "config-2")
        assertEquals(created, push.onCreateTaskPushNotificationConfig(created, ctx))
        assertEquals(
            pushConfig,
            push.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest(TASK_ID, "config-1"), ctx)
        )
        assertEquals(
            ListTaskPushNotificationConfigsResponse(configs = listOf(pushConfig, created)),
            push.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest(TASK_ID), ctx).let {
                it.copy(configs = it.configs.sortedBy { config -> config.id }, nextPageToken = "")
            }
        )
        push.onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigRequest(TASK_ID, "config-1"), ctx)
    }

    @Test
    fun testExtendedCardIsReturnedWhenDeclaredAndConfigured() = runTest {
        val server = createServer(enabledCapabilities("extendedAgentCard"), agentCardExtended = extendedCard)

        assertEquals(extendedCard, server.onGetExtendedAgentCard(GetExtendedAgentCardRequest(), ctx))
    }

    @Test
    fun testExtendedCardDeclaredButNotConfiguredFailsWithNotConfigured() = runTest {
        val server = createServer(enabledCapabilities("extendedAgentCard"), agentCardExtended = null)

        val failure = assertFailsWith<A2AExtendedAgentCardNotConfiguredException> {
            server.onGetExtendedAgentCard(GetExtendedAgentCardRequest(), ctx)
        }

        assertEquals(A2AErrorCodes.EXTENDED_AGENT_CARD_NOT_CONFIGURED, failure.errorCode)
    }

    @Test
    fun testExtensionCheckPrecedesCapabilityCheck() = runTest {
        val server = createServer(
            AgentCapabilities(extensions = listOf(AgentExtension(uri = REQUIRED_EXTENSION, required = true)))
        )

        (streamingOperations + pushOperations).forEach { operation ->
            val failure = assertFailsWith<A2AExtensionSupportRequiredException>(operation.name) {
                operation.call(server, ctx)
            }

            assertEquals(A2AErrorCodes.EXTENSION_SUPPORT_REQUIRED, failure.errorCode, operation.name)
        }
    }
}
