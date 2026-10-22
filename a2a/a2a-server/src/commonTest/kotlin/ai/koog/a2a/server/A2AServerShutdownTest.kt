@file:OptIn(InternalA2AApi::class)

package ai.koog.a2a.server

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.pushConfig
import ai.koog.a2a.server.notifications.pushes
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.isTerminal
import ai.koog.a2a.server.turn.Turn
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [A2AServer.cancel], [A2AServer.shutdown] and the shutdown state: which requests are rejected, what is waited for, how
 * open streams end, and which scope is cancelled.
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
        taskStorage: TaskStorage,
        pushConfigStorage: PushNotificationConfigStorage,
        pushSender: GatedPushNotificationSender,
        scope: CoroutineScope,
    ) : A2AServer(
        // Cancellation leaves it to the server to cancel the task.
        agentExecutor = ScriptedAgentExecutor(scripts, cancelHandler = { _, _ -> }),
        agentCard = testAgentCard(AgentCapabilities(streaming = true, pushNotifications = true, extendedAgentCard = true)),
        agentCardExtended = testAgentCard(name = "Extended agent"),
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
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
        taskStorage: TaskStorage = this@A2AServerShutdownTest.taskStorage,
        pushConfigStorage: PushNotificationConfigStorage = this@A2AServerShutdownTest.pushConfigStorage,
        pushSender: GatedPushNotificationSender = GatedPushNotificationSender(),
    ) = ObservedServer(scripts, taskStorage, pushConfigStorage, pushSender, scope)

    /** The class and message of the failure of each operation of [server]. */
    private suspend fun failures(server: A2AServer): Map<A2AMethod, Pair<Any, String?>?> {
        val operations = operations(server)

        return A2AMethod.entries.associateWith { method ->
            runCatching { operations.getValue(method)(ctx) }.exceptionOrNull()?.let { it::class to it.message }
        }
    }

    private val shutDown = A2AInternalErrorException::class to "Server is shut down"

    private val shuttingDown = A2AInternalErrorException::class to "Server is shutting down"

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

    /** Sets the task working, waits for [release], then sets it to [finalState]. */
    private class GatedAgent(private val finalState: TaskState = TaskState.TASK_STATE_COMPLETED) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val script = AgentScript { context, processor ->
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING, taskId = context.taskId))
            started.complete(Unit)
            release.await()
            processor.sendTaskEvent(statusUpdate(finalState, taskId = context.taskId))
        }

        /** The updates the agent sends to [TASK_ID]. */
        val updates = listOf(statusUpdate(TaskState.TASK_STATE_WORKING), statusUpdate(finalState))
    }

    /** Starts a turn of [agent] (registered as "hold") on another task, keeping a graceful shutdown draining. */
    private suspend fun holdDrain(server: A2AServer, agent: GatedAgent) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED, taskId = OTHER_TASK_ID))
        server.onSendMessage(request("hold", OTHER_TASK_ID, returnImmediately = true), ctx)
        agent.started.await()
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

    @Test
    fun testGracefulShutdownWaitsForRunningTurn() = runTest(timeout = TEST_TIMEOUT) {
        val idle = task(TaskState.TASK_STATE_INPUT_REQUIRED).also { taskStorage.update(it) }
        val agent = GatedAgent()
        val server = createServer(mapOf("work" to agent.script))
        val subscriber = async { server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).toList() }
        runCurrent()
        val requester = async { server.onSendMessageStreaming(request("work", TASK_ID), ctx).toList() }
        agent.started.await()

        val shutdown = async { server.shutdown(1.minutes) }
        runCurrent()
        assertEquals(false, shutdown.isCompleted)
        agent.release.complete(Unit)
        shutdown.await()

        // Returned once the turn ended, before the deadline; the requester and the subscriber ended normally.
        assertEquals(0L, currentTime)
        assertEquals(listOf(idle) + agent.updates, requester.await())
        assertEquals(listOf(idle) + agent.updates, subscriber.await())
        assertEquals(0 to 0, server.held())
        assertEquals(A2AMethod.entries.associateWith { shutDown }, failures(server))
    }

    @Test
    fun testDrainingRejectsOnlyNewTurnsAndSubscriptions() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val hold = GatedAgent()
        val server = createServer(mapOf("hold" to hold.script))
        holdDrain(server, hold)

        val shutdown = async { server.shutdown(1.minutes) }
        runCurrent()

        val rejected = failures(server).filterValues { it == shuttingDown || it == shutDown }
        assertEquals(
            mapOf(
                A2AMethod.SendMessage to shuttingDown,
                A2AMethod.SendMessageStreaming to shuttingDown,
                A2AMethod.SubscribeToTask to shuttingDown,
            ),
            rejected,
        )
        // The CancelTask has canceled the task.
        assertEquals(TaskState.TASK_STATE_CANCELED, taskStorage.get(TASK_ID)?.status?.state)
        assertEquals(false, shutdown.isCompleted)

        hold.release.complete(Unit)
        shutdown.await()
    }

    @Test
    fun testFollowUpWaitingForRunningTurnIsRejectedWhileDraining() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val work = GatedAgent(finalState = TaskState.TASK_STATE_INPUT_REQUIRED)
        val hold = GatedAgent()
        var followUpRan = false
        val server = createServer(
            mapOf("work" to work.script, "hold" to hold.script, "finish" to AgentScript { _, _ -> followUpRan = true })
        )
        holdDrain(server, hold)
        server.onSendMessage(request("work", TASK_ID, returnImmediately = true), ctx)
        work.started.await()

        // Accepted while running, then waits for the running turn to end.
        val followUpRequest = request("finish", TASK_ID).copy(
            configuration = SendMessageConfiguration(
                taskPushNotificationConfig = TaskPushNotificationConfig(url = "https://example.com/hook"),
            ),
        )
        val followUp = async { runCatching { server.onSendMessage(followUpRequest, ctx) } }
        runCurrent()
        val shutdown = async { server.shutdown(1.minutes) }
        runCurrent()
        work.release.complete(Unit)

        // Rejected when its turn would start, while the other task's turn keeps the server draining.
        assertEquals(shuttingDown, followUp.await().exceptionOrNull()?.let { it::class to it.message })
        assertEquals(false, followUpRan)
        assertEquals(emptyList(), pushConfigStorage.getAll(TASK_ID))
        assertEquals(false, shutdown.isCompleted)

        hold.release.complete(Unit)
        shutdown.await()
    }

    @Test
    fun testTurnStillStartingIsWaitedFor() = runTest(timeout = TEST_TIMEOUT) {
        val saves = GatedSaveConfigStorage(pushConfigStorage)
        val server = createServer(
            mapOf("done" to AgentScript { _, processor -> processor.sendTaskEvent(task(TaskState.TASK_STATE_COMPLETED)) }),
            pushConfigStorage = saves,
        )
        val sendRequest = request("done").copy(
            configuration = SendMessageConfiguration(
                taskPushNotificationConfig = TaskPushNotificationConfig(url = "https://example.com/hook"),
            ),
        )
        val response = async { server.onSendMessage(sendRequest, ctx) }
        // Admitted, but not running yet: its inline config is being saved.
        saves.saving.await()

        val shutdown = async { server.shutdown(1.minutes).let { currentTime } }
        runCurrent()
        // Released before asserting: the save can't be cancelled.
        val isShutDownEarly = shutdown.isCompleted
        saves.release.complete(Unit)

        assertEquals(false, isShutDownEarly)
        assertEquals(0L, shutdown.await())
        val completed = task(TaskState.TASK_STATE_COMPLETED).copy(history = listOf(sendRequest.message.copy(taskId = TASK_ID)))
        assertEquals<Any>(completed, response.await())
    }

    @Test
    fun testDeadlineCutsRunningTurn() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val server = createServer(mapOf("run" to running.script))
        val requester = async { server.onSendMessageStreaming(request("run"), ctx).collectExpectingFailure() }
        running.started.await()

        server.shutdown(10.seconds)

        assertEquals(10.seconds.inWholeMilliseconds, currentTime)
        running.interrupted.await()
        assertEquals(shutDown to listOf<Event>(runningTask()), requester.await().described())
        assertEquals(runningTask(), taskStorage.get(TASK_ID))
    }

    @Test
    fun testSubscriptionOfIdleTaskStaysOpenUntilDrainEnds() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        val hold = GatedAgent()
        val server = createServer(mapOf("hold" to hold.script))
        val subscriber = subscribeExpectingFailure(server)
        runCurrent()
        holdDrain(server, hold)

        val shutdown = async { server.shutdown(1.minutes) }
        runCurrent()
        assertEquals(false, subscriber.isCompleted)
        hold.release.complete(Unit)
        shutdown.await()

        assertEquals(shutDown to listOf<Event>(stored), subscriber.await().described())
    }

    /** Lets the agent complete [TASK_ID] while every push to the config "hook" is blocked. */
    private suspend fun TestScope.completeWithBlockedPushes(): Pair<ObservedServer, GatedPushNotificationSender> {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        pushConfigStorage.save(pushConfig(TASK_ID, "hook"), ctx)
        val sender = GatedPushNotificationSender().apply { block("hook") }
        val server = createServer(mapOf("finish" to finishScript), pushSender = sender)

        server.onSendMessage(request("finish", TASK_ID), ctx)
        runCurrent()

        return server to sender
    }

    private val finishScript = AgentScript { _, processor -> finishUpdates.forEach { processor.sendTaskEvent(it) } }

    private val finishUpdates = listOf(statusUpdate(TaskState.TASK_STATE_WORKING), statusUpdate(TaskState.TASK_STATE_COMPLETED))

    @Test
    fun testQueuedPushIsDeliveredDuringGracePeriod() = runTest(timeout = TEST_TIMEOUT) {
        val (server, sender) = completeWithBlockedPushes()

        val shutdown = async { server.shutdown(1.minutes) }
        runCurrent()
        assertEquals(false, shutdown.isCompleted)
        sender.unblock("hook")
        shutdown.await()

        assertEquals(0L, currentTime)
        assertEquals(pushes("hook", finishUpdates), sender.sent)
    }

    @Test
    fun testQueuedPushIsDroppedAtDeadline() = runTest(timeout = TEST_TIMEOUT) {
        val (server, sender) = completeWithBlockedPushes()

        server.shutdown(10.seconds)

        assertEquals(10.seconds.inWholeMilliseconds, currentTime)
        assertEquals(emptyList(), sender.sent)
        // The first push was cut, the second never started.
        assertEquals(pushes("hook", finishUpdates.take(1)), sender.started)
    }

    @Test
    fun testEarliestDeadlineWins() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val server = createServer(mapOf("run" to running.script))
        server.onSendMessage(request("run", returnImmediately = true), ctx)
        running.started.await()

        val long = async { server.shutdown(1.hours).let { currentTime } }
        runCurrent()
        val short = async { server.shutdown(10.seconds).let { currentTime } }
        runCurrent()
        val longer = async { server.shutdown(2.hours).let { currentTime } }

        assertEquals(List(3) { 10.seconds.inWholeMilliseconds }, awaitAll(long, short, longer))
        running.interrupted.await()
    }

    @Test
    fun testCancelEndsWaitingShutdown() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val server = createServer(mapOf("run" to running.script))
        server.onSendMessage(request("run", returnImmediately = true), ctx)
        running.started.await()
        val shutdown = async { server.shutdown(1.hours).let { currentTime } }
        runCurrent()

        server.cancel()

        assertEquals(0L, shutdown.await())
        running.interrupted.await()
    }

    @Test
    fun testCancelledShutdownCallerShutsDownAtOnce() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningAgent()
        val server = createServer(mapOf("run" to running.script))
        server.onSendMessage(request("run", returnImmediately = true), ctx)
        running.started.await()
        val caller = launch { server.shutdown(1.hours) }
        runCurrent()

        caller.cancelAndJoin()

        assertTrue(caller.isCancelled)
        running.interrupted.await()
        assertEquals(0L, currentTime)
        assertEquals(A2AMethod.entries.associateWith { shutDown }, failures(server))
    }

    @Test
    fun testExternallyCancelledScopeEndsWaitingShutdown() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))
        val storage = HangingTaskStorage(taskStorage)
        val agent = GatedAgent()
        val userScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val server = createServer(mapOf("work" to agent.script), scope = userScope, taskStorage = storage)
        server.onSendMessage(request("work", TASK_ID, returnImmediately = true), ctx)
        agent.started.await()
        val shutdown = async { server.shutdown(1.hours).let { currentTime } }
        runCurrent()
        // The agent's next update hangs in the storage, which a shutdown can't interrupt, so its turn can't end.
        storage.hang()
        agent.release.complete(Unit)
        runCurrent()

        userScope.cancel()
        val shutDownAt = shutdown.await()
        // Released before asserting: the update can't be cancelled.
        storage.release.complete(Unit)

        assertEquals(0L, shutDownAt)
    }

    /** Waits for [release] in every save, after completing [saving]. */
    private class GatedSaveConfigStorage(
        private val delegate: PushNotificationConfigStorage,
    ) : PushNotificationConfigStorage by delegate {
        val saving = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun save(config: TaskPushNotificationConfig, context: ServerCallContext): TaskPushNotificationConfig {
            saving.complete(Unit)
            release.await()
            return delegate.save(config, context)
        }
    }

    /** Once [hang] is called, every update waits for [release]. */
    private class HangingTaskStorage(private val delegate: TaskStorage) : TaskStorage by delegate {
        val release = CompletableDeferred<Unit>()
        private var hangs = false

        fun hang() {
            hangs = true
        }

        override suspend fun update(event: TaskEvent) {
            if (hangs) release.await()
            delegate.update(event)
        }
    }

    private companion object {
        const val OTHER_TASK_ID = "task-2"
    }
}
