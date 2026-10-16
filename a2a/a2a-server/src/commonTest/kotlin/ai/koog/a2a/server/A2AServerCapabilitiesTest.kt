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
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Capability gating of [A2AServer]: operations of a capability the agent card doesn't declare are rejected
 * with the protocol's error before any work is done (and before the first event of a stream).
 */
class A2AServerCapabilitiesTest {
    private companion object {
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

    private val extendedCard = testAgentCard(AgentCapabilities(extendedAgentCard = true), name = "Extended agent")

    private suspend fun createServer(
        capabilities: AgentCapabilities,
        agentCardExtended: AgentCard? = null,
    ): A2AServer {
        val taskStorage = InMemoryTaskStorage()
        taskStorage.update(workingTask)

        val pushStorage = InMemoryPushNotificationConfigStorage()
        pushStorage.save(pushConfig, ctx)

        return A2AServer(
            agentExecutor = ScriptedAgentExecutor(mapOf("hi" to AgentScript { _, processor -> processor.sendMessage(reply) })),
            agentCard = testAgentCard(capabilities),
            agentCardExtended = agentCardExtended,
            taskStorage = taskStorage,
            pushConfigStorage = pushStorage,
            pushSender = GatedPushNotificationSender(),
        )
    }

    private data class Operation(
        val name: String,
        /** The agent capabilities with the capability of this operation set to the given value. */
        val capabilities: (Boolean?) -> AgentCapabilities,
        val expectedError: Int,
        val expectedType: KClass<out A2AException>,
        val call: suspend (A2AServer, ServerCallContext) -> Any?,
    )

    private val streamingOperations = listOf(
        Operation("SendStreamingMessage", { AgentCapabilities(streaming = it) }, A2AErrorCodes.UNSUPPORTED_OPERATION, A2AUnsupportedOperationException::class) { server, c ->
            server.onSendMessageStreaming(SendMessageRequest(message), c).toList()
        },
        Operation("SubscribeToTask", { AgentCapabilities(streaming = it) }, A2AErrorCodes.UNSUPPORTED_OPERATION, A2AUnsupportedOperationException::class) { server, c ->
            // The stream of a working task lasts until the task is terminal: take the snapshot only.
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), c).take(1).toList()
        },
    )

    private val pushOperations = listOf(
        pushOperation("CreateTaskPushNotificationConfig") { server, c ->
            server.onCreateTaskPushNotificationConfig(pushConfig.copy(id = "config-2"), c)
        },
        pushOperation("GetTaskPushNotificationConfig") { server, c ->
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest(TASK_ID, "config-1"), c)
        },
        pushOperation("ListTaskPushNotificationConfigs") { server, c ->
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest(TASK_ID), c)
        },
        pushOperation("DeleteTaskPushNotificationConfig") { server, c ->
            server.onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigRequest(TASK_ID, "config-1"), c)
        },
    )

    private fun pushOperation(name: String, call: suspend (A2AServer, ServerCallContext) -> Any?) = Operation(
        name,
        { AgentCapabilities(pushNotifications = it) },
        A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED,
        A2APushNotificationNotSupportedException::class,
        call,
    )

    private val extendedCardOperation = Operation(
        "GetExtendedAgentCard",
        { AgentCapabilities(extendedAgentCard = it) },
        A2AErrorCodes.UNSUPPORTED_OPERATION,
        A2AUnsupportedOperationException::class,
    ) { server, c -> server.onGetExtendedAgentCard(GetExtendedAgentCardRequest(), c) }

    @Test
    fun testOperationsOfDisabledCapabilityFailWithExpectedErrors() = runTest {
        (streamingOperations + pushOperations + extendedCardOperation).forEach { operation ->
            // A capability that is not declared is the same as a disabled one.
            listOf(null, false).forEach { declared ->
                val label = "${operation.name} with $declared"
                val server = createServer(operation.capabilities(declared), agentCardExtended = extendedCard)

                val failure = assertFailsWith<A2AException>(label) { operation.call(server, ctx) }

                assertEquals(operation.expectedType, failure::class, label)
                assertEquals(operation.expectedError, failure.errorCode, label)
            }
        }
    }

    @Test
    fun testStreamingErrorIsThrownBeforeFirstEvent() = runTest {
        val server = createServer(AgentCapabilities(streaming = false))

        listOf(
            server.onSendMessageStreaming(SendMessageRequest(message), ctx),
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx),
        ).forEach { stream ->
            val (failure, received) = stream.collectExpectingFailure()

            assertEquals(A2AUnsupportedOperationException::class, failure::class)
            assertEquals(emptyList(), received)
        }
    }

    @Test
    fun testStreamingDisabledDoesNotAffectBlockingSendMessage() = runTest {
        val server = createServer(AgentCapabilities())

        assertEquals(reply, server.onSendMessage(SendMessageRequest(message), ctx))
    }

    @Test
    fun testEnabledStreamingAndPushOperationsSucceed() = runTest {
        val streaming = createServer(AgentCapabilities(streaming = true))
        assertEquals(listOf<Event>(reply), streaming.onSendMessageStreaming(SendMessageRequest(message), ctx).toList())
        assertEquals(
            listOf<Event>(workingTask),
            streaming.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).take(1).toList()
        )

        val push = createServer(AgentCapabilities(pushNotifications = true))
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
        val server = createServer(AgentCapabilities(extendedAgentCard = true), agentCardExtended = extendedCard)

        assertEquals(extendedCard, server.onGetExtendedAgentCard(GetExtendedAgentCardRequest(), ctx))
    }

    @Test
    fun testExtendedCardDeclaredButNotConfiguredFailsWithNotConfigured() = runTest {
        val server = createServer(AgentCapabilities(extendedAgentCard = true), agentCardExtended = null)

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
