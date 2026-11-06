package ai.koog.a2a.server

import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.messages.MessageStorage
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.STATUS_ONLY
import ai.koog.a2a.server.tasks.TaskProjection
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.StateKey

// Authorization-aware storages for the server tests: data belongs to the owner of the call that created it and is
// visible to that owner only. They record the context of every call, so tests can check which context the server uses.

/** The owner of a call, read by the owner-scoped storages. */
internal val OWNER_KEY = StateKey<String>("owner")

/** Tells apart the calls of the same owner, e.g. two requests of one user. */
private val CALL_KEY = StateKey<String>("call")

/** A call context of [owner]; contexts with different [call]s are different requests of that owner. */
internal fun ownerContext(owner: String, call: String = owner): ServerCallContext =
    ServerCallContext(state = mapOf(OWNER_KEY to owner, CALL_KEY to call))

private val ServerCallContext.owner: String? get() = getFromStateOrNull(OWNER_KEY)

/** A call made to an owner-scoped storage: what was done, and with which context. */
internal data class StorageCall(val operation: String, val callContext: ServerCallContext) {
    override fun toString(): String = "StorageCall($operation, ${callContext.getFromStateOrNull(CALL_KEY)})"
}

/** A [TaskStorage] in which a task is visible only to the owner of the context that created it. */
internal class OwnerScopedTaskStorage : TaskStorage {
    private val delegate = InMemoryTaskStorage()
    private val owners = mutableMapOf<String, String?>()

    /** Every call, in order. */
    val calls = mutableListOf<StorageCall>()

    private fun isVisible(taskId: String, callContext: ServerCallContext): Boolean =
        taskId in owners && owners[taskId] == callContext.owner

    override suspend fun get(request: GetTaskRequest, callContext: ServerCallContext): Task? {
        calls += StorageCall("get ${request.id}", callContext)
        return if (isVisible(request.id, callContext)) delegate.get(request, callContext) else null
    }

    /** Recorded as `get <id> status` for [STATUS_ONLY], the projection of the server's bookkeeping reads. */
    override suspend fun get(taskId: String, projection: TaskProjection, callContext: ServerCallContext): Task? {
        calls += StorageCall("get $taskId ${if (projection == STATUS_ONLY) "status" else "$projection"}", callContext)
        return if (isVisible(taskId, callContext)) delegate.get(taskId, projection, callContext) else null
    }

    /** Filters the page of the in-memory storage, so paging isn't owner-scoped; enough for the tests. */
    override suspend fun list(request: ListTasksRequest, callContext: ServerCallContext): ListTasksResponse {
        calls += StorageCall("list", callContext)
        val page = delegate.list(request, callContext)
        val visible = page.tasks.filter { isVisible(it.id, callContext) }
        return page.copy(tasks = visible, totalSize = visible.size)
    }

    override suspend fun update(event: TaskEvent, callContext: ServerCallContext) {
        calls += StorageCall("update ${event.describe()}", callContext)
        if (event is Task && event.id !in owners) owners[event.id] = callContext.owner
        delegate.update(event, callContext)
    }

    override suspend fun appendHistory(taskId: String, message: Message, callContext: ServerCallContext) {
        calls += StorageCall("appendHistory ${message.messageId}", callContext)
        if (isVisible(taskId, callContext)) delegate.appendHistory(taskId, message, callContext)
    }

    /** Unscoped, like the in-memory storage: a service method. */
    override suspend fun getAll(taskIds: List<String>): List<Task> = delegate.getAll(taskIds)

    /** Unscoped, like the in-memory storage: a service method. */
    override suspend fun deleteAll(taskIds: List<String>) {
        delegate.deleteAll(taskIds)
        owners -= taskIds.toSet()
    }

    /** The writes among [calls]. */
    fun writes(): List<StorageCall> = calls.filterNot { it.operation.startsWith("get ") || it.operation == "list" }

