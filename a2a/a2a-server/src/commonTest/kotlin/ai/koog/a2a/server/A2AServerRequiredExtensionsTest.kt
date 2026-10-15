package ai.koog.a2a.server

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AExtensionSupportRequiredException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentExtension
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTasksRequest
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
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A2AServerRequiredExtensionsTest {
    private companion object {
        private const val REQUIRED = "urn:example:required"
        private const val OTHER_REQUIRED = "urn:example:other-required"
        private const val OPTIONAL = "urn:example:optional"
    }

    private val seenExtensions = mutableListOf<Set<String>>()
    private lateinit var server: A2AServer

    private fun ctxWith(vararg extensions: String) = ServerCallContext(
        headers = mapOf("a2a-extensions" to listOf(extensions.joinToString(",")))
    )

    private val withoutExtensions = ServerCallContext()

    private val message = Message(messageId = "m1", role = Role.ROLE_USER, parts = listOf(TextPart("hi")))

    @BeforeTest
    fun setUp() = runTest {
        seenExtensions.clear()

        val taskStorage = InMemoryTaskStorage()
        taskStorage.update(
            Task(id = "task-1", contextId = "context-1", status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED))
        )

        server = A2AServer(
            agentExecutor = object : AgentExecutor {
                override suspend fun execute(
                    context: RequestContext<SendMessageRequest>,
                    eventProcessor: SessionEventProcessor
                ) {
                    seenExtensions += context.callContext.requestedExtensions
                    eventProcessor.sendMessage(
                        Message(
                            messageId = "reply",
                            role = Role.ROLE_AGENT,
                            parts = listOf(TextPart("ok")),
                            contextId = context.contextId,
                        )
                    )
                }
            },
            agentCard = agentCard(
                AgentExtension(uri = REQUIRED, required = true),
                AgentExtension(uri = OTHER_REQUIRED, required = true),
                AgentExtension(uri = OPTIONAL, required = false),
                AgentExtension(uri = "urn:example:unspecified"),
            ),
            taskStorage = taskStorage,
        )
    }

    private fun agentCard(vararg extensions: AgentExtension) = AgentCard(
        name = "Test agent",
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
        capabilities = AgentCapabilities(extensions = extensions.toList()),
    )

    private fun operations(): Map<String, suspend (ServerCallContext) -> Unit> = mapOf(
        "SendMessage" to { c ->
            server.onSendMessage(SendMessageRequest(message), c)
            Unit
        },
        "SendMessageStreaming" to { c ->
            server.onSendMessageStreaming(SendMessageRequest(message), c).toList()
            Unit
        },
        "GetTask" to { c ->
            server.onGetTask(GetTaskRequest(id = "task-1"), c)
            Unit
        },
        "ListTasks" to { c ->
            server.onListTasks(ListTasksRequest(), c)
            Unit
        },
        "CancelTask" to { c ->
            server.onCancelTask(CancelTaskRequest(id = "task-1"), c)
            Unit
        },
        "SubscribeToTask" to { c ->
            server.onSubscribeToTask(SubscribeToTaskRequest(id = "task-1"), c).toList()
            Unit
        },
        "CreateTaskPushNotificationConfig" to { c ->
            server.onCreateTaskPushNotificationConfig(
                TaskPushNotificationConfig(taskId = "task-1", url = "http://localhost/hook"),
                c
            )
            Unit
        },
        "GetTaskPushNotificationConfig" to { c ->
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest(taskId = "task-1", id = "c"), c)
            Unit
        },
        "ListTaskPushNotificationConfigs" to { c ->
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest(taskId = "task-1"), c)
            Unit
        },
        "DeleteTaskPushNotificationConfig" to { c ->
            server.onDeleteTaskPushNotificationConfig(
                DeleteTaskPushNotificationConfigRequest(taskId = "task-1", id = "c"),
                c
            )
        },
    )

    @Test
    fun testEveryOperationRequiresDeclaredExtensions() = runTest {
        operations().forEach { (name, operation) ->
            val e = assertFailsWith<A2AExtensionSupportRequiredException>(name) { operation(withoutExtensions) }
            assertEquals(A2AErrorCodes.EXTENSION_SUPPORT_REQUIRED, e.errorCode, name)
        }
    }

    @Test
    fun testGetExtendedAgentCardSkipsRequiredExtensionsCheck() = runTest {
        val extendedCard = agentCard(AgentExtension(uri = REQUIRED, required = true)).copy(name = "Extended agent")
        val cardServer = A2AServer(
            agentExecutor = object : AgentExecutor {
                override suspend fun execute(
                    context: RequestContext<SendMessageRequest>,
                    eventProcessor: SessionEventProcessor
                ) = error("Not expected to be called")
            },
            agentCard = agentCard(AgentExtension(uri = REQUIRED, required = true)).let {
                it.copy(capabilities = it.capabilities.copy(extendedAgentCard = true))
            },
            agentCardExtended = extendedCard,
        )

        assertEquals(extendedCard, cardServer.onGetExtendedAgentCard(GetExtendedAgentCardRequest(), withoutExtensions))
        assertFailsWith<A2AExtensionSupportRequiredException> {
            cardServer.onListTasks(ListTasksRequest(), withoutExtensions)
        }
    }

    @Test
    fun testMessageNamesMissingExtensionsOnly() = runTest {
        val e = assertFailsWith<A2AExtensionSupportRequiredException> {
            server.onGetTask(GetTaskRequest(id = "task-1"), ctxWith(REQUIRED, OPTIONAL))
        }

        assertEquals(
            "Required extensions were not requested in ${A2AHeaders.A2A_EXTENSIONS}: $OTHER_REQUIRED",
            e.message
        )
    }

    @Test
    fun testAllRequiredExtensionsPresentAllowsRequests() = runTest {
        val ctx = ctxWith(REQUIRED, OTHER_REQUIRED)

        assertEquals("task-1", server.onGetTask(GetTaskRequest(id = "task-1"), ctx).id)
        assertEquals(
            "reply",
            (server.onSendMessage(SendMessageRequest(message), ctx) as Message).messageId
        )
    }

    @Test
    fun testUnknownRequestedExtensionsAreIgnored() = runTest {
        val ctx = ctxWith(REQUIRED, OTHER_REQUIRED, "urn:example:unknown")

        assertEquals("task-1", server.onGetTask(GetTaskRequest(id = "task-1"), ctx).id)
    }

    @Test
    fun testUnknownExtensionDoesNotSatisfyRequiredOne() = runTest {
        assertFailsWith<A2AExtensionSupportRequiredException> {
            server.onGetTask(GetTaskRequest(id = "task-1"), ctxWith("urn:example:unknown"))
        }
    }

    @Test
    fun testRequestedExtensionsReachTheExecutor() = runTest {
        server.onSendMessage(SendMessageRequest(message), ctxWith(REQUIRED, OTHER_REQUIRED, "urn:example:unknown"))

        assertEquals(listOf(setOf(REQUIRED, OTHER_REQUIRED, "urn:example:unknown")), seenExtensions)
    }

    @Test
    fun testNoRequiredExtensionsInCardMeansNoCheck() = runTest {
        val plainServer = A2AServer(
            agentExecutor = object : AgentExecutor {
                override suspend fun execute(
                    context: RequestContext<SendMessageRequest>,
                    eventProcessor: SessionEventProcessor
                ) = error("Not expected to be called")
            },
            agentCard = agentCard(AgentExtension(uri = OPTIONAL, required = false)),
        )

        assertEquals(emptyList(), plainServer.onListTasks(ListTasksRequest(), withoutExtensions).tasks)
    }

    @Test
    fun testCheckIsOverridable() = runTest {
        val lenient = object : A2AServer(
            agentExecutor = object : AgentExecutor {
                override suspend fun execute(
                    context: RequestContext<SendMessageRequest>,
                    eventProcessor: SessionEventProcessor
                ) = error("Not expected to be called")
            },
            agentCard = agentCard(AgentExtension(uri = REQUIRED, required = true)),
        ) {
            override fun checkRequiredExtensions(context: ServerCallContext) = Unit
        }

        assertEquals(emptyList(), lenient.onListTasks(ListTasksRequest(), withoutExtensions).tasks)
    }
}
