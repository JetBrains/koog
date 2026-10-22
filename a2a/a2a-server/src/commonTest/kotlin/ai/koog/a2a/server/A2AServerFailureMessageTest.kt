package ai.koog.a2a.server

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidAgentResponseException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.turn.Turn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** [A2AServer.failureMessage]: the status message of a task the server marks as failed. */
@OptIn(InternalA2AApi::class)
class A2AServerFailureMessageTest {
    private companion object {
        private const val GENERIC_MESSAGE_ID = "generic-message-id"
    }

    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private class FailureMessageCall(val taskId: String, val contextId: String, val error: Throwable)

    /** A server whose [failureMessage] records its calls and delegates to [onFailure]. */
    private class CustomFailureServer(
        executor: ScriptedAgentExecutor,
        taskStorage: InMemoryTaskStorage,
        scope: CoroutineScope,
        private val onFailure: suspend (taskId: String, contextId: String, error: Throwable) -> Message,
    ) : A2AServer(
        agentExecutor = executor,
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator(messageId = GENERIC_MESSAGE_ID),
        coroutineScope = scope,
    ) {
        val calls = mutableListOf<FailureMessageCall>()

        override suspend fun failureMessage(taskId: String, contextId: String, error: Throwable): Message {
            calls += FailureMessageCall(taskId, contextId, error)
            return onFailure(taskId, contextId, error)
        }

        suspend fun turn(taskId: String): Turn? = turnManager.getTurn(taskId)
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript>,
        cancelHandler: CancelHandler? = null,
        scope: CoroutineScope = backgroundScope,
        onFailure: suspend (taskId: String, contextId: String, error: Throwable) -> Message = { _, _, _ -> custom },
    ) = CustomFailureServer(ScriptedAgentExecutor(scripts, cancelHandler), taskStorage, scope, onFailure)

    /** Returned by the override with wrong ids and role, which the server replaces. */
    private val custom = Message(
        messageId = "custom-message-id",
        role = Role.ROLE_USER,
        parts = listOf(TextPart("Model quota exceeded, retry later")),
        taskId = "other-task",
        contextId = "other-context",
        metadata = buildJsonObject { put("correlationId", JsonPrimitive("abc-123")) },
    )

    private val expectedCustom = custom.copy(role = Role.ROLE_AGENT, taskId = TASK_ID, contextId = CONTEXT_ID)

