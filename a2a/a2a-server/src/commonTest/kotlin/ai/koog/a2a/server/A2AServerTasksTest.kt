package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class A2AServerTasksTest {
    private val ctx = ServerCallContext()
    private lateinit var storage: InMemoryTaskStorage
    private lateinit var server: A2AServer

    private val messages = (1..3).map {
        Message(messageId = "m$it", role = Role.ROLE_USER, parts = listOf(TextPart("message $it")))
    }
    private val artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("content")))
    private val task = Task(
        id = "task-1",
        contextId = "context-1",
        status = TaskStatus(
            state = TaskState.TASK_STATE_COMPLETED,
            timestamp = Instant.parse("2024-01-01T00:00:00Z")
        ),
        history = messages,
        artifacts = listOf(artifact),
    )

    @BeforeTest
    fun setUp() = runTest {
        storage = InMemoryTaskStorage()
        storage.update(task)
        server = A2AServer(
            agentExecutor = UnusedAgentExecutor,
            agentCard = testAgentCard(),
            taskStorage = storage,
        )
    }

    @Test
    fun testGetTaskIncludesArtifacts() = runTest {
        assertEquals(task, server.onGetTask(GetTaskRequest(id = "task-1"), ctx))
    }

    @Test
    fun testGetTaskAppliesHistoryLength() = runTest {
        assertEquals(
            task.copy(history = messages.takeLast(1)),
            server.onGetTask(GetTaskRequest(id = "task-1", historyLength = 1), ctx)
        )
        assertEquals(
            task.copy(history = null),
            server.onGetTask(GetTaskRequest(id = "task-1", historyLength = 0), ctx)
        )
    }

    @Test
    fun testGetTaskRejectsUnknownTaskAndNegativeHistoryLength() = runTest {
        assertFailsWith<A2ATaskNotFoundException> {
            server.onGetTask(GetTaskRequest(id = "unknown"), ctx)
        }
        assertFailsWith<A2AInvalidParamsException> {
            server.onGetTask(GetTaskRequest(id = "task-1", historyLength = -1), ctx)
        }
    }

    @Test
    fun testListTasksDelegatesToStorage() = runTest {
        assertEquals(
            ListTasksResponse(
                tasks = listOf(task.copy(artifacts = null)),
                nextPageToken = "",
                pageSize = 50,
                totalSize = 1
            ),
            server.onListTasks(ListTasksRequest(contextId = "context-1"), ctx)
        )
    }

    @Test
    fun testListTasksValidatesPageSizeBoundsAndHistoryLength() = runTest {
        listOf(0, -1, 101).forEach { pageSize ->
            assertFailsWith<A2AInvalidParamsException>("pageSize=$pageSize") {
                server.onListTasks(ListTasksRequest(pageSize = pageSize), ctx)
            }
        }
        assertFailsWith<A2AInvalidParamsException> {
            server.onListTasks(ListTasksRequest(historyLength = -1), ctx)
        }

        listOf(1, 100).forEach { pageSize ->
            assertEquals(pageSize, server.onListTasks(ListTasksRequest(pageSize = pageSize), ctx).pageSize)
        }
    }
}
