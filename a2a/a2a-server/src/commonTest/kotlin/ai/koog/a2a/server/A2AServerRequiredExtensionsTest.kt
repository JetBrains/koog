package ai.koog.a2a.server

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AExtensionSupportRequiredException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentExtension
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.server.turn.TurnEventProcessor
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.ServerCallContext
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
                    eventProcessor: TurnEventProcessor
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

    private fun agentCard(vararg extensions: AgentExtension) =
        testAgentCard(AgentCapabilities(extensions = extensions.toList()))

    private fun serverWithCard(card: AgentCard, extendedCard: AgentCard? = null) = A2AServer(
        agentExecutor = UnusedAgentExecutor,
        agentCard = card,
        agentCardExtended = extendedCard,
    )

    @Test
    fun testEveryOperationRequiresDeclaredExtensions() = runTest {
        (operations(server) - A2AMethod.GetExtendedAgentCard).forEach { (method, operation) ->
            val e = assertFailsWith<A2AExtensionSupportRequiredException>(method.value) { operation(withoutExtensions) }
            assertEquals(A2AErrorCodes.EXTENSION_SUPPORT_REQUIRED, e.errorCode, method.value)
        }
    }

    @Test
    fun testGetExtendedAgentCardSkipsRequiredExtensionsCheck() = runTest {
        val card = agentCard(AgentExtension(uri = REQUIRED, required = true))
        val extendedCard = card.copy(name = "Extended agent")
        val cardServer = serverWithCard(
            card.copy(capabilities = card.capabilities.copy(extendedAgentCard = true)),
            extendedCard,
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
        // An unknown extension doesn't stand in for a required one.
        assertFailsWith<A2AExtensionSupportRequiredException> {
            server.onGetTask(GetTaskRequest(id = "task-1"), ctxWith("urn:example:unknown"))
        }
    }

    @Test
    fun testAllRequiredExtensionsPresentAllowsRequestsAndUnknownOnesAreIgnored() = runTest {
        val unknown = "urn:example:unknown"

        listOf(ctxWith(REQUIRED, OTHER_REQUIRED), ctxWith(REQUIRED, OTHER_REQUIRED, unknown)).forEach { ctx ->
            assertEquals("task-1", server.onGetTask(GetTaskRequest(id = "task-1"), ctx).id)
            assertEquals("reply", (server.onSendMessage(SendMessageRequest(message), ctx) as Message).messageId)
        }

        // The executor gets the requested extensions as they are, unknown ones included.
        assertEquals(listOf(setOf(REQUIRED, OTHER_REQUIRED), setOf(REQUIRED, OTHER_REQUIRED, unknown)), seenExtensions)
    }

    @Test
    fun testNoRequiredExtensionsInCardMeansNoCheck() = runTest {
        val plainServer = serverWithCard(agentCard(AgentExtension(uri = OPTIONAL, required = false)))

        assertEquals(emptyList(), plainServer.onListTasks(ListTasksRequest(), withoutExtensions).tasks)
    }

    @Test
    fun testOverrideCanSkipExtensionCheckForOneOperation() = runTest {
        val lenient = object : A2AServer(
            agentExecutor = UnusedAgentExecutor,
            agentCard = agentCard(AgentExtension(uri = REQUIRED, required = true)),
        ) {
            override fun checkAccepting(method: A2AMethod, ctx: ServerCallContext) {
                if (method != A2AMethod.ListTasks) super.checkAccepting(method, ctx)
            }
        }

        assertEquals(emptyList(), lenient.onListTasks(ListTasksRequest(), withoutExtensions).tasks)
        assertFailsWith<A2AExtensionSupportRequiredException> {
            lenient.onGetTask(GetTaskRequest(id = "task-1"), withoutExtensions)
        }
    }
}
