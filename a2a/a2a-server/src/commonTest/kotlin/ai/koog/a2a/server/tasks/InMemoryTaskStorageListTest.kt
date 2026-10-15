package ai.koog.a2a.server.tasks

import ai.koog.a2a.exceptions.A2AInvalidParamsException
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
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class InMemoryTaskStorageListTest {
    private lateinit var storage: InMemoryTaskStorage
    private val ctx = ServerCallContext()

    @BeforeTest
    fun setUp() {
        storage = InMemoryTaskStorage()
    }

    @Test
    fun testListReturnsAllTasksNewestFirst() = runTest {
        val oldest = task("t-oldest", timestamp = "2024-01-01T00:00:00Z")
        val newest = task("t-newest", timestamp = "2024-03-01T00:00:00Z")
        val middle = task("t-middle", timestamp = "2024-02-01T00:00:00Z")
        listOf(oldest, newest, middle).forEach { storage.update(it) }

        val response = storage.list(ListTasksRequest(), ctx)

        assertEquals(
            ListTasksResponse(tasks = listOf(newest, middle, oldest), nextPageToken = "", pageSize = 50, totalSize = 3),
            response
        )
    }

    @Test
    fun testListOnEmptyStorage() = runTest {
        assertEquals(
            ListTasksResponse(tasks = emptyList(), nextPageToken = "", pageSize = 50, totalSize = 0),
            storage.list(ListTasksRequest(), ctx)
        )
    }

    @Test
    fun testFilterByContextId() = runTest {
        val inContext = task("t1", contextId = "c1", timestamp = "2024-01-01T00:00:00Z")
        val otherContext = task("t2", contextId = "c2", timestamp = "2024-01-02T00:00:00Z")
        listOf(inContext, otherContext).forEach { storage.update(it) }

        assertEquals(
            ListTasksResponse(listOf(inContext), nextPageToken = "", pageSize = 50, totalSize = 1),
            storage.list(ListTasksRequest(contextId = "c1"), ctx)
        )
    }

    @Test
    fun testFilterByStatus() = runTest {
        val working = task("t1", state = TaskState.TASK_STATE_WORKING, timestamp = "2024-01-01T00:00:00Z")
        val completed = task("t2", state = TaskState.TASK_STATE_COMPLETED, timestamp = "2024-01-02T00:00:00Z")
        listOf(working, completed).forEach { storage.update(it) }

        assertEquals(
            ListTasksResponse(listOf(completed), nextPageToken = "", pageSize = 50, totalSize = 1),
            storage.list(ListTasksRequest(status = TaskState.TASK_STATE_COMPLETED), ctx)
        )
    }

    @Test
    fun testUnspecifiedStatusMeansNoFilter() = runTest {
        val working = task("t1", state = TaskState.TASK_STATE_WORKING, timestamp = "2024-01-01T00:00:00Z")
        val completed = task("t2", state = TaskState.TASK_STATE_COMPLETED, timestamp = "2024-01-02T00:00:00Z")
        listOf(working, completed).forEach { storage.update(it) }

        val expected = ListTasksResponse(listOf(completed, working), nextPageToken = "", pageSize = 50, totalSize = 2)

        assertEquals(expected, storage.list(ListTasksRequest(status = TaskState.TASK_STATE_UNSPECIFIED), ctx))
        assertEquals(expected, storage.list(ListTasksRequest(status = null), ctx))
    }

    @Test
    fun testFilterByStatusTimestampAfterIsInclusiveAndExcludesMissingTimestamps() = runTest {
        val before = task("t1", timestamp = "2024-01-01T00:00:00Z")
        val exactly = task("t2", timestamp = "2024-02-01T00:00:00Z")
        val after = task("t3", timestamp = "2024-03-01T00:00:00Z")
        val noTimestamp = task("t4", timestamp = null)
        listOf(before, exactly, after, noTimestamp).forEach { storage.update(it) }

        assertEquals(
            ListTasksResponse(listOf(after, exactly), nextPageToken = "", pageSize = 50, totalSize = 2),
            storage.list(ListTasksRequest(statusTimestampAfter = Instant.parse("2024-02-01T00:00:00Z")), ctx)
        )
    }

    @Test
    fun testFiltersAreCombined() = runTest {
        val match = task("t1", "c1", TaskState.TASK_STATE_COMPLETED, "2024-03-01T00:00:00Z")
        val wrongContext = task("t2", "c2", TaskState.TASK_STATE_COMPLETED, "2024-03-01T00:00:00Z")
        val wrongStatus = task("t3", "c1", TaskState.TASK_STATE_WORKING, "2024-03-01T00:00:00Z")
        val tooOld = task("t4", "c1", TaskState.TASK_STATE_COMPLETED, "2024-01-01T00:00:00Z")
        listOf(match, wrongContext, wrongStatus, tooOld).forEach { storage.update(it) }

        val response = storage.list(
            ListTasksRequest(
                contextId = "c1",
                status = TaskState.TASK_STATE_COMPLETED,
                statusTimestampAfter = Instant.parse("2024-02-01T00:00:00Z")
            ),
            ctx
        )

        assertEquals(ListTasksResponse(listOf(match), nextPageToken = "", pageSize = 50, totalSize = 1), response)
    }

    @Test
    fun testTiesAreBrokenByDescendingIdAndMissingTimestampsGoLast() = runTest {
        val tieA = task("a", timestamp = "2024-02-01T00:00:00Z")
        val tieB = task("b", timestamp = "2024-02-01T00:00:00Z")
        val newest = task("z-newest", timestamp = "2024-03-01T00:00:00Z")
        val missingX = task("x-missing", timestamp = null)
        val missingY = task("y-missing", timestamp = null)
        listOf(missingX, tieA, missingY, newest, tieB).forEach { storage.update(it) }

        val response = storage.list(ListTasksRequest(), ctx)

        assertEquals(listOf(newest, tieB, tieA, missingY, missingX), response.tasks)
    }

    @Test
    fun testDefaultPageSizeIsFifty() = runTest {
        (1..60).forEach { storage.update(task("task-${pad(it)}", timestamp = "2024-01-${pad(it.coerceAtMost(28))}T00:00:00Z")) }

        val response = storage.list(ListTasksRequest(), ctx)

        assertEquals(50, response.tasks.size)
        assertEquals(50, response.pageSize)
        assertEquals(60, response.totalSize)
        assertTrue(response.nextPageToken.isNotEmpty())
    }

    @Test
    fun testPagingWithTokensCoversEveryTaskExactlyOnce() = runTest {
        val tasks = (1..20).map { index ->
            // Several ties and several tasks without a timestamp
            val timestamp = if (index % 5 == 0) null else "2024-01-${pad(index / 3 + 1)}T00:00:00Z"
            task("task-${pad(index)}", timestamp = timestamp)
        }
        tasks.forEach { storage.update(it) }
        val expectedOrder = storage.list(ListTasksRequest(pageSize = 100), ctx).tasks
        assertEquals(20, expectedOrder.size)

        val pages = mutableListOf<ListTasksResponse>()
        var token: String? = null
        do {
            val page = storage.list(ListTasksRequest(pageSize = 7, pageToken = token), ctx)
            pages += page
            token = page.nextPageToken.takeIf { it.isNotEmpty() }
        } while (token != null)

        assertEquals(listOf(7, 7, 6), pages.map { it.tasks.size })
        assertEquals(listOf(7, 7, 7), pages.map { it.pageSize })
        assertEquals(listOf(20, 20, 20), pages.map { it.totalSize })
        assertEquals("", pages.last().nextPageToken)
        assertEquals(expectedOrder, pages.flatMap { it.tasks })
    }

    @Test
    fun testExactMultipleOfPageSizeEndsWithEmptyToken() = runTest {
        val tasks = (1..4).map { task("t$it", timestamp = "2024-01-0${it}T00:00:00Z") }
        tasks.forEach { storage.update(it) }

        val first = storage.list(ListTasksRequest(pageSize = 2), ctx)
        val second = storage.list(ListTasksRequest(pageSize = 2, pageToken = first.nextPageToken), ctx)

        assertEquals(tasks.reversed().take(2), first.tasks)
        assertEquals(tasks.reversed().drop(2), second.tasks)
        assertEquals("", second.nextPageToken)
    }

    @Test
    fun testPageTokenSurvivesDeletionOfTheLastReturnedTask() = runTest {
        val tasks = (1..4).map { task("t$it", timestamp = "2024-01-0${it}T00:00:00Z") }
        tasks.forEach { storage.update(it) }

        val first = storage.list(ListTasksRequest(pageSize = 2), ctx)
        storage.delete(first.tasks.last().id)
        val second = storage.list(ListTasksRequest(pageSize = 2, pageToken = first.nextPageToken), ctx)

        assertEquals(listOf(tasks[1], tasks[0]), second.tasks)
        assertEquals(3, second.totalSize)
    }

    @Test
    fun testTotalSizeIsFilteredCountBeforePagination() = runTest {
        (1..5).forEach { storage.update(task("c1-$it", contextId = "c1", timestamp = "2024-01-0${it}T00:00:00Z")) }
        (1..3).forEach { storage.update(task("c2-$it", contextId = "c2", timestamp = "2024-02-0${it}T00:00:00Z")) }

        val response = storage.list(ListTasksRequest(contextId = "c1", pageSize = 2), ctx)

        assertEquals(2, response.tasks.size)
        assertEquals(5, response.totalSize)
        assertEquals(2, response.pageSize)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testInvalidPageTokenIsInvalidParams() = runTest {
        storage.update(task("t1", timestamp = "2024-01-01T00:00:00Z"))

        val invalidTokens = listOf(
            "not a token!",
            Base64.UrlSafe.encode("no-separator".encodeToByteArray()),
            Base64.UrlSafe.encode("not-a-timestamp|t1".encodeToByteArray()),
        )

        invalidTokens.forEach { token ->
            assertFailsWith<A2AInvalidParamsException>(token) {
                storage.list(ListTasksRequest(pageToken = token), ctx)
            }
        }
    }

    @Test
    fun testNonPositivePageSizeIsInvalidParams() = runTest {
        assertFailsWith<A2AInvalidParamsException> {
            storage.list(ListTasksRequest(pageSize = 0), ctx)
        }
    }

    @Test
    fun testArtifactsAreOmittedUnlessRequested() = runTest {
        val withArtifacts = task("t1", timestamp = "2024-01-01T00:00:00Z", artifacts = listOf(artifact()))
        storage.update(withArtifacts)

        val withoutArtifacts = ListTasksResponse(
            listOf(withArtifacts.copy(artifacts = null)),
            nextPageToken = "",
            pageSize = 50,
            totalSize = 1
        )

        assertEquals(withoutArtifacts, storage.list(ListTasksRequest(includeArtifacts = null), ctx))
        assertEquals(withoutArtifacts, storage.list(ListTasksRequest(includeArtifacts = false), ctx))
        assertEquals(
            ListTasksResponse(listOf(withArtifacts), nextPageToken = "", pageSize = 50, totalSize = 1),
            storage.list(ListTasksRequest(includeArtifacts = true), ctx)
        )
    }

    @Test
    fun testHistoryLengthProjection() = runTest {
        val messages = (1..3).map { message("m$it") }
        val withHistory = task("t1", timestamp = "2024-01-01T00:00:00Z", history = messages)
        storage.update(withHistory)

        fun expected(history: List<Message>?) = ListTasksResponse(
            listOf(withHistory.copy(history = history)),
            nextPageToken = "",
            pageSize = 50,
            totalSize = 1
        )

        assertEquals(expected(messages), storage.list(ListTasksRequest(historyLength = null), ctx))
        assertEquals(expected(null), storage.list(ListTasksRequest(historyLength = 0), ctx))
        assertEquals(expected(messages.takeLast(2)), storage.list(ListTasksRequest(historyLength = 2), ctx))
    }

    @Test
    fun testGetByRequestAlwaysIncludesArtifacts() = runTest {
        val stored = task(
            "t1",
            timestamp = "2024-01-01T00:00:00Z",
            history = (1..3).map { message("m$it") },
            artifacts = listOf(artifact())
        )
        storage.update(stored)

        assertEquals(stored, storage.get(GetTaskRequest(id = "t1"), ctx))
    }

    @Test
    fun testGetByRequestAppliesHistoryLength() = runTest {
        val messages = (1..3).map { message("m$it") }
        val stored = task("t1", timestamp = "2024-01-01T00:00:00Z", history = messages)
        storage.update(stored)

        assertEquals(stored.copy(history = null), storage.get(GetTaskRequest(id = "t1", historyLength = 0), ctx))
        assertEquals(
            stored.copy(history = messages.takeLast(1)),
            storage.get(GetTaskRequest(id = "t1", historyLength = 1), ctx)
        )
    }

    @Test
    fun testGetByRequestUnknownTaskIsNull() = runTest {
        assertNull(storage.get(GetTaskRequest(id = "unknown"), ctx))
    }

    @Test
    fun testInternalGetDefaultsReturnFullTask() = runTest {
        val stored = task(
            "t1",
            timestamp = "2024-01-01T00:00:00Z",
            history = listOf(message("m1")),
            artifacts = listOf(artifact())
        )
        storage.update(stored)

        assertEquals(stored, storage.get("t1"))
        assertEquals(listOf(stored), storage.getAll(listOf("t1")))
        assertEquals(listOf(stored), storage.getByContext("context-1"))
        assertEquals(stored.copy(history = null, artifacts = null), storage.get("t1", historyLength = 0, includeArtifacts = false))
    }

    private fun task(
        id: String,
        contextId: String = "context-1",
        state: TaskState = TaskState.TASK_STATE_COMPLETED,
        timestamp: String?,
        history: List<Message>? = null,
        artifacts: List<Artifact>? = null,
    ) = Task(
        id = id,
        contextId = contextId,
        status = TaskStatus(state = state, timestamp = timestamp?.let { Instant.parse(it) }),
        history = history,
        artifacts = artifacts,
    )

    private fun pad(number: Int) = number.toString().padStart(2, '0')

    private fun message(id: String) = Message(messageId = id, role = Role.ROLE_USER, parts = listOf(TextPart(id)))

    private fun artifact() = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("content")))
}
