package ai.koog.a2a.server.tasks

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.agents.lock.RWLock
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * In-memory implementation of [TaskStorage] using a thread-safe map.
 *
 * Stores tasks in memory, indexed by ID and context ID, with concurrency safety through a read-write lock.
 * [get] with a [GetTaskRequest] and [list] ignore the request's tenant and the [ServerCallContext].
 *
 * [list] ordering: by status timestamp, newest first; tasks without one go last; ties are broken by task id in
 * descending order (compared as strings), so the order is total. The page token holds the sort key of the last task
 * of the previous page, so adding or removing tasks between calls doesn't shift the pages. A status update changes a
 * task's sort key, though, so a task updated between calls may move across the cursor and be skipped or repeated.
 */
@OptIn(InternalA2AApi::class)
public class InMemoryTaskStorage : TaskStorage {
    private val tasks = mutableMapOf<String, Task>()
    private val tasksByContext = mutableMapOf<String, MutableSet<String>>()
    private val rwLock = RWLock()

    override suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task? = rwLock.withReadLock {
        tasks[request.id]?.project(request.historyLength, includeArtifacts = true)
    }

    override suspend fun list(request: ListTasksRequest, context: ServerCallContext): ListTasksResponse =
        rwLock.withReadLock {
            val pageSize = request.pageSize ?: DEFAULT_PAGE_SIZE
            if (pageSize < 1) {
                throw A2AInvalidParamsException("pageSize must be positive, but was $pageSize")
            }
            val cursor = request.pageToken?.takeIf { it.isNotEmpty() }?.let(::decodePageToken)

            val contextFilter = request.contextId
            val statusFilter = request.status?.takeIf { it != TaskState.TASK_STATE_UNSPECIFIED }
            val timestampFilter = request.statusTimestampAfter
            val matching = tasks.values
                .filter { task ->
                    (contextFilter == null || task.contextId == contextFilter) &&
                        (statusFilter == null || task.status.state == statusFilter) &&
                        (timestampFilter == null || task.status.timestamp?.let { it >= timestampFilter } == true)
                }
                .sortedWith(TASK_ORDER)

            val remaining = if (cursor == null) {
                matching
            } else {
                matching.filter { TASK_ORDER.compare(it, cursor) > 0 }
            }
            val page = remaining.take(pageSize)
            val nextPageToken = if (remaining.size > page.size) encodePageToken(page.last()) else ""

            ListTasksResponse(
                tasks = page.map { it.project(request.historyLength, request.includeArtifacts ?: false) },
                nextPageToken = nextPageToken,
                pageSize = pageSize,
                totalSize = matching.size
            )
        }

    override suspend fun get(
        taskId: String,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): Task? = rwLock.withReadLock {
        tasks[taskId]?.project(historyLength, includeArtifacts)
    }

    override suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): List<Task> = rwLock.withReadLock {
        taskIds.mapNotNull { taskId ->
            get(taskId, historyLength, includeArtifacts)
        }
    }

    override suspend fun getByContext(
        contextId: String,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): List<Task> = rwLock.withReadLock {
        val contextTaskIds = tasksByContext[contextId] ?: emptySet()
        contextTaskIds.mapNotNull { taskId ->
            get(taskId, historyLength, includeArtifacts)
        }
    }

    override suspend fun update(event: TaskEvent): Unit = rwLock.withWriteLock {
        when (event) {
            is Task -> {
                val oldTask = tasks[event.id]

                if (oldTask != null && event.contextId != oldTask.contextId) {
                    throw TaskOperationException("Cannot change context for existing task: ${event.id}")
                }

                tasks[event.id] = event

                event.contextId?.let { contextId ->
                    tasksByContext.getOrPut(contextId) { mutableSetOf() }.add(event.id)
                }
            }

            is TaskStatusUpdateEvent -> {
                val existingTask = tasks[event.taskId]
                    ?: throw TaskOperationException("Cannot update status for non-existing task: ${event.taskId}")

                val updatedTask = existingTask.copy(
                    status = event.status,
                    history = existingTask.status.message
                        ?.let { existingTask.history.plusIfAbsent(it) }
                        ?: existingTask.history,
                    metadata = existingTask.metadata
                        ?.let { JsonObject(it + event.metadata.orEmpty()) }
                        ?: event.metadata,
                )

                tasks[event.taskId] = updatedTask
            }

            is TaskArtifactUpdateEvent -> {
                val existingTask = tasks[event.taskId]
                    ?: throw TaskOperationException("Cannot update artifact for non-existing task: ${event.taskId}")

                val currentArtifacts = existingTask.artifacts?.toMutableList() ?: mutableListOf()
                val existingArtifactIndex = currentArtifacts.indexOfFirst { it.artifactId == event.artifact.artifactId }

                if (existingArtifactIndex >= 0) {
                    val existingArtifact = currentArtifacts[existingArtifactIndex]

                    currentArtifacts[existingArtifactIndex] = if (event.append == true) {
                        existingArtifact.copy(parts = existingArtifact.parts + event.artifact.parts)
                    } else {
                        event.artifact
                    }
                } else {
                    currentArtifacts.add(event.artifact)
                }

                val updatedTask = existingTask.copy(
                    artifacts = currentArtifacts,
                    metadata = existingTask.metadata
                        ?.let { JsonObject(it + event.metadata.orEmpty()) }
                        ?: event.metadata
                )

                tasks[event.taskId] = updatedTask
            }
        }
    }

    override suspend fun delete(taskId: String): Unit = rwLock.withWriteLock {
        tasks.remove(taskId)?.let { task ->
            removeFromContextIndex(task)
        }
    }

    override suspend fun deleteAll(taskIds: List<String>): Unit = rwLock.withWriteLock {
        taskIds.forEach { taskId ->
            tasks.remove(taskId)?.let { task ->
                removeFromContextIndex(task)
            }
        }
    }

    /**
     * The token is the base64url encoding of `<status timestamp in ISO-8601 or empty>|<task id>` of the last task of
     * the page; the next page holds the tasks that sort strictly after it.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun encodePageToken(lastTask: Task): String =
        Base64.UrlSafe.encode("${lastTask.status.timestamp?.toString().orEmpty()}|${lastTask.id}".encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodePageToken(token: String): Task {
        try {
            val decoded = Base64.UrlSafe.decode(token).decodeToString(throwOnInvalidSequence = true)
            val separator = decoded.indexOf('|')
            require(separator >= 0) { "missing separator" }
            val timestamp = decoded.substring(0, separator).takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
            val id = decoded.substring(separator + 1)
            // Only the sort key matters for comparison.
            return Task(id = id, status = TaskStatus(state = TaskState.TASK_STATE_UNSPECIFIED, timestamp = timestamp))
        } catch (e: Exception) {
            throw A2AInvalidParamsException("Invalid page token: '$token'")
        }
    }

    private fun removeFromContextIndex(task: Task) {
        val contextId = task.contextId ?: return
        tasksByContext[contextId]?.remove(task.id)
        if (tasksByContext[contextId]?.isEmpty() == true) {
            tasksByContext.remove(contextId)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50

        /** Newest status timestamp first; tasks without one go last; ties are broken by descending task id. */
        val TASK_ORDER: Comparator<Task> = Comparator { a, b ->
            val timestampA = a.status.timestamp
            val timestampB = b.status.timestamp
            when {
                timestampA != null && timestampB != null && timestampA != timestampB -> timestampB.compareTo(timestampA)
                timestampA == null && timestampB != null -> 1
                timestampA != null && timestampB == null -> -1
                else -> b.id.compareTo(a.id)
            }
        }
    }
}
