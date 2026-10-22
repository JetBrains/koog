package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * SubscribeToTask and the per-task event stream at the [A2AServer] level, with a scripted agent gated by
 * [CompletableDeferred]s.
 */
class A2AServerSubscribeTest {
    private companion object {
        private const val FAILURE_MESSAGE_ID = "failure-message-id"
    }

    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript>,
        scope: CoroutineScope = backgroundScope,
    ): A2AServer = A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts),
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator(messageId = FAILURE_MESSAGE_ID),
        coroutineScope = scope,
    )

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val startScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "start"))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    /** Follow-up turn that completes the task. */
    private val finishScript = AgentScript { _, processor ->
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(artifactChunk("result"))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
    }

    private val finishEvents = listOf(
        statusUpdate(TaskState.TASK_STATE_WORKING),
        artifactChunk("result"),
        statusUpdate(TaskState.TASK_STATE_COMPLETED),
    )

    /** Runs the `start` script: the task is created and waits for input. */
    private suspend fun A2AServer.startIdleTask(): Task {
        onSendMessage(request("start"), ctx)
        return assertNotNull(taskStorage.get(TASK_ID))
    }

    /** Subscribes and waits for the snapshot. */
    private suspend fun CoroutineScope.subscribeToSnapshot(
        server: A2AServer,
        onEvent: suspend (Event) -> Unit = {},
    ) = subscribe(server, onEvent).also { it.firstEvent.await() }

    @Test
    fun testSubscribeAfterInputRequiredTurnEndedFollowsLaterTurn() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript, "finish" to finishScript))

        // The first turn returns at input-required and ends.
        val stored = server.startIdleTask()
        assertEquals(TaskState.TASK_STATE_INPUT_REQUIRED, stored.status.state)

        val subscription = subscribeToSnapshot(server)

        // A follow-up turn from another coroutine (another client) completes the task.
        launch { server.onSendMessage(request("finish", TASK_ID), ctx) }

        // The subscription completes at the terminal state.
        subscription.job.join()

        assertEquals(listOf<Event>(stored) + finishEvents, subscription.events)
    }

    @Test
    fun testSubscribersReceiveSameTurnEventsAsSendStream() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript, "finish" to finishScript))
        val stored = server.startIdleTask()

        val first = subscribeToSnapshot(server)
        val second = subscribeToSnapshot(server)

        val streamEvents = server.onSendMessageStreaming(request("finish", TASK_ID), ctx).toList()
        first.job.join()
        second.job.join()

        // The follow-up stream begins with the task snapshot taken when the turn view was registered, i.e. before the
        // user message of the follow-up is persisted (on the agent's first event).
        assertEquals(listOf<Event>(stored) + finishEvents, streamEvents)
        assertEquals(listOf<Event>(stored) + finishEvents, first.events)
        assertEquals(first.events, second.events)
    }

    @Test
    fun testCancellingOneSubscriberLeavesOthersRunning() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript, "finish" to finishScript))
        val stored = server.startIdleTask()

        val cancelled = subscribeToSnapshot(server)
        val active = subscribeToSnapshot(server)

        cancelled.job.cancelAndJoin()

        server.onSendMessage(request("finish", TASK_ID), ctx)
        active.job.join()

        assertEquals(listOf<Event>(stored), cancelled.events)
        assertEquals(listOf<Event>(stored) + finishEvents, active.events)
    }

    @Test
    fun testServerCancelEndsSubscriptionOfIdleTaskWithShutdownError() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript))
        val stored = server.startIdleTask()
        val subscription = async { server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collectExpectingFailure() }
        runCurrent()

        server.cancel()
        val (failure, received) = subscription.await()

        assertEquals(A2AInternalErrorException::class to "Server is shut down", failure::class to failure.message)
        assertEquals(listOf<Event>(stored), received)
    }

    @Test
    fun testSubscribeToTerminalOrUnknownTaskFailsBeforeFirstEvent() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap())
        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED, "start"))

        listOf(
            TASK_ID to A2AUnsupportedOperationException::class,
            "unknown" to A2ATaskNotFoundException::class,
        ).forEach { (taskId, expectedFailure) ->
            val (failure, received) = server.onSubscribeToTask(SubscribeToTaskRequest(taskId), ctx)
                .collectExpectingFailure()

            assertEquals(expectedFailure, failure::class, taskId)
            assertEquals(emptyList(), received, taskId)
        }
    }

    @Test
    fun testSubscribeWhileAgentEmitsRapidlyHasNoGap() = runTest(timeout = 60.seconds) {
        // Real concurrency: the agent and the subscribers run on different threads.
        withContext(Dispatchers.Default) {
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val eventCount = 2_000
            val subscriberCount = 8
            val taskCreated = CompletableDeferred<Unit>()
            val allSubscribed = CompletableDeferred<Unit>()

            val rapidScript = AgentScript { _, processor ->
                processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "rapid"))
                taskCreated.complete(Unit)

                repeat(eventCount) { i ->
                    processor.sendTaskEvent(
                        if (i % 2 == 0) artifactChunk("chunk-$i") else statusUpdate(TaskState.TASK_STATE_WORKING, "step-$i")
                    )
                }

                allSubscribed.await()
                processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
            }

            try {
                val server = createServer(mapOf("rapid" to rapidScript), scope = serverScope)

                server.onSendMessage(request("rapid", returnImmediately = true), ctx)
                taskCreated.await()

                // Subscribe while the agent is emitting.
                val subscriptions = List(subscriberCount) { subscribe(server) }
                subscriptions.forEach { it.firstEvent.await() }
                allSubscribed.complete(Unit)
                subscriptions.forEach { it.job.join() }

                val finalTask = assertNotNull(taskStorage.get(TASK_ID))
                assertEquals(TaskState.TASK_STATE_COMPLETED, finalTask.status.state)

                // Replaying each subscription (snapshot + events) must give exactly the final stored task:
                // a missed or duplicated event would change the history or the appended artifact parts.
                subscriptions.forEach { subscription ->
                    val replay = InMemoryTaskStorage()
                    subscription.events.forEach { replay.update(it as TaskEvent) }
                    assertEquals(finalTask, replay.get(TASK_ID))
                }
            } finally {
                serverScope.cancel()
            }
        }
    }

    @Test
    fun testExecutorExceptionMarksTaskFailedForSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val failingScript = AgentScript { _, processor ->
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
            throw IllegalStateException("Agent crashed")
        }
        val server = createServer(mapOf("start" to startScript, "fail" to failingScript))
        val stored = server.startIdleTask()

        val subscription = subscribeToSnapshot(server)

        // The blocking requester of the failing turn gets the failed task, written by the server.
        val response = server.onSendMessage(request("fail", TASK_ID), ctx)

        // The subscriber sees FAILED, then its stream completes.
        subscription.job.join()

        val failed = assertNotNull(taskStorage.get(TASK_ID))
        assertNotNull(failed.status.timestamp)

        // The message is generic: it doesn't expose the exception.
        val failedStatus = TaskStatus(
            state = TaskState.TASK_STATE_FAILED,
            message = Message(
                messageId = FAILURE_MESSAGE_ID,
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Task failed due to an internal error")),
                contextId = CONTEXT_ID,
                taskId = TASK_ID,
            ),
            timestamp = failed.status.timestamp,
        )
        assertEquals(failedStatus, failed.status)
        assertEquals(failed, response)
        assertEquals(
            listOf<Event>(
                stored,
                statusUpdate(TaskState.TASK_STATE_WORKING),
                TaskStatusUpdateEvent(taskId = TASK_ID, contextId = CONTEXT_ID, status = failedStatus),
            ),
            subscription.events
        )
    }

    @Test
    fun testSlowSubscriberDelaysAgentButReceivesEveryEventInOrder() = runTest(timeout = TEST_TIMEOUT) {
        val updates = List(20) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val agentProgress = MutableStateFlow(0)
        val manyScript = AgentScript { _, processor ->
            updates.forEach {
                processor.sendTaskEvent(it)
                agentProgress.value++
            }
            processor.sendTaskEvent(completed)
        }
        val server = createServer(mapOf("start" to startScript, "many" to manyScript))
        val stored = server.startIdleTask()

        val releaseSlow = CompletableDeferred<Unit>()
        val slow = subscribeToSnapshot(server) { if (it == updates[2]) releaseSlow.await() }
        val fast = subscribeToSnapshot(server)

        val response = async { server.onSendMessage(request("many", TASK_ID), ctx) }
        runCurrent()

        // The slow subscriber is still processing the third update: the agent has sent it, but is held back from
        // sending the fourth one. Nothing is dropped.
        assertEquals(3, agentProgress.value)
        assertEquals(false, response.isCompleted)

        releaseSlow.complete(Unit)
        slow.job.join()
        fast.job.join()

        assertEquals(assertNotNull(taskStorage.get(TASK_ID)), response.await())
        assertEquals(listOf<Event>(stored) + updates + completed, slow.events)
        assertEquals(listOf<Event>(stored) + updates + completed, fast.events)
    }

    @Test
    fun testAgentEmittingManyEventsWithoutSuspendingCompletesBlockingSend() = runTest(timeout = TEST_TIMEOUT) {
        // More events than any reasonable buffer, sent in a tight loop on the single-threaded test dispatcher.
        val updates = List(500) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val manyScript = AgentScript { _, processor ->
            updates.forEach { processor.sendTaskEvent(it) }
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("start" to startScript, "many" to manyScript))
        val stored = server.startIdleTask()

        val subscription = subscribeToSnapshot(server)

        val response = server.onSendMessage(request("many", TASK_ID), ctx)
        subscription.job.join()

        assertEquals(assertNotNull(taskStorage.get(TASK_ID)), response)
        assertEquals(listOf<Event>(stored) + updates + statusUpdate(TaskState.TASK_STATE_COMPLETED), subscription.events)
    }
}