    private fun TaskEvent.describe(): String = when (this) {
        is Task -> "Task ${status.state}"
        is TaskStatusUpdateEvent -> "status ${status.state}"
        is TaskArtifactUpdateEvent -> "artifact ${artifact.artifactId}"
    }
}

/** A [MessageStorage] that keeps the messages of every owner apart. */
internal class OwnerScopedMessageStorage : MessageStorage {
    private val byOwner = mutableMapOf<String?, InMemoryMessageStorage>()

    private fun storage(callContext: ServerCallContext) =
        byOwner.getOrPut(callContext.owner) { InMemoryMessageStorage() }

    override suspend fun save(message: Message, callContext: ServerCallContext) =
        storage(callContext).save(message, callContext)

    override suspend fun getByContext(contextId: String, callContext: ServerCallContext): List<Message> =
        storage(callContext).getByContext(contextId, callContext)

    override suspend fun deleteByContext(contextId: String, callContext: ServerCallContext) =
        storage(callContext).deleteByContext(contextId, callContext)

    override suspend fun replaceByContext(contextId: String, messages: List<Message>, callContext: ServerCallContext) =
        storage(callContext).replaceByContext(contextId, messages, callContext)
}

/**
 * A [PushNotificationConfigStorage] in which a config is visible only to the owner of the context that saved it;
 * [getAll], the dispatch read, returns the configs of every owner. Records the calls that take a context.
 */
internal class OwnerScopedPushConfigStorage : PushNotificationConfigStorage {
    private val delegate = InMemoryPushNotificationConfigStorage()
    private val owners = mutableMapOf<Pair<String?, String?>, String?>()

    /** Every call that takes a context, in order. */
    val calls = mutableListOf<StorageCall>()

    private fun isVisible(taskId: String, id: String, callContext: ServerCallContext): Boolean =
        (taskId to id) in owners && owners[taskId to id] == callContext.owner

    override suspend fun save(
        config: TaskPushNotificationConfig,
        callContext: ServerCallContext,
    ): TaskPushNotificationConfig {
        calls += StorageCall("save ${config.taskId}/${config.id}", callContext)
        owners[config.taskId to config.id] = callContext.owner
        return delegate.save(config, callContext)
    }

    override suspend fun get(
        request: GetTaskPushNotificationConfigRequest,
        callContext: ServerCallContext,
    ): TaskPushNotificationConfig? {
        calls += StorageCall("get ${request.taskId}/${request.id}", callContext)
        return if (isVisible(request.taskId, request.id, callContext)) delegate.get(request, callContext) else null
    }

    override suspend fun list(
        request: ListTaskPushNotificationConfigsRequest,
        callContext: ServerCallContext,
    ): ListTaskPushNotificationConfigsResponse {
        calls += StorageCall("list ${request.taskId}", callContext)
        val all = delegate.list(request, callContext)
        return all.copy(configs = all.configs.filter { isVisible(request.taskId, it.id.orEmpty(), callContext) })
    }

    override suspend fun delete(request: DeleteTaskPushNotificationConfigRequest, callContext: ServerCallContext) {
        calls += StorageCall("delete ${request.taskId}/${request.id}", callContext)
        if (isVisible(request.taskId, request.id, callContext)) delegate.delete(request, callContext)
    }

    override suspend fun getAll(taskId: String): List<TaskPushNotificationConfig> = delegate.getAll(taskId)

    /** Deletes the configs of [taskId] that the owner of [callContext] saved. */
    override suspend fun deleteAll(taskId: String, callContext: ServerCallContext) {
        calls += StorageCall("deleteAll $taskId", callContext)
        delegate.getAll(taskId)
            .filter { isVisible(taskId, it.id.orEmpty(), callContext) }
            .forEach { delegate.delete(DeleteTaskPushNotificationConfigRequest(taskId, it.id.orEmpty()), callContext) }
    }
}
