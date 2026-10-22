@file:OptIn(InternalA2AApi::class)

package ai.koog.a2a.server.turn

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.CONTEXT_ID
import ai.koog.a2a.server.TASK_ID
import ai.koog.a2a.server.TEST_TIMEOUT
import ai.koog.a2a.server.ctx
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TurnNotActiveException
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.pushConfig
import ai.koog.a2a.server.statusUpdate
import ai.koog.a2a.server.task
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.userMessage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TurnManagerTest {
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: GatedPushNotificationSender

    private val contextId = CONTEXT_ID
    private val taskId = TASK_ID

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
        pushSender = GatedPushNotificationSender()
    }

    /**
     * The task as stored when the agent created it in a turn started with [start]: the server adds the user message
     * of the request to its history.
     */
    private fun Task.withUserMessage() =
        copy(history = listOf(userMessage("Hello", taskId = id, messageId = "user-msg")))

    /**
     * Starts a turn of [taskId] with a user message "Hello" in [contextId]: with [followUp], a turn continuing the stored
     * task, otherwise a turn of a new task.
     */
    private suspend fun TurnManager.start(
        taskId: String,
        contextId: String,
        followUp: Boolean = false,
        messageId: String = "user-msg",
        block: suspend (TurnEventProcessor) -> Unit,
    ): Turn = startTurn(
        request = SendMessageRequest(userMessage("Hello", taskId.takeIf { followUp }, contextId, messageId)),
        ctx = ctx,
        taskId = taskId,
    ) { _, processor -> block(processor) }

    private fun TestScope.createManager() = TurnManager(
        coroutineScope = backgroundScope,
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
        idGenerator = FixedMessageIdGenerator,
    )

    /**
     * The events of a turn are delivered with back-pressure, so the requester's view must be consumed for the agent
     * to make progress.
     */
    private fun TestScope.drain(turn: Turn): Deferred<List<Event>> = async { turn.events.toList() }

    private suspend fun storeTask(
        taskId: String = this.taskId,
        contextId: String = this.contextId,
        state: TaskState = TaskState.TASK_STATE_INPUT_REQUIRED,
    ): Task = task(state, taskId = taskId, contextId = contextId).also { taskStorage.update(it) }

    @Test
    fun testMessageTurn() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val message = userMessage("Hello", messageId = "msg-1")
        assertNull(turnManager.getTurn("any-task-id"))

        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendMessage(message)
        }

        assertEquals(listOf<Event>(message), turn.events.toList())
        turn.join()
    }

    @Test
    fun testTaskTurn() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()
        val task = task()
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(task)
            gate.await()
            processor.sendTaskEvent(completed)
        }

        assertSame(turn, turnManager.getTurn(taskId))

        gate.complete(Unit)

        assertEquals(listOf(task.withUserMessage(), completed), turn.events.toList())
        turn.join()

        assertEquals(task.withUserMessage().copy(status = completed.status), taskStorage.get(taskId))
    }

    @Test
    fun testFollowUpsWaitForRunningTurnAndStartOneAfterAnotherInArrivalOrder() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val release = CompletableDeferred<Unit>()
        val log = mutableListOf<String>()

        val running = turnManager.start(taskId, contextId, followUp = true) { release.await() }
        drain(running)
        val followUps = (1..3).map { i ->
            async {
                turnManager.start(taskId, contextId, followUp = true, messageId = "user-$i") {
                    log += "start $i"
                    delay(100) // a turn running concurrently would interleave here
                    log += "end $i"
                }.also { it.events.toList() }
            }.also { runCurrent() }
        }

        // None of them starts while the turn runs.
        assertEquals(emptyList(), log)
        assertEquals(listOf(false, false, false), followUps.map { it.isCompleted })
        assertSame(running, turnManager.getTurn(taskId))

        release.complete(Unit)
        followUps.awaitAll().forEach { it.join() }

        assertEquals(listOf("start 1", "end 1", "start 2", "end 2", "start 3", "end 3"), log)
    }

    @Test
    fun testFollowUpCancelledWhileWaitingNeverStartsAndDoesNotBlockNextOne() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()

        val running = turnManager.start(taskId, contextId, followUp = true) { release.await() }
        drain(running)
        val cancelled = async {
            turnManager.start(taskId, contextId, followUp = true, messageId = "user-1") { started += "follow-up 1" }
        }
        runCurrent()
        val next = async {
            turnManager.start(taskId, contextId, followUp = true, messageId = "user-2") { started += "follow-up 2" }
                .also { it.events.toList() }
        }
        runCurrent()

        // The client of the first follow-up goes away while it waits.
        cancelled.cancelAndJoin()
        // Waiting pins nothing: only the running turn holds the task stream.
        assertEquals(1, turnManager.activeStreams())

        release.complete(Unit)
        next.await().join()

        assertTrue(cancelled.isCancelled)
        assertEquals(listOf("follow-up 2"), started)
    }

    @Test
    fun testTurnOfOtherTaskDoesNotWaitForBusyTask() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask("task-a", "context-a")

        // Task A has a turn that never ends on its own, and a follow-up waiting for it.
        val running = turnManager.start("task-a", "context-a", followUp = true) { awaitCancellation() }
        drain(running)
        val waiting = async { turnManager.start("task-a", "context-a", followUp = true) {}.also { drain(it) } }
        runCurrent()

        val reply = userMessage("Hello B", contextId = "context-b", messageId = "msg-b")
        val otherTask = async {
            turnManager.start("task-b", "context-b") { it.sendMessage(reply) }.events.toList()
        }
        runCurrent()

        assertEquals(true, otherTask.isCompleted)
        assertEquals(listOf<Event>(reply), otherTask.await())
        assertEquals(false, waiting.isCompleted)

        running.agentJob.cancelAndJoin()
        waiting.await().join()
    }

    @Test
    fun testMultipleTurns() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()

        fun turn(taskId: String, contextId: String): suspend (TurnEventProcessor) -> Unit = { processor ->
            processor.sendTaskEvent(task(taskId = taskId, contextId = contextId))
            gate.await()
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED, taskId = taskId, contextId = contextId))
        }

        val turn1 = turnManager.start("task-1", "context-1", block = turn("task-1", "context-1"))
        val turn2 = turnManager.start("task-2", "context-2", block = turn("task-2", "context-2"))
        drain(turn1)
        drain(turn2)

        assertSame(turn1, turnManager.getTurn("task-1"))
        assertSame(turn2, turnManager.getTurn("task-2"))

        gate.complete(Unit)
        turn1.join()
        turn2.join()
    }

    @Test
    fun testAgentOwnTimeoutMarksTaskFailedAndEndsSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val turn = turnManager.start(taskId, contextId, followUp = true) { processor ->
            processor.sendTaskEvent(working)
            withTimeout(1) { awaitCancellation() }
        }

        val events = turn.events.toList()
        val failure = assertFailsWith<AgentCancellationFailure> { turn.agentJob.await() }
        assertIs<TimeoutCancellationException>(failure.cause)
        turn.join()
        subscriber.join()

        val stored = assertNotNull(taskStorage.get(taskId))
        val failedStatus = failedStatus(taskId, contextId, stored.status.timestamp)
        val failedEvent = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = failedStatus)
        assertEquals(failedStatus, stored.status)
        assertEquals(listOf<Event>(working, failedEvent), events)
        assertEquals(listOf<Event>(inputRequired, working, failedEvent), received)
    }

    @Test
    fun testCancelledAgentJobDoesNotMarkTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val working = task(TaskState.TASK_STATE_WORKING)
        val agentStarted = CompletableDeferred<Unit>()

        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(working)
            agentStarted.complete(Unit)
            awaitCancellation()
        }
        val events = drain(turn)
        agentStarted.await()

        turn.agentJob.cancelAndJoin()
        turn.join()

        assertFailsWith<CancellationException> { turn.agentJob.await() }
        assertEquals(listOf<Event>(working.withUserMessage()), events.await())
        assertEquals(working.withUserMessage(), taskStorage.get(taskId))
    }

    /** A scope with a plain, non-supervisor job, like `CoroutineScope(Dispatchers.IO)`. */
    private fun TestScope.nonSupervisorScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))

    @Test
    fun testFailedTurnDoesNotCancelNonSupervisorScope() = runTest(timeout = TEST_TIMEOUT) {
        val scope = nonSupervisorScope()
        val turnManager = TurnManager(scope, taskStorage, pushConfigStorage, pushSender, FixedMessageIdGenerator)
        savePushConfig()
        val task = task()
        val runningTask = task(taskId = "task-2")
        val gate = CompletableDeferred<Unit>()

        val running = turnManager.start("task-2", contextId) { processor ->
            processor.sendTaskEvent(runningTask)
            gate.await()
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED, taskId = "task-2"))
        }
        val runningEvents = drain(running)

        turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(task)
            throw IllegalStateException("boom")
        }.also { drain(it) }.join()
        runCurrent()

        assertTrue(scope.isActive)
        val stored = assertNotNull(taskStorage.get(taskId))
        val failed = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = failedStatus(taskId, contextId, stored.status.timestamp),
        )
        assertEquals(failed.status, stored.status)
        assertEquals(listOf<Pair<String, Event>>("c1" to task.withUserMessage(), "c1" to failed), pushSender.sent)

        // The other running agent goes on, and a new turn runs normally.
        gate.complete(Unit)
        assertEquals(
            listOf(runningTask.withUserMessage(), statusUpdate(TaskState.TASK_STATE_COMPLETED, taskId = "task-2")),
            runningEvents.await(),
        )

        val newTask = task(TaskState.TASK_STATE_COMPLETED, taskId = "task-3")
        val next = turnManager.start("task-3", contextId) { processor ->
            processor.sendTaskEvent(newTask)
        }
        assertEquals(listOf(newTask.withUserMessage()), next.events.toList())
        assertTrue(scope.isActive)
    }

    @Test
    fun testCancellingNonSupervisorScopeCancelsRunningAgent() = runTest(timeout = TEST_TIMEOUT) {
        val scope = nonSupervisorScope()
        val turnManager = TurnManager(coroutineScope = scope, taskStorage = taskStorage)
        val agentStarted = CompletableDeferred<Unit>()

        val turn = turnManager.start(taskId, contextId) {
            agentStarted.complete(Unit)
            awaitCancellation()
        }
        drain(turn)
        agentStarted.await()

        scope.cancel()
        turn.join()

        assertFailsWith<CancellationException> { turn.agentJob.await() }
        assertNull(turnManager.getTurn(taskId))
    }

    @Test
    fun testSubscribeFollowsTaskAcrossTurns() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val inputRequiredAgain = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED)
        turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(inputRequiredAgain)
        }.also { drain(it) }.join()

        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(completed)
        }.also { drain(it) }.join()

        subscriber.join()

        assertEquals(listOf<Event>(inputRequired, working, inputRequiredAgain, completed), received)
    }

    @Test
    fun testWithTaskProcessorOnIdleTaskReachesSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val canceled = statusUpdate(TaskState.TASK_STATE_CANCELED)
        turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, processor, agentJob, _ ->
            assertNull(agentJob)
            processor.sendTaskEvent(canceled)
        }

        subscriber.join()
        assertEquals(listOf<Event>(inputRequired, canceled), received)
    }

    @Test
    fun testStandaloneProcessorIsClosedAfterWithTaskProcessor() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val leaked = turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, processor, _, _ -> processor }

        assertFailsWith<TurnNotActiveException> {
            leaked.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        }
        assertEquals(inputRequired, taskStorage.get(taskId))
    }

    @Test
    fun testWithTaskProcessorReadsTaskUnderGate() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val working = task(TaskState.TASK_STATE_WORKING)
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch {
            turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, _, _, _ ->
                releaseFirst.await()
                taskStorage.update(working)
            }
        }
        runCurrent()
        // Waits for the gate held by the first block, so it reads the task the first block left.
        val second = async {
            turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { task, processor, _, _ ->
                task to processor.contextId
            }
        }
        runCurrent()

        releaseFirst.complete(Unit)
        first.join()

        assertEquals(working to contextId, second.await())
    }

    @Test
    fun testWithTaskProcessorUnknownTaskSkipsBlock() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        var blockRan = false

        val error = assertFailsWith<A2ATaskNotFoundException> {
            turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, _, _, _ -> blockRan = true }
        }
        assertEquals("Task '$taskId' not found", error.message)
        assertEquals(false, blockRan)
    }

    @Test
    fun testWithTaskProcessorReadsTaskWithLookupAndCallContext() = runTest(timeout = TEST_TIMEOUT) {
        val inputRequired = storeTask()
        val owner = ServerCallContext()
        val lookups = mutableListOf<Pair<GetTaskRequest, ServerCallContext>>()
        // An authorization-aware storage: the task is visible only to its owner.
        val ownerScopedStorage = object : TaskStorage by taskStorage {
            override suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task? {
                lookups += request to context
                return if (context === owner) taskStorage.get(request, context) else null
            }
        }
        val turnManager = TurnManager(coroutineScope = backgroundScope, taskStorage = ownerScopedStorage)
        val lookup = GetTaskRequest(taskId, historyLength = 0, tenant = "tenant-1")
        var blockRan = false

        assertFailsWith<A2ATaskNotFoundException> {
            turnManager.withTaskProcessor(lookup, ctx) { _, _, _, _ -> blockRan = true }
        }
        val task = turnManager.withTaskProcessor(lookup, owner) { task, _, _, _ -> task }

        assertEquals(false, blockRan)
        assertEquals(inputRequired, task)
        assertEquals(listOf(lookup to ctx, lookup to owner), lookups)
    }

    @Test
    fun testTurnProcessorIsClosedAfterTurnEnds() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val task = task()
        var leaked: TurnEventProcessor? = null

        val turn = turnManager.start(taskId, contextId) { processor ->
            leaked = processor
            processor.sendTaskEvent(task)
        }
        val events = drain(turn)
        turn.join()

        assertFailsWith<TurnNotActiveException> {
            assertNotNull(leaked).sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        assertEquals(listOf<Event>(task.withUserMessage()), events.await())
        assertEquals(task.withUserMessage(), taskStorage.get(taskId))
    }

    @Test
    fun testTurnStartedInCancelledScopeEndsWithoutRunningAgent() = runTest(timeout = TEST_TIMEOUT) {
        // The scope of a server that has been shut down.
        val cancelledScope = CoroutineScope(backgroundScope.coroutineContext + Job().apply { cancel() })
        val turnManager = TurnManager(coroutineScope = cancelledScope, taskStorage = taskStorage)
        var agentStarted = false

        val turn = turnManager.start(taskId, contextId) { agentStarted = true }

        withTimeout(5.seconds) {
            assertEquals(emptyList(), turn.events.toList())
            turn.join()
        }
        assertFailsWith<CancellationException> { turn.agentJob.await() }
        assertEquals(false, agentStarted)
        assertNull(turnManager.getTurn(taskId))
    }

    @Test
    fun testWithTaskProcessorKeepsRunningTurnOpen() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val task = task(TaskState.TASK_STATE_WORKING)
        val taskSent = CompletableDeferred<Unit>()

        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(task)
            taskSent.complete(Unit)
            CompletableDeferred<Unit>().await() // runs until cancelled
        }
        val events = drain(turn)
        taskSent.await()

        val canceled = statusUpdate(TaskState.TASK_STATE_CANCELED)
        turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, processor, agentJob, _ ->
            assertSame(turn.agentJob, agentJob)
            agentJob?.cancelAndJoin()
            // Give the turn a chance to finish, if it (wrongly) could.
            advanceUntilIdle()
            processor.sendTaskEvent(canceled)
        }

        // The requester of the turn receives the events published by the cancellation.
        assertEquals(listOf(task.withUserMessage(), canceled), events.await())
        turn.join()
    }

    @Test
    fun testJoinReturnsOnlyAfterTurnTeardown() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val blockEntered = CompletableDeferred<Unit>()
        val releaseBlock = CompletableDeferred<Unit>()

        // The agent completes only once the block below holds the turn gate.
        val turn = turnManager.start(taskId, contextId) { blockEntered.await() }
        val events = drain(turn)
        // Holds the turn gate, so the turn can't end after its agent job has completed.
        val block = launch {
            turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, _, _, _ ->
                blockEntered.complete(Unit)
                releaseBlock.await()
            }
        }
        blockEntered.await()
        val join = launch { turn.join() }
        runCurrent()

        assertEquals(true, turn.agentJob.isCompleted)
        assertEquals(false, join.isCompleted)
        assertSame(turn, turnManager.getTurn(taskId))

        releaseBlock.complete(Unit)
        join.join()

        assertNull(turnManager.getTurn(taskId))
        assertEquals(emptyList(), events.await())
        block.join()
    }

    @Test
    fun testServerPublishDuringFollowUpTurnSkipsPendingUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        savePushConfig()
        val agentStarted = CompletableDeferred<Unit>()

        val turn = turnManager.start(taskId, contextId, followUp = true) {
            agentStarted.complete(Unit)
            CompletableDeferred<Unit>().await() // sends nothing, runs until cancelled
        }
        val events = drain(turn)
        agentStarted.await()

        val canceled = statusUpdate(TaskState.TASK_STATE_CANCELED)
        turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, _, agentJob, publish ->
            agentJob?.cancelAndJoin()
            publish(canceled)
        }
        turn.join()
        runCurrent()

        // The requester gets the event, it's pushed, and the follow-up's user message isn't added to the history.
        assertEquals(listOf<Event>(canceled), events.await())
        assertEquals(listOf<Pair<String, Event>>("c1" to canceled), pushSender.sent)
        assertEquals(
            inputRequired.copy(status = canceled.status, history = listOf(followUpPrompt())),
            taskStorage.get(taskId)
        )
    }

    @Test
    fun testServerPublishAfterWithTaskProcessorReturnsFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val leaked = turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, _, _, publish -> publish }

        assertFailsWith<IllegalStateException> {
            leaked(statusUpdate(TaskState.TASK_STATE_CANCELED))
        }
        assertEquals(inputRequired, taskStorage.get(taskId))
    }

    private fun followUpPrompt() = Message(
        messageId = "agent-prompt",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("Which one?")),
        contextId = contextId,
        taskId = taskId,
    )

    private suspend fun storeInputRequiredTask(): Task =
        task(TaskState.TASK_STATE_INPUT_REQUIRED).let {
            it.copy(status = it.status.copy(message = followUpPrompt()))
        }.also { taskStorage.update(it) }

    @Test
    fun testFollowUpTurnPersistsUserMessageOnFirstTaskEvent() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        val userMessage = userMessage("Hello", messageId = "user-msg").copy(taskId = taskId)
        var storedWhenAgentStarted: Task? = null
        val working = statusUpdate(TaskState.TASK_STATE_WORKING)

        val turn = turnManager.start(taskId, contextId, followUp = true) {
            storedWhenAgentStarted = taskStorage.get(taskId)
            it.sendTaskEvent(working)
        }
        drain(turn)
        turn.join()

        assertEquals(inputRequired, storedWhenAgentStarted)
        assertEquals(inputRequired, turn.taskSnapshot)
        assertEquals(
            inputRequired.copy(
                status = working.status,
                history = listOf(followUpPrompt(), userMessage),
            ),
            taskStorage.get(taskId)
        )
    }

    @Test
    fun testFollowUpTurnWithoutTaskEventsDoesNotPersistUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()

        val turn = turnManager.start(taskId, contextId, followUp = true) {}
        drain(turn)
        turn.join()

        assertEquals(inputRequired, taskStorage.get(taskId))
    }

    /** Asserts that a follow-up turn fails with [expected] without running the agent, and leaves the task usable. */
    private suspend fun TestScope.assertFollowUpFails(
        expected: A2AException,
        turnManager: TurnManager,
        startFollowUp: suspend TurnManager.(agent: suspend (TurnEventProcessor) -> Unit) -> Turn,
    ) {
        val stored = taskStorage.get(taskId)
        var agentStarted = false

        val failure = assertFailsWith<A2AException> { turnManager.startFollowUp { agentStarted = true } }
        advanceUntilIdle()

        assertEquals(expected::class to expected.message, failure::class to failure.message)
        assertEquals(false, agentStarted)
        assertNull(turnManager.getTurn(taskId))
        assertEquals(stored, taskStorage.get(taskId))
    }

    @Test
    fun testFollowUpTurnOfTerminalTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask(state = TaskState.TASK_STATE_CANCELED)

        assertFollowUpFails(
            A2AUnsupportedOperationException(
                "Task '$taskId' is in terminal state TASK_STATE_CANCELED and can't accept messages"
            ),
            turnManager,
        ) { agent -> start(taskId, contextId, followUp = true, block = agent) }
    }

    @Test
    fun testFollowUpTurnOfUnknownTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()

        assertFollowUpFails(A2ATaskNotFoundException("Task '$taskId' not found"), turnManager) { agent ->
            start(taskId, contextId, followUp = true, block = agent)
        }
    }

    @Test
    fun testFollowUpTurnWithOtherContextIdFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()

        assertFollowUpFails(
            A2AInvalidParamsException(
                "Message contextId 'other-context' doesn't match contextId '$contextId' of task '$taskId'"
            ),
            turnManager,
        ) { agent -> start(taskId, "other-context", followUp = true, block = agent) }

        // The slot is free again: the next follow-up starts.
        val turn = turnManager.start(taskId, contextId, followUp = true) {}
        drain(turn)
        turn.join()
    }

    @Test
    fun testFollowUpAgentGetsFullTaskAndSnapshotAppliesHistoryLength() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val history = listOf(userMessage("Hi", messageId = "user-0"), followUpPrompt())
        val stored = task(TaskState.TASK_STATE_INPUT_REQUIRED).copy(history = history)
        taskStorage.update(stored)
        var agentTask: Task? = null
        var processorContextId: String? = null

        val request = SendMessageRequest(
            message = userMessage("Hello", messageId = "user-msg").copy(taskId = taskId),
            configuration = SendMessageConfiguration(historyLength = 1),
        )
        val turn = turnManager.startTurn(request, ctx, taskId) { task, processor ->
            agentTask = task
            processorContextId = processor.contextId
        }
        drain(turn)
        turn.join()

        assertEquals(stored, agentTask)
        assertEquals(stored.copy(history = listOf(followUpPrompt())), turn.taskSnapshot)
        assertEquals(contextId to contextId, processorContextId to turn.contextId)
    }

    /** Records the messages [generateContextId] is called with. */
    private class RecordingContextIdGenerator : IdGenerator {
        val contextIdRequests = mutableListOf<Message>()

        override suspend fun generateContextId(message: Message): String {
            contextIdRequests += message
            return "generated-context"
        }

        override suspend fun generateTaskId(message: Message): String = error("Not expected")
        override suspend fun generateMessageId(taskId: String, contextId: String): String = FixedMessageIdGenerator.MESSAGE_ID
    }

    @Test
    fun testFollowUpWithoutMessageContextIdDoesNotGenerateContextId() = runTest(timeout = TEST_TIMEOUT) {
        val idGenerator = RecordingContextIdGenerator()
        val turnManager = TurnManager(backgroundScope, taskStorage, pushConfigStorage, pushSender, idGenerator)
        storeTask()
        var processorContextId: String? = null

        val request = SendMessageRequest(userMessage("Hello", messageId = "user-msg").copy(contextId = null, taskId = taskId))
        val turn = turnManager.startTurn(request, ctx, taskId) { _, processor ->
            processorContextId = processor.contextId
        }
        drain(turn)
        turn.join()

        assertEquals(emptyList(), idGenerator.contextIdRequests)
        assertEquals(contextId to contextId, processorContextId to turn.contextId)
    }

    @Test
    fun testNewTaskWithoutContextIdGeneratesContextIdOnce() = runTest(timeout = TEST_TIMEOUT) {
        val idGenerator = RecordingContextIdGenerator()
        val turnManager = TurnManager(backgroundScope, taskStorage, pushConfigStorage, pushSender, idGenerator)
        var processorContextId: String? = null

        val message = userMessage("Hello", messageId = "user-msg").copy(contextId = null)
        val turn = turnManager.startTurn(SendMessageRequest(message), ctx, taskId) { _, processor ->
            processorContextId = processor.contextId
        }
        drain(turn)
        turn.join()

        assertEquals(listOf(message), idGenerator.contextIdRequests)
        assertEquals("generated-context" to "generated-context", processorContextId to turn.contextId)
    }

    /**
     * Runs a follow-up turn of an input-required task that has a push config and a subscriber, with [agent] failing
     * with [error] before its first event, and asserts that the turn changed nothing.
     */
    private suspend fun TestScope.assertFollowUpRejectedWith(error: A2AException, agent: suspend (TurnEventProcessor) -> Unit) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        savePushConfig()
        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val turn = turnManager.start(taskId, contextId, followUp = true, block = agent)

        assertEquals(emptyList(), turn.events.toList())
        val outcome = assertIs<TurnOutcome.RequestError>(turn.outcome.await())
        assertEquals(error::class to error.message, outcome.cause::class to outcome.cause.message)
        turn.join()
        runCurrent()

        // Neither the user message nor a failed status is persisted, delivered, or pushed.
        assertEquals(inputRequired, taskStorage.get(taskId))
        assertEquals(listOf<Event>(inputRequired), received)
        assertTrue(subscriber.isActive)
        assertEquals(emptyList(), pushSender.started)

        subscriber.cancelAndJoin()
    }

    @Test
    fun testA2AErrorBeforeFirstEventOfFollowUpLeavesTaskUnchanged() = runTest(timeout = TEST_TIMEOUT) {
        val error = A2AContentTypeNotSupportedException("Unsupported content type")

        assertFollowUpRejectedWith(error) { throw error }
    }

    @Test
    fun testInternalA2AErrorBeforeFirstEventOfFollowUpLeavesTaskUnchanged() = runTest(timeout = TEST_TIMEOUT) {
        val error = A2AInternalErrorException("Agent error")

        assertFollowUpRejectedWith(error) { throw error }
    }

    @Test
    fun testRejectedAndIgnoredEventsDoNotCountAsFirstEventOfFollowUp() = runTest(timeout = TEST_TIMEOUT) {
        val error = A2AContentTypeNotSupportedException("Unsupported content type")

        assertFollowUpRejectedWith(error) { processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            assertFailsWith<InvalidEventException> {
                processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING, contextId = "other-context"))
            }
            throw error
        }
    }

    @Test
    fun testNonA2AErrorBeforeFirstEventOfFollowUpMarksTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        listOf(IllegalStateException("boom"), CancellationException("agent gave up")).forEach { error ->
            taskStorage = InMemoryTaskStorage()
            val turnManager = createManager()
            val inputRequired = storeInputRequiredTask()

            val turn = turnManager.start(taskId, contextId, followUp = true) { throw error }

            val events = turn.events.toList()
            assertIs<TurnOutcome.Failed>(turn.outcome.await(), error.toString())
            turn.join()

            // The user message is not persisted, the status message moves into the history.
            val stored = assertNotNull(taskStorage.get(taskId))
            val failedStatus = failedStatus(taskId, contextId, stored.status.timestamp)
            val failedEvent = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = failedStatus)
            assertEquals(listOf<Event>(failedEvent), events, error.toString())
            assertEquals(inputRequired.copy(status = failedStatus, history = listOf(followUpPrompt())), stored, error.toString())
        }
    }

    private suspend fun savePushConfig() {
        pushConfigStorage.save(pushConfig(taskId, "c1"), ctx)
    }

    @Test
    fun testMessageReplyIsNotPushed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        savePushConfig()

        turnManager.start(taskId, contextId) { processor ->
            processor.sendMessage(userMessage("Hi", messageId = "agent-msg"))
        }.also { drain(it) }.join()
        runCurrent()

        assertEquals(emptyList(), pushSender.started)
    }

    @Test
    fun testIgnoredTaskOfExistingTaskIsNotPushed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        savePushConfig()
        val working = statusUpdate(TaskState.TASK_STATE_WORKING)

        turnManager.start(taskId, contextId, followUp = true) { processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(working)
        }.also { drain(it) }.join()
        runCurrent()

        // Neither the ignored Task nor the user message added to the history is pushed.
        assertEquals(listOf<Pair<String, Event>>("c1" to working), pushSender.sent)
    }

    @Test
    fun testBlockedPushSenderDoesNotBlockAgentStreamsOrTurnEnd() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()
        savePushConfig()
        pushSender.block("c1")

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        val requesterEvents = drain(turn)

        withTimeout(5.seconds) {
            turn.join()
            subscriber.join()
        }

        assertEquals(listOf<Event>(inputRequired, working, completed), received)
        assertEquals(listOf<Event>(working, completed), requesterEvents.await())
        assertEquals(listOf<Pair<String, Event>>("c1" to working), pushSender.started)

        pushSender.unblock("c1")
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to working, "c1" to completed), pushSender.sent)
    }

    /** Starts the SubscribeToTask flow in a new coroutine. [onEvent] is called for every received event. */
    private fun TestScope.subscribeWith(
        turnManager: TurnManager,
        onEvent: suspend (Event) -> Unit,
    ): Job = launch {
        turnManager.subscribe(SubscribeToTaskRequest(taskId), ctx).collect { onEvent(it) }
    }

    @Test
    fun testCancellingRequesterViewUnblocksAgent() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()
        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            gate.await()
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        // The requester goes away after the first event (e.g. returnImmediately, or the client disconnects).
        val firstEvent = turn.events.first()
        gate.complete(Unit)
        turn.join()

        assertEquals(task(TaskState.TASK_STATE_WORKING).withUserMessage(), firstEvent)
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskId)?.status?.state)
    }

    /**
     * Makes the stream of [taskId] stuck: the stream lock is held by a fan-out waiting for a subscriber that doesn't
     * consume. Returns the job of the stuck subscriber; cancelling it unblocks the fan-out.
     */
    private fun TestScope.stickStream(turnManager: TurnManager, taskId: String, contextId: String): Job {
        val stuck = launch {
            turnManager.subscribe(SubscribeToTaskRequest(taskId), ctx).collect {
                CompletableDeferred<Unit>().await() // never consumes anything more
            }
        }
        runCurrent()

        launch {
            turnManager.withTaskProcessor(GetTaskRequest(taskId), ctx) { _, processor, _, _ ->
                processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING, taskId = taskId, contextId = contextId))
            }
        }
        runCurrent()

        return stuck
    }

    @Test
    fun testStuckFanOutOfOneTaskDoesNotBlockOtherTasks() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val taskA = "task-a"
        val contextA = "context-a"
        val taskB = "task-b"
        val contextB = "context-b"
        storeTask(taskA, contextA)
        storeTask(taskB, contextB)

        val stuck = stickStream(turnManager, taskA, contextA)

        // A follow-up turn for A has to wait for A's stuck stream (whose publisher holds A's turn gate).
        val completedA = statusUpdate(TaskState.TASK_STATE_COMPLETED, taskId = taskA, contextId = contextA)
        val startA = async {
            turnManager.start(taskA, contextA) { processor ->
                processor.sendTaskEvent(completedA)
            }
        }
        runCurrent()
        assertEquals(false, startA.isCompleted)

        // The turn of A isn't visible until its view is registered.
        assertNull(turnManager.getTurn(taskA))

        // Everything for the other task, and the registry queries, go through while A is stuck.
        val messageB = userMessage("Hello B", contextId = contextB, messageId = "msg-b")
        val operationsB = async {
            val subscriptionB = turnManager.subscribe(SubscribeToTaskRequest(taskB), ctx).first()
            val turnB = turnManager.start(taskB, contextB) { processor ->
                processor.sendMessage(messageB)
            }
            val turnLookup = turnManager.getTurn(taskB)
            val eventsB = turnB.events.toList()
            turnB.join()

            listOf(subscriptionB, turnLookup === turnB, eventsB, turnManager.activeTurns())
        }
        runCurrent()

        assertEquals(true, operationsB.isCompleted)
        assertEquals(
            listOf(
                task(TaskState.TASK_STATE_INPUT_REQUIRED, taskId = taskB, contextId = contextB),
                true,
                listOf<Event>(messageB),
                0, // B has ended, and A is still starting
            ),
            operationsB.await()
        )
        assertEquals(false, startA.isCompleted)

        // Unblocking A lets everything finish.
        stuck.cancelAndJoin()
        val turnA = startA.await()
        assertSame(turnA, turnManager.getTurn(taskA))
        val eventsA = drain(turnA)
        turnA.join()

        assertEquals(listOf<Event>(completedA), eventsA.await())
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskA)?.status?.state)
    }

    @Test
    fun testCancelledStartTurnReleasesReservation() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val stuck = stickStream(turnManager, taskId, contextId)

        var agentStarted = false
        val start = async {
            turnManager.start(taskId, contextId) { agentStarted = true }
        }
        runCurrent()
        assertEquals(false, start.isCompleted)
        assertNull(turnManager.getTurn(taskId))

        // The caller goes away while the turn is waiting to register its view.
        start.cancelAndJoin()

        assertNull(turnManager.getTurn(taskId))

        // The task can start a new turn, and the cancelled one never ran.
        stuck.cancelAndJoin()
        val turn = turnManager.start(taskId, contextId) { processor ->
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        drain(turn)
        turn.join()

        assertEquals(false, agentStarted)
    }
}
