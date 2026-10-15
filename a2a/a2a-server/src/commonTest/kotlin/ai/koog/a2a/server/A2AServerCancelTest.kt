package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.IdGenerator
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * CancelTask at the [A2AServer] level: the executor's cancellation, the server-forced CANCELED state, and the effect on
 * subscribers and on the requester of the cancelled turn.
 */
class A2AServerCancelTest {
    private companion object {
        private const val TASK_ID = "task-1"
        private const val CONTEXT_ID = "context-1"
        private val TIMESTAMP = Instant.parse("2025-01-01T10:00:00Z")
        private val TEST_TIMEOUT = 30.seconds
    }

    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private fun interface Script {
        suspend fun run(processor: SessionEventProcessor)
    }

    private fun interface CancelHandler {
        suspend fun cancel(processor: SessionEventProcessor, agentJob: Deferred<Unit>?)
    }

    private class CancelAgentExecutor(
        private val scripts: Map<String, Script>,
        private val cancelHandler: CancelHandler,
    ) : AgentExecutor {
        override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: SessionEventProcessor) {
            val text = context.params.message.parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }
            (scripts[text] ?: error("No script for '$text'")).run(eventProcessor)
        }

        override suspend fun cancel(
            context: RequestContext<CancelTaskRequest>,
            eventProcessor: SessionEventProcessor,
            agentJob: Deferred<Unit>?,
        ) = cancelHandler.cancel(eventProcessor, agentJob)
    }

    private object FixedIdGenerator : IdGenerator {
        override suspend fun generateContextId(message: Message): String = CONTEXT_ID
        override suspend fun generateTaskId(message: Message): String = TASK_ID
        override suspend fun generateMessageId(taskId: String, contextId: String): String = "generated-message-id"
    }

    private val agentCard = AgentCard(
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
        capabilities = AgentCapabilities(streaming = true),
    )

    private fun TestScope.createServer(
        scripts: Map<String, Script>,
        cancelHandler: CancelHandler,
    ): A2AServer = A2AServer(
        agentExecutor = CancelAgentExecutor(scripts, cancelHandler),
        agentCard = agentCard,
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator,
        coroutineScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job)),
    )

    private fun request(text: String) = SendMessageRequest(
        message = Message(
            messageId = "user-$text",
            role = Role.ROLE_USER,
            parts = listOf(TextPart(text)),
            contextId = CONTEXT_ID,
        ),
    )

    private fun status(state: TaskState, timestamp: Instant = TIMESTAMP, message: String? = null) = TaskStatus(
        state = state,
        message = message?.let {
            Message(
                messageId = "agent-$it",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart(it)),
                contextId = CONTEXT_ID,
                taskId = TASK_ID,
            )
        },
        timestamp = timestamp,
    )

    private fun statusUpdate(state: TaskState, message: String? = null) = TaskStatusUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        status = status(state, message = message),
    )

    private fun task(state: TaskState) = Task(id = TASK_ID, contextId = CONTEXT_ID, status = status(state))

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val idleScript = Script { processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    private val doNothingOnCancel = CancelHandler { _, _ -> }

    private val canceledByExecutor = statusUpdate(TaskState.TASK_STATE_CANCELED, "canceled by executor")

    private val cancelingExecutor = CancelHandler { processor, agentJob ->
        agentJob?.cancelAndJoin()
        processor.sendTaskEvent(canceledByExecutor)
    }

    private class RunningTask {
        val started = CompletableDeferred<Unit>()
        val interrupted = CompletableDeferred<Unit>()

        val script = Script { processor ->
            processor.sendTaskEvent(Task(id = TASK_ID, contextId = CONTEXT_ID, status = status(TaskState.TASK_STATE_WORKING)))
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                interrupted.complete(Unit)
            }
        }

        private fun status(state: TaskState) = TaskStatus(state = state, timestamp = TIMESTAMP)
    }

    /**
     * Subscribes to the task in a new coroutine, recording the events; completes when the subscription ends.
     */
    private class Subscription(val events: MutableList<Event>, val firstEvent: CompletableDeferred<Unit>, val job: kotlinx.coroutines.Job)

    private fun CoroutineScope.subscribe(server: A2AServer): Subscription {
        val events = mutableListOf<Event>()
        val firstEvent = CompletableDeferred<Unit>()
        val job = launch {
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collect {
                events += it
                firstEvent.complete(Unit)
            }
        }
        return Subscription(events, firstEvent, job)
    }

    private fun forcedCancellation(event: Event): TaskStatusUpdateEvent {
        assertIs<TaskStatusUpdateEvent>(event)
        // The timestamp is taken by the server's clock.
        assertEquals(statusUpdate(TaskState.TASK_STATE_CANCELED).copy(status = status(TaskState.TASK_STATE_CANCELED, event.status.timestamp ?: TIMESTAMP)), event)
        assertNotNull(event.status.timestamp)
        return event
    }

    @Test
    fun testCancelIdleInputRequiredTaskForcesCanceledAndClosesSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to idleScript), doNothingOnCancel)
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        val subscription = subscribe(server)
        subscription.firstEvent.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        subscription.job.join()

        assertEquals(2, subscription.events.size)
        assertEquals(stored, subscription.events.first())
        val forced = forcedCancellation(subscription.events.last())
        assertEquals(
            stored.copy(status = forced.status, history = null),
            canceled,
        )
        assertEquals(canceled, taskStorage.get(TASK_ID, historyLength = 0, includeArtifacts = true))
    }

    @Test
    fun testCancelIdleTaskWithExecutorCancellationSendsNoDuplicateCanceled() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to idleScript), cancelingExecutor)
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        val subscription = subscribe(server)
        subscription.firstEvent.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        subscription.job.join()

        assertEquals(listOf<Event>(stored, canceledByExecutor), subscription.events)
        assertEquals(stored.copy(status = canceledByExecutor.status, history = null), canceled)
    }

    @Test
    fun testCancelRunningTaskEndsRequesterWithCanceledTaskWhenExecutorCancels() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), cancelingExecutor)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()
        val subscription = subscribe(server)
        subscription.firstEvent.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        subscription.job.join()

        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals(canceledByExecutor.status, canceled.status)
        // The requester's blocking call ends with the canceled task, not with an exception.
        assertEquals<Event?>(taskStorage.get(TASK_ID), response.await())
        assertEquals(1, subscription.events.count { it is TaskStatusUpdateEvent && it.status.state == TaskState.TASK_STATE_CANCELED })
    }

    @Test
    fun testCancelRunningTaskForcesCanceledAndInterruptsAgentWhenExecutorDoesNothing() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), doNothingOnCancel)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        running.interrupted.await()
        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals(null, canceled.status.message)
        assertEquals<Event?>(taskStorage.get(TASK_ID), response.await())
        assertEquals(TaskState.TASK_STATE_CANCELED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testCancelTerminalTaskIsNotCancelable() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap(), cancelingExecutor)
        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED))

        assertFailsWith<A2ATaskNotCancelableException> {
            server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        }
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testCancelUnknownTaskIsNotFound() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap(), cancelingExecutor)

        assertFailsWith<A2ATaskNotFoundException> {
            server.onCancelTask(CancelTaskRequest("unknown"), ctx)
        }
    }

    @Test
    fun testCancelRacingWithFollowUpTurnEndsInOneConsistentTerminalState() = runTest(timeout = TEST_TIMEOUT) {
        // Real concurrency: the cancellation and the follow-up turn run on different threads.
        withContext(Dispatchers.Default) {
            repeat(50) { attempt ->
                val storage = InMemoryTaskStorage()
                val finishScript = Script { processor ->
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
                }
                val server = A2AServer(
                    agentExecutor = CancelAgentExecutor(mapOf("start" to idleScript, "finish" to finishScript), doNothingOnCancel),
                    agentCard = agentCard,
                    taskStorage = storage,
                    idGenerator = FixedIdGenerator,
                    coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                )
                server.onSendMessage(request("start"), ctx)
                val followUp = request("finish").let { it.copy(message = it.message.copy(taskId = TASK_ID, messageId = "finish-")) }

                val results = listOf(
                    async { runCatching { server.onSendMessage(followUp, ctx) } },
                    async { runCatching { server.onCancelTask(CancelTaskRequest(TASK_ID), ctx) } },
                ).map { it.await() }

                val finalState = assertNotNull(storage.get(TASK_ID)).status.state
                assertEquals(true, finalState.terminal, "attempt : ")
                // The cancellation succeeds only if the task ended up canceled.
                val cancelResult = results[1]
                assertEquals(finalState == TaskState.TASK_STATE_CANCELED, cancelResult.isSuccess, "attempt : ")
            }
        }
    }
}
