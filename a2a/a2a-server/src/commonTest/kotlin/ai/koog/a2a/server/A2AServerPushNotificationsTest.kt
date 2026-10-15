package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2APushNotificationNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.turn.IdGenerator
import ai.koog.a2a.server.turn.UuidIdGenerator
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class A2AServerPushNotificationsTest {
    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushStorage: InMemoryPushNotificationConfigStorage
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
}
