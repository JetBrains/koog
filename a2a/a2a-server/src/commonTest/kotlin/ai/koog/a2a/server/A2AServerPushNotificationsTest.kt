package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInvalidAgentResponseException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2APushNotificationNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.pushes
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.turn.IdGenerator
import ai.koog.a2a.server.turn.UuidIdGenerator
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The id of the task created by a turn of the agent, see [NewTaskIdGenerator]. */
private const val NEW_TASK_ID = "task-2"

private object NewTaskIdGenerator : IdGenerator {
    override suspend fun generateContextId(message: Message): String = CONTEXT_ID
    override suspend fun generateTaskId(message: Message): String = NEW_TASK_ID
    override suspend fun generatePushNotificationConfigId(config: TaskPushNotificationConfig): String =
        error("Not expected")
}

class A2AServerPushNotificationsTest {
    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: GatedPushNotificationSender
    private lateinit var server: A2AServer

    private val completedTask = Task(
        id = "task-1",
        contextId = "context-1",
        status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED),
    )
    private val config = TaskPushNotificationConfig(
        url = "https://example.com/webhook",
        taskId = "task-1",
        id = "config-1",
        token = "token",
    )

    private fun createServer(
        pushNotifications: Boolean = true,
        pushConfigStorage: InMemoryPushNotificationConfigStorage? = pushStorage,
        idGenerator: IdGenerator = UuidIdGenerator,
        storage: TaskStorage = taskStorage,
    ) = A2AServer(
        agentExecutor = UnusedAgentExecutor,
        agentCard = testAgentCard(AgentCapabilities(pushNotifications = pushNotifications)),
        taskStorage = storage,
        pushConfigStorage = pushConfigStorage,
        idGenerator = idGenerator,
    )

    @BeforeTest
    fun setUp() = runTest {
        taskStorage = InMemoryTaskStorage()
        pushStorage = InMemoryPushNotificationConfigStorage()
        pushSender = GatedPushNotificationSender()
        configsAtAgentStart.clear()
        validated.clear()
        taskStorage.update(completedTask)
        server = createServer()
    }

    @Test
    fun testCreateKeepsClientProvidedIdAndConfigIsRetrievable() = runTest {
        // The task of the fixture is already completed, which doesn't matter.
        assertEquals(config, server.onCreateTaskPushNotificationConfig(config, ctx))

        assertEquals(
            config,
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("task-1", "config-1"), ctx)
        )
        assertEquals(listOf(config), pushStorage.getAll("task-1"))
    }

    @Test
    fun testCreateWithoutIdAssignsDifferentIdsAndConfigsAreRetrievable() = runTest {
        val first = server.onCreateTaskPushNotificationConfig(config.copy(id = null), ctx)
        val second = server.onCreateTaskPushNotificationConfig(config.copy(id = " "), ctx)

        val firstId = assertNotNull(first.id)
        val secondId = assertNotNull(second.id)
        assertTrue(firstId.isNotBlank() && secondId.isNotBlank())
        assertNotEquals(firstId, secondId)
        assertEquals(config.copy(id = firstId), first)
        assertEquals(config.copy(id = secondId), second)
        assertEquals(
            first,
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("task-1", firstId), ctx)
        )
        assertEquals(
            ListTaskPushNotificationConfigsResponse(configs = listOf(first, second)),
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest("task-1"), ctx)
        )
    }

    @Test
    fun testCreateWithoutIdUsesIdGenerator() = runTest {
        val generator = object : IdGenerator {
            override suspend fun generateContextId(message: Message): String = error("Not expected")
            override suspend fun generateTaskId(message: Message): String = error("Not expected")
            override suspend fun generatePushNotificationConfigId(config: TaskPushNotificationConfig): String =
                "generated-${config.taskId}"
        }
        val customServer = createServer(idGenerator = generator)

        val created = customServer.onCreateTaskPushNotificationConfig(config.copy(id = null), ctx)

        assertEquals(config.copy(id = "generated-task-1"), created)
    }

    @Test
    fun testCreateWithoutTaskIdFailsWithInvalidParams() = runTest {
        assertFailsWith<A2AInvalidParamsException> {
            server.onCreateTaskPushNotificationConfig(config.copy(taskId = null), ctx)
        }
    }

    @Test
    fun testCreateForUnknownTaskFailsWithTaskNotFound() = runTest {
        assertFailsWith<A2ATaskNotFoundException> {
            server.onCreateTaskPushNotificationConfig(config.copy(taskId = "unknown"), ctx)
        }
        assertEquals(emptyList(), pushStorage.getAll("unknown"))
    }

    @Test
    fun testGetListDeleteForUnknownTaskFailWithTaskNotFound() = runTest {
        assertFailsWith<A2ATaskNotFoundException> {
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("unknown", "config-1"), ctx)
        }
        assertFailsWith<A2ATaskNotFoundException> {
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest("unknown"), ctx)
        }
        assertFailsWith<A2ATaskNotFoundException> {
            server.onDeleteTaskPushNotificationConfig(
                DeleteTaskPushNotificationConfigRequest("unknown", "config-1"),
                ctx
            )
        }
    }

    @Test
    fun testOperationsForTaskInvisibleToCallerFailWithTaskNotFound() = runTest {
        val owner = ctx
        val otherCaller = ServerCallContext()
        // An authorization-aware storage: the owner-scoped lookup hides the task from everyone but its owner.
        val ownerScopedStorage = object : TaskStorage by taskStorage {
            override suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task? =
                if (context === owner) taskStorage.get(request, context) else null
        }
        val server = createServer(storage = ownerScopedStorage)
        server.onCreateTaskPushNotificationConfig(config, owner)

        assertFailsWith<A2ATaskNotFoundException> {
            server.onCreateTaskPushNotificationConfig(config.copy(id = "config-2"), otherCaller)
        }
        assertFailsWith<A2ATaskNotFoundException> {
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("task-1", "config-1"), otherCaller)
        }
        assertFailsWith<A2ATaskNotFoundException> {
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest("task-1"), otherCaller)
        }
        assertFailsWith<A2ATaskNotFoundException> {
            server.onDeleteTaskPushNotificationConfig(
                DeleteTaskPushNotificationConfigRequest("task-1", "config-1"),
                otherCaller
            )
        }

        // Nothing was created or deleted, and the owner still sees the config.
        assertEquals(listOf(config), pushStorage.getAll("task-1"))
        assertEquals(
            config,
            server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("task-1", "config-1"), owner)
        )
    }

    @Test
    fun testListReturnsConfigsOfTask() = runTest {
        server.onCreateTaskPushNotificationConfig(config, ctx)

        assertEquals(
            ListTaskPushNotificationConfigsResponse(configs = listOf(config)),
            server.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest("task-1"), ctx)
        )
    }

    @Test
    fun testDeleteTwiceSucceeds() = runTest {
        server.onCreateTaskPushNotificationConfig(config, ctx)
        val request = DeleteTaskPushNotificationConfigRequest("task-1", "config-1")

        server.onDeleteTaskPushNotificationConfig(request, ctx)
        server.onDeleteTaskPushNotificationConfig(request, ctx)

        // Deleted and never created configs are the same: not found.
        listOf("config-1", "missing").forEach { id ->
            assertFailsWith<A2ATaskNotFoundException>(id) {
                server.onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("task-1", id), ctx)
            }
        }
    }

    @Test
    fun testOperationsFailWhenPushNotificationsAreNotSupported() = runTest {
        val unsupported = createServer(pushNotifications = false)
        val notConfigured = createServer(pushConfigStorage = null)

        listOf(unsupported, notConfigured).forEach { s ->
            assertFailsWith<A2APushNotificationNotSupportedException> {
                s.onCreateTaskPushNotificationConfig(config, ctx)
            }
            assertFailsWith<A2APushNotificationNotSupportedException> {
                s.onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest("task-1"), ctx)
            }
        }
    }

    // Inline push notification config of SendMessage

    /** The push configs of the turn's task when the agent started, per turn. */
    private val configsAtAgentStart = mutableListOf<List<TaskPushNotificationConfig>>()

    /** The configs passed to [A2AServer.validatePushNotificationConfig]. */
    private val validated = mutableListOf<TaskPushNotificationConfig>()

    private lateinit var executor: ScriptedAgentExecutor

    private val webhook = TaskPushNotificationConfig(url = "https://example.com/inline", token = "inline-token")

    private fun userMessage(text: String, taskId: String? = null) = Message(
        messageId = "user-$text",
        role = Role.ROLE_USER,
        parts = listOf(TextPart(text)),
        contextId = CONTEXT_ID,
        taskId = taskId,
    )

    private fun sendRequest(
        text: String,
        taskId: String? = null,
        pushConfig: TaskPushNotificationConfig? = null,
        tenant: String? = null,
    ) = SendMessageRequest(
        message = userMessage(text, taskId),
        configuration = pushConfig?.let { SendMessageConfiguration(taskPushNotificationConfig = it) },
        tenant = tenant,
    )

    private val submittedTask = Task(
        id = NEW_TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = TaskState.TASK_STATE_SUBMITTED, timestamp = TIMESTAMP),
        history = listOf(userMessage("ask")),
    )

    private fun inputRequired(text: String) = TaskStatusUpdateEvent(
        taskId = NEW_TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(
            state = TaskState.TASK_STATE_INPUT_REQUIRED,
            message = agentMessage(text),
            timestamp = TIMESTAMP,
        ),
    )

    private fun agentMessage(text: String, taskId: String? = NEW_TASK_ID) = Message(
        messageId = "agent-$text",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart(text)),
        contextId = CONTEXT_ID,
        taskId = taskId,
    )

    /** The task stored after the "ask" turn. */
    private val inputRequiredTask = submittedTask.copy(status = inputRequired("ask").status)

    private val scripts = mapOf(
        // Creates the task and leaves it waiting for input.
        "ask" to AgentScript { _, processor ->
            processor.sendTaskEvent(submittedTask)
            processor.sendTaskEvent(inputRequired("ask"))
        },
        // A follow-up that asks for input again.
        "again" to AgentScript { _, processor ->
            processor.sendTaskEvent(inputRequired("again"))
        },
        // A Message instead of a task; rejected in a follow-up.
        "reply" to AgentScript { _, processor ->
            processor.sendMessage(agentMessage("reply", taskId = null))
        },
        "crash" to AgentScript { _, _ ->
            error("Agent failed")
        },
    ).mapValues { (_, script) ->
        AgentScript { context, processor ->
            configsAtAgentStart += pushStorage.getAll(context.taskId)
            script.run(context, processor)
        }
    }

    private fun TestScope.createTurnServer(
        pushNotifications: Boolean = true,
        pushConfigStorage: InMemoryPushNotificationConfigStorage? = pushStorage,
        rejectConfigs: Boolean = false,
    ): A2AServer {
        executor = ScriptedAgentExecutor(scripts)

        return object : A2AServer(
            agentExecutor = executor,
            agentCard = testAgentCard(AgentCapabilities(pushNotifications = pushNotifications)),
            taskStorage = taskStorage,
            pushConfigStorage = pushConfigStorage,
            pushSender = pushSender,
            idGenerator = NewTaskIdGenerator,
            coroutineScope = agentScope(),
        ) {
            override suspend fun validatePushNotificationConfig(
                config: TaskPushNotificationConfig,
                ctx: ServerCallContext,
            ) {
                validated += config
                if (rejectConfigs) {
                    throw A2AInvalidParamsException("Webhook URL is not allowed")
                }
            }
        }
    }

    @Test
    fun testInlineConfigWithoutIdIsSavedWithTaskIdBeforeAgentStartsAndFirstTaskIsPushed() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()

        val response = server.onSendMessage(sendRequest("ask", pushConfig = webhook), ctx)
        runCurrent()

        val stored = webhook.copy(taskId = NEW_TASK_ID, id = NEW_TASK_ID)
        assertEquals(inputRequiredTask, response)
        assertEquals(listOf(stored), validated)
        assertEquals(listOf(listOf(stored)), configsAtAgentStart)
        assertEquals(listOf(stored), pushStorage.getAll(NEW_TASK_ID))
        assertEquals(pushes(NEW_TASK_ID, listOf(submittedTask, inputRequired("ask"))), pushSender.sent)
    }

    @Test
    fun testInlineConfigKeepsClientIdAndGetsTaskIdAndTenantOfRequest() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()
        val inline = webhook.copy(id = "client-config", taskId = "other-task", tenant = "other-tenant")

        server.onSendMessage(sendRequest("ask", pushConfig = inline, tenant = "tenant-1"), ctx)
        runCurrent()

        assertEquals(
            listOf(inline.copy(taskId = NEW_TASK_ID, tenant = "tenant-1")),
            pushStorage.getAll(NEW_TASK_ID)
        )
        assertEquals(emptyList(), pushStorage.getAll("other-task"))
        assertEquals(pushes("client-config", listOf(submittedTask, inputRequired("ask"))), pushSender.sent)
    }

    @Test
    fun testResentInlineConfigWithoutIdOnFollowUpReplacesConfig() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()

        server.onSendMessage(sendRequest("ask", pushConfig = webhook), ctx)
        server.onSendMessage(sendRequest("again", taskId = NEW_TASK_ID, pushConfig = webhook), ctx)
        runCurrent()

        assertEquals(listOf(webhook.copy(taskId = NEW_TASK_ID, id = NEW_TASK_ID)), pushStorage.getAll(NEW_TASK_ID))
        assertEquals(
            pushes(NEW_TASK_ID, listOf(submittedTask, inputRequired("ask"), inputRequired("again"))),
            pushSender.sent
        )
    }

    @Test
    fun testFollowUpInlineConfigWithNewIdIsSavedForExistingTask() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()
        server.onSendMessage(sendRequest("ask"), ctx)
        val inline = webhook.copy(id = "follow-up-config")

        server.onSendMessage(sendRequest("again", taskId = NEW_TASK_ID, pushConfig = inline), ctx)
        runCurrent()

        val stored = inline.copy(taskId = NEW_TASK_ID)
        assertEquals(listOf(emptyList(), listOf(stored)), configsAtAgentStart)
        assertEquals(listOf(stored), pushStorage.getAll(NEW_TASK_ID))
        assertEquals(pushes("follow-up-config", listOf(inputRequired("again"))), pushSender.sent)
    }

    @Test
    fun testInlineConfigIsIgnoredWhenPushNotificationsAreNotSupported() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer(pushNotifications = false)

        val response = server.onSendMessage(sendRequest("ask", pushConfig = webhook), ctx)
        runCurrent()

        assertEquals(inputRequiredTask, response)
        assertEquals(emptyList(), validated)
        assertEquals(emptyList(), pushStorage.getAll(NEW_TASK_ID))
        assertEquals(emptyList(), pushSender.started)
    }

    @Test
    fun testInlineConfigIsIgnoredWithoutPushConfigStorage() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer(pushConfigStorage = null)

        val response = server.onSendMessage(sendRequest("ask", pushConfig = webhook), ctx)
        runCurrent()

        assertEquals(inputRequiredTask, response)
        assertEquals(emptyList(), validated)
        assertEquals(emptyList(), pushSender.started)
    }

    @Test
    fun testInlineConfigIsDeletedWhenAgentRepliesWithMessage() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()

        val response = server.onSendMessage(sendRequest("reply", pushConfig = webhook), ctx)
        runCurrent()

        assertEquals(agentMessage("reply", taskId = null), response)
        assertEquals(listOf(listOf(webhook.copy(taskId = NEW_TASK_ID, id = NEW_TASK_ID))), configsAtAgentStart)
        assertEquals(emptyList(), pushStorage.getAll(NEW_TASK_ID))
        assertEquals(emptyList(), pushSender.started)
    }

    @Test
    fun testInlineConfigIsDeletedWhenAgentFailsBeforeCreatingTask() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()

        assertFailsWith<IllegalStateException> {
            server.onSendMessage(sendRequest("crash", pushConfig = webhook), ctx)
        }
        runCurrent()

        assertEquals(listOf(listOf(webhook.copy(taskId = NEW_TASK_ID, id = NEW_TASK_ID))), configsAtAgentStart)
        assertEquals(emptyList(), pushStorage.getAll(NEW_TASK_ID))
    }

    @Test
    fun testInlineConfigIsNotSavedWhenMessageValidationFails() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()

        assertFailsWith<A2ATaskNotFoundException> {
            server.onSendMessage(sendRequest("again", taskId = "unknown", pushConfig = webhook), ctx)
        }
        assertFailsWith<A2AUnsupportedOperationException> {
            server.onSendMessage(sendRequest("again", taskId = completedTask.id, pushConfig = webhook), ctx)
        }

        assertEquals(emptyList(), pushStorage.getAll("unknown"))
        assertEquals(emptyList(), pushStorage.getAll(completedTask.id))
        assertEquals(emptyList(), validated)
        assertEquals(emptyList(), executor.executed)
    }

    @Test
    fun testFollowUpMessageReplyDoesNotDeletePushConfigs() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer()
        server.onSendMessage(sendRequest("ask", pushConfig = webhook), ctx)
        val inline = webhook.copy(id = "follow-up-config")

        // A Message for an existing task is rejected; the task exists, so its configs stay.
        assertFailsWith<A2AInvalidAgentResponseException> {
            server.onSendMessage(sendRequest("reply", taskId = NEW_TASK_ID, pushConfig = inline), ctx)
        }
        runCurrent()

        assertEquals(
            listOf(webhook.copy(taskId = NEW_TASK_ID, id = NEW_TASK_ID), inline.copy(taskId = NEW_TASK_ID)),
            pushStorage.getAll(NEW_TASK_ID)
        )
    }

    @Test
    fun testRejectedInlineConfigFailsSendMessageWithoutStartingAgent() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer(rejectConfigs = true)

        assertFailsWith<A2AInvalidParamsException> {
            server.onSendMessage(sendRequest("ask", pushConfig = webhook), ctx)
        }
        runCurrent()

        assertEquals(listOf(webhook.copy(taskId = NEW_TASK_ID, id = NEW_TASK_ID)), validated)
        assertEquals(emptyList(), pushStorage.getAll(NEW_TASK_ID))
        assertEquals(emptyList(), executor.executed)
        assertEquals(null, taskStorage.get(NEW_TASK_ID))
    }

    @Test
    fun testRejectedConfigFailsCreate() = runTest(timeout = TEST_TIMEOUT) {
        val server = createTurnServer(rejectConfigs = true)

        assertFailsWith<A2AInvalidParamsException> {
            server.onCreateTaskPushNotificationConfig(config, ctx)
        }

        assertEquals(listOf(config), validated)
        assertEquals(emptyList(), pushStorage.getAll("task-1"))
    }
}