    private val generic = Message(
        messageId = GENERIC_MESSAGE_ID,
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("Task failed due to an internal error")),
        contextId = CONTEXT_ID,
        taskId = TASK_ID,
    )

    private val workingTask = task(TaskState.TASK_STATE_WORKING)

    /** Creates the task, then fails with [failure]. */
    private fun failingScript(failure: suspend () -> Unit) = AgentScript { _, processor ->
        processor.sendTaskEvent(workingTask)
        failure()
    }

    private val crashScript = failingScript { throw IllegalStateException("Agent crashed") }

    /** The FAILED status the server stored, with [message]; the timestamp is taken from the storage. */
    private suspend fun storedFailedStatus(message: Message): TaskStatus {
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        return TaskStatus(state = TaskState.TASK_STATE_FAILED, message = message, timestamp = stored.status.timestamp)
    }

    @Test
    fun testCustomFailureMessageIsStoredAndStreamedWithServerIds() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("crash" to crashScript))

        val events = server.onSendMessageStreaming(request("crash"), ctx).toList()

        val failedStatus = storedFailedStatus(expectedCustom)
        assertEquals(failedStatus, assertNotNull(taskStorage.get(TASK_ID)).status)
        assertEquals(
            listOf<Event>(
                workingTask.copy(history = listOf(request("crash").message.copy(taskId = TASK_ID))),
                TaskStatusUpdateEvent(taskId = TASK_ID, contextId = CONTEXT_ID, status = failedStatus),
            ),
            events,
        )
        val call = server.calls.single()
        assertEquals(TASK_ID to CONTEXT_ID, call.taskId to call.contextId)
        assertEquals(IllegalStateException::class, call.error::class)
        assertEquals("Agent crashed", call.error.message)
    }

    @Test
    fun testThrowingFailureMessageFallsBackToGenericMessage() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("crash" to crashScript)) { _, _, _ -> error("Hook failed") }

        val response = server.onSendMessage(request("crash"), ctx)

        val stored = assertNotNull(taskStorage.get(TASK_ID))
        assertEquals(storedFailedStatus(generic), stored.status)
        assertEquals(stored, response)
        assertEquals(1, server.calls.size)
    }

    @Test
    fun testRejectedEventUsesCustomFailureMessage() = runTest(timeout = TEST_TIMEOUT) {
        val invalid = TaskStatusUpdateEvent(
            taskId = "other-task",
            contextId = CONTEXT_ID,
            status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED, timestamp = TIMESTAMP),
        )
        val invalidScript = AgentScript { _, processor ->
            processor.sendTaskEvent(workingTask)
            processor.sendTaskEvent(invalid)
        }
        val server = createServer(mapOf("invalid" to invalidScript))

        assertFailsWith<A2AInvalidAgentResponseException> { server.onSendMessage(request("invalid"), ctx) }

        assertEquals(storedFailedStatus(expectedCustom), assertNotNull(taskStorage.get(TASK_ID)).status)
        assertIs<InvalidEventException>(server.calls.single().error)
    }

    @Test
    fun testAgentOwnCancellationIsPassedUnwrapped() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("timeout" to failingScript { withTimeout(1) { awaitCancellation() } }))

        server.onSendMessage(request("timeout"), ctx)

        assertEquals(storedFailedStatus(expectedCustom), assertNotNull(taskStorage.get(TASK_ID)).status)
        assertIs<CancellationException>(server.calls.single().error)
    }

    @Test
    fun testFailureMessageNotCalledForCompletedOrRequestError() = runTest(timeout = TEST_TIMEOUT) {
        val completed = workingTask.copy(status = workingTask.status.copy(state = TaskState.TASK_STATE_COMPLETED))
        val error = A2AContentTypeNotSupportedException("Unsupported content type")
        val server = createServer(
            mapOf(
                "complete" to AgentScript { _, processor -> processor.sendTaskEvent(completed) },
                "reject" to AgentScript { _, _ -> throw error },
            )
        )

        server.onSendMessage(request("complete"), ctx)
        assertFailsWith<A2AContentTypeNotSupportedException> { server.onSendMessage(request("reject"), ctx) }

        assertEquals(emptyList(), server.calls.map { it.error })
    }

    @Test
    fun testServerShutdownInterruptsHangingFailureMessage() = runTest(timeout = TEST_TIMEOUT) {
        val hookEntered = CompletableDeferred<Turn>()
        lateinit var server: CustomFailureServer
        server = createServer(mapOf("crash" to crashScript)) { taskId, _, _ ->
            hookEntered.complete(assertNotNull(server.turn(taskId)))
            awaitCancellation()
        }
        val response = backgroundScope.async { runCatching { server.onSendMessage(request("crash"), ctx) } }
        val turn = hookEntered.await()

        server.cancel()
        turn.join()

        // The shutdown ends the requester's view before the turn marks the task failed.
        assertEquals(storedFailedStatus(generic), assertNotNull(taskStorage.get(TASK_ID)).status)
        assertEquals(
            A2AInternalErrorException::class to "Server is shut down",
            response.await().exceptionOrNull()?.let { it::class to it.message },
        )
    }

    @Test
    fun testCancelTaskWhileFailureMessageRunsLeavesTaskCanceled() = runTest(timeout = TEST_TIMEOUT) {
        val hookEntered = CompletableDeferred<Turn>()
        val hookRelease = CompletableDeferred<Unit>()
        lateinit var server: CustomFailureServer
        // A no-op handler: the server forces the task to canceled.
        server = createServer(mapOf("crash" to crashScript), cancelHandler = { _, _ -> }) { taskId, _, _ ->
            hookEntered.complete(assertNotNull(server.turn(taskId)))
            hookRelease.await()
            custom
        }
        val response = backgroundScope.async { server.onSendMessage(request("crash"), ctx) }
        val turn = hookEntered.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        hookRelease.complete(Unit)
        turn.join()

        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        assertEquals(canceled.copy(history = stored.history), stored)
        assertEquals(stored, response.await())
    }
}
