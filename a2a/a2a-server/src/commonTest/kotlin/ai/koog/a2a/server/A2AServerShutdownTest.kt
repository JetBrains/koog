@file:OptIn(InternalA2AApi::class)

package ai.koog.a2a.server

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.isTerminal
import ai.koog.a2a.server.turn.Turn
import ai.koog.a2a.transport.A2AMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [A2AServer.cancel] and the shutdown state: which requests are rejected, how open streams end, and which scope is
 * cancelled.
 */
class A2AServerShutdownTest {
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
    }

    private class ObservedServer(
        scripts: Map<String, AgentScript>,
        taskStorage: InMemoryTaskStorage,
        pushConfigStorage: InMemoryPushNotificationConfigStorage,
        scope: CoroutineScope,
    ) : A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts),
        agentCard = testAgentCard(AgentCapabilities(streaming = true, pushNotifications = true, extendedAgentCard = true)),
        agentCardExtended = testAgentCard(name = "Extended agent"),
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = GatedPushNotificationSender(),
        idGenerator = FixedIdGenerator(),
        coroutineScope = scope,
    ) {
        suspend fun turn(): Turn? = turnManager.getTurn(TASK_ID)

        /** The number of running turns and of task streams held in memory. */
        suspend fun held(): Pair<Int, Int> = turnManager.activeTurns() to turnManager.activeStreams()
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript> = emptyMap(),
        scope: CoroutineScope = backgroundScope,
    ) = ObservedServer(scripts, taskStorage, pushConfigStorage, scope)

    /** The class and message of the failure of each operation of [server]. */
    private suspend fun failures(server: A2AServer): Map<A2AMethod, Pair<Any, String?>?> {
        val operations = operations(server)

        return A2AMethod.entries.associateWith { method ->
            runCatching { operations.getValue(method)(ctx) }.exceptionOrNull()?.let { it::class to it.message }
        }
    }

    private val shutDown = A2AInternalErrorException::class to "Server is shut down"

    /** The class and message of the failure, and the events received before it. */
    private fun Pair<Throwable, List<Event>>.described() = (first::class to first.message) to second

    private fun TestScope.subscribeExpectingFailure(server: A2AServer) =
        async { server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collectExpectingFailure() }

    /** The task created by [RunningAgent] for the message [text]. */
    private fun runningTask(text: String = "run") =
        task(TaskState.TASK_STATE_WORKING).copy(history = listOf(request(text).message.copy(taskId = TASK_ID)))

    /** Creates the task and keeps the agent running until it is cancelled. */
    private class RunningAgent {
        val started = CompletableDeferred<Unit>()
        val interrupted = CompletableDeferred<Unit>()

        val script = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                interrupted.complete(Unit)
            }
        }
    }

    @Test
    fun testEveryOperationFailsAfterCancel() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val server = createServer()

        server.cancel()

        assertEquals(A2AMethod.entries.associateWith { shutDown }, failures(server))
    }

    @Test
    fun testCancelStopsAgentsButNotUserScope() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val userScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val server = createServer(mapOf("run" to running.script), scope = userScope)
        server.onSendMessage(request("run", returnImmediately = true), ctx)
        running.started.await()

        server.cancel()
        running.interrupted.await()

        assertTrue(userScope.coroutineContext.job.isActive)
    }

    @Test
    fun testExternallyCancelledScopeShutsServerDown() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val userScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val server = createServer(scope = userScope)

        userScope.cancel()

        assertEquals(A2AMethod.entries.associateWith { shutDown }, failures(server))
    }

    @Test
    fun testFollowUpWaitingForRunningTurnIsRejectedOnCancel() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        var followUpRan = false
        val server = createServer(mapOf("run" to running.script, "finish" to AgentScript { _, _ -> followUpRan = true }))
        server.onSendMessage(request("run", returnImmediately = true), ctx)
        running.started.await()

        // Accepted while running, then waits for the running turn to end.
        val followUpRequest = request("finish", TASK_ID).copy(
            configuration = SendMessageConfiguration(
                taskPushNotificationConfig = TaskPushNotificationConfig(url = "https://example.com/hook"),
            ),
        )
        val followUp = async { runCatching { server.onSendMessage(followUpRequest, ctx) } }
        runCurrent()
        assertEquals(false, followUp.isCompleted)

        server.cancel()

        // Rejected when its turn would start: neither its agent ran, nor was its inline config saved.
        assertEquals(shutDown, followUp.await().exceptionOrNull()?.let { it::class to it.message })
        assertEquals(false, followUpRan)
        assertEquals(emptyList(), pushConfigStorage.getAll(TASK_ID))
        assertEquals(runningTask(), taskStorage.get(TASK_ID))
    }

    @Test
    fun testCancelEndsOpenStreamsOfRunningTaskWithShutdownError() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val server = createServer(mapOf("run" to running.script))
        val requester = async { server.onSendMessageStreaming(request("run"), ctx).collectExpectingFailure() }
        running.started.await()
        val subscriber = subscribeExpectingFailure(server)
        runCurrent()

        server.cancel()
        running.interrupted.await()

        assertEquals(shutDown to listOf<Event>(runningTask()), requester.await().described())
        assertEquals(shutDown to listOf<Event>(runningTask()), subscriber.await().described())
        // The task keeps its last state, and the server holds nothing.
        assertEquals(runningTask(), taskStorage.get(TASK_ID))
        runCurrent()
        assertEquals(0 to 0, server.held())
    }

    @Test
    fun testCancelFailsBlockingRequesterOfRunningTaskWithShutdownError() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val server = createServer(mapOf("run" to running.script))
        val response = async { runCatching { server.onSendMessage(request("run"), ctx) } }
        running.started.await()

        server.cancel()
        running.interrupted.await()

        assertEquals(shutDown, response.await().exceptionOrNull()?.let { it::class to it.message })
        assertEquals(runningTask(), taskStorage.get(TASK_ID))
    }

    @Test
    fun testCancelEndsSubscriptionThatGotTerminalEventNormally() = runTest(timeout = TEST_TIMEOUT) {
        val idle = task(TaskState.TASK_STATE_INPUT_REQUIRED).also { taskStorage.update(it) }
        val events = listOf(
            statusUpdate(TaskState.TASK_STATE_WORKING),
            artifactChunk("result"),
            statusUpdate(TaskState.TASK_STATE_COMPLETED),
        )
        val server = createServer(mapOf("finish" to AgentScript { _, processor -> events.forEach { processor.sendTaskEvent(it) } }))
        val release = CompletableDeferred<Unit>()

        // Gets the terminal event while its delivery to the stalled subscriber is pending, and shuts the server down.
        val completed = async {
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).onEach { if (it.isTerminal()) server.cancel() }.toList()
        }
        // Stalls on the artifact, so it never gets the terminal event.
        val stalled = async {
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx)
                .onEach { if (it is TaskArtifactUpdateEvent) release.await() }
                .collectExpectingFailure()
        }
        runCurrent()
        val response = async { runCatching { server.onSendMessage(request("finish", TASK_ID), ctx) } }

        assertEquals(listOf(idle) + events, completed.await())
        release.complete(Unit)
        assertEquals(shutDown to listOf(idle) + events.dropLast(1), stalled.await().described())
        // The terminal event hadn't reached the requester's view either.
        assertEquals(shutDown, response.await().exceptionOrNull()?.let { it::class to it.message })
    }

    @Test
    fun testCancelEndsFailedTurnWaitingForStalledRequester() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(
            mapOf(
                "fail" to AgentScript { _, processor ->
                    processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
                    throw IllegalStateException("Agent crashed")
                }
            )
        )
        val release = CompletableDeferred<Unit>()
        // Stalls on the task, so the delivery of the FAILED update, which isn't cancellable, waits for it.
        val requester = async {
            server.onSendMessageStreaming(request("fail"), ctx).onEach { release.await() }.collectExpectingFailure()
        }
        runCurrent()
        val turn = assertNotNull(server.turn())

        server.cancel()
        turn.join()
        release.complete(Unit)

        assertEquals(shutDown to listOf<Event>(runningTask("fail")), requester.await().described())
        runCurrent()
        assertEquals(0 to 0, server.held())
    }

    @Test
    fun testExternallyCancelledScopeEndsOpenSubscriptionWithShutdownError() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        val userScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val server = createServer(scope = userScope)
        val subscriber = subscribeExpectingFailure(server)
        runCurrent()

        userScope.cancel()

        assertEquals(shutDown to listOf<Event>(stored), subscriber.await().described())
    }
}
