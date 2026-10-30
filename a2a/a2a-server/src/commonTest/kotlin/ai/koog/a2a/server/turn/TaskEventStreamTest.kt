package ai.koog.a2a.server.turn

import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.CONTEXT_ID
import ai.koog.a2a.server.StreamDeliveryConfig
import ai.koog.a2a.server.TASK_ID
import ai.koog.a2a.server.TEST_TIMEOUT
import ai.koog.a2a.server.agentMessage
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.statusUpdate
import ai.koog.a2a.server.task
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TaskEventStreamTest {
    private val taskId = TASK_ID
    private val contextId = CONTEXT_ID

    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private fun createStream() = TaskEventStream(taskId, taskStorage, FixedMessageIdGenerator)

    /** Delivery is back-pressured by default, so a channel must be consumed while events are published. */
    private fun TestScope.collect(channel: ReceiveChannel<Event>): Deferred<List<Event>> = async { channel.toList() }

    private suspend fun TaskEventStream.subscribeToStored(
        delivery: StreamDeliveryConfig = StreamDeliveryConfig.Suspend(),
    ): Pair<Task, ReceiveChannel<Event>> =
        subscribe(delivery) { taskStorage.get(taskId) }.let { (task, subscriber) -> task to subscriber.events }

    @Test
    fun testSubscribeReturnsSnapshotAndSubsequentEventsUntilTerminal() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val inputRequired = task(TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val (snapshot, channel) = stream.subscribeToStored()
        val events = collect(channel)

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(working)
        stream.publish(completed)

        assertEquals(inputRequired, snapshot)
        // The channel is closed after the terminal event, so the collection completes.
        assertEquals(listOf(working, completed), events.await())
    }

    @Test
    fun testSubscribeToUnknownOrTerminalTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        assertFailsWith<A2ATaskNotFoundException> {
            createStream().subscribeToStored()
        }

        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED))

        assertFailsWith<A2AUnsupportedOperationException> {
            createStream().subscribeToStored()
        }
    }

    @Test
    fun testFollowUpMessageIsPersistedBeforeFirstTaskEventMovingStatusMessageIntoHistory() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val firstUser = userMessage("user-1", taskId)
        val prompt = agentMessage("prompt")
        val artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("result")))
        val inputRequired = task(TaskState.TASK_STATE_INPUT_REQUIRED).copy(
            status = TaskStatus(
                state = TaskState.TASK_STATE_INPUT_REQUIRED,
                message = prompt,
                timestamp = Instant.parse("2025-01-01T10:00:00Z"),
            ),
            history = listOf(firstUser),
            artifacts = listOf(artifact),
        )
        taskStorage.update(inputRequired)

        val secondUser = userMessage("user-2", taskId)
        stream.openTurnViewChannel().cancel()

        // Nothing is persisted until the agent sends an event.
        assertEquals(inputRequired, taskStorage.get(taskId))

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        stream.publish(working, followUpMessage = secondUser)

        val expected = inputRequired.copy(
            status = working.status,
            history = listOf(firstUser, prompt, secondUser),
        )
        assertEquals(expected, taskStorage.get(taskId))
    }

    @Test
    fun testFollowUpMessageAlreadyInHistoryIsNotDuplicated() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val user = userMessage("user-1", taskId)
        val prompt = agentMessage("prompt")
        val stored = task(TaskState.TASK_STATE_INPUT_REQUIRED).copy(
            status = TaskStatus(state = TaskState.TASK_STATE_INPUT_REQUIRED, message = prompt),
            history = listOf(user),
        )
        taskStorage.update(stored)

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        stream.publish(working, followUpMessage = user)

        assertEquals(
            stored.copy(status = working.status, history = listOf(user, prompt)),
            taskStorage.get(taskId),
        )
    }

    @Test
    fun testPublishedTaskGetsUserMessageAppendedToHistory() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val user = userMessage("user-1", taskId)
        val turnView = collect(stream.openTurnViewChannel())

        val applied = stream.publish(task(TaskState.TASK_STATE_WORKING), userMessage = user)
        stream.endTurn(contextId, outcome = TurnOutcome.Completed(rejection = null))

        val expected = task(TaskState.TASK_STATE_WORKING).copy(history = listOf(user))
        assertTrue(applied)
        assertEquals(expected, taskStorage.get(taskId))
        assertEquals(listOf<Event>(expected), turnView.await())
    }

    @Test
    fun testPublishedTaskDoesNotDuplicateUserMessageAlreadyInHistory() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val user = userMessage("user-1", taskId)
        val created = task(TaskState.TASK_STATE_WORKING).copy(history = listOf(user))

        stream.publish(created, userMessage = user.copy(parts = listOf(TextPart("changed"))))

        assertEquals(created, taskStorage.get(taskId))
    }

    @Test
    fun testTaskEventForExistingTaskIsIgnored() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val inputRequired = task(TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)
        val subscriber = collect(stream.subscribeToStored().second)

        val applied = stream.publish(task(TaskState.TASK_STATE_COMPLETED), userMessage = userMessage("user-1", taskId))
        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(working)
        stream.publish(completed)

        assertFalse(applied)
        // The ignored terminal Task neither closed the subscriber nor reached it.
        assertEquals(listOf(working, completed), subscriber.await())
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskId)?.status?.state)
        assertEquals(listOfNotNull(working.status.message), taskStorage.get(taskId)?.history)
    }

    @Test
    fun testEndTurnClosesOnlyItsTurnView() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val turnView = collect(stream.openTurnViewChannel())
        val subscriber = collect(stream.subscribeToStored().second)

        val inputRequired = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED)
        stream.publish(inputRequired)
        stream.endTurn(contextId, outcome = TurnOutcome.Completed(rejection = null))

        assertEquals(listOf(inputRequired), turnView.await())

        // The task subscriber stays open across turns and receives the next turn's events.
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(completed)

        assertEquals(listOf(inputRequired, completed), subscriber.await())
    }

    @Test
    fun testTurnViewOpensOnlyWhenNoOtherTurnViewIsOpen() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()

        val firstView = stream.openTurnViewChannel()
        assertFailsWith<IllegalStateException> { stream.openTurnViewChannel() }
        stream.endTurn(contextId, outcome = TurnOutcome.Completed(rejection = null))
        assertEquals(emptyList(), firstView.toList())

        // A view whose consumer has gone away, e.g. of a turn that didn't start, doesn't count.
        stream.openTurnViewChannel().cancel()

        val nextView = collect(stream.openTurnViewChannel())
        val message = agentMessage("reply")
        stream.publish(message)
        stream.endTurn(contextId, outcome = TurnOutcome.Completed(rejection = null))

        assertEquals(listOf<Event>(message), nextView.await())
    }

    @Test
    fun testEndTurnWithFailureMarksTaskFailedAndClosesAllSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val turnView = collect(stream.openTurnViewChannel())
        val subscriber = collect(stream.subscribeToStored().second)

        stream.endTurn(contextId, outcome = TurnOutcome.Failed(IllegalStateException("boom")))

        val stored = assertNotNull(taskStorage.get(taskId))
        assertNotNull(stored.status.timestamp)

        val expectedStatus = failedStatus(taskId, contextId, stored.status.timestamp)
        assertEquals(expectedStatus, stored.status)

        val failed = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = expectedStatus)
        assertEquals(listOf(failed), turnView.await())
        assertEquals(listOf(failed), subscriber.await())
    }

    @Test
    fun testEndTurnWithRejectionMarksTaskFailedWithoutDeliveringItToTurnView() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val turnView = collect(stream.openTurnViewChannel())
        val subscriber = collect(stream.subscribeToStored().second)

        stream.endTurn(contextId, outcome = TurnOutcome.Rejected(InvalidEventException("invalid")))

        val stored = assertNotNull(taskStorage.get(taskId))
        val expectedStatus = failedStatus(taskId, contextId, assertNotNull(stored.status.timestamp))
        assertEquals(expectedStatus, stored.status)

        // The requester gets an error instead, see TurnOutcome.Rejected.
        assertEquals(emptyList(), turnView.await())
        val failed = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = expectedStatus)
        assertEquals(listOf(failed), subscriber.await())
    }

    @Test
    fun testEndTurnWithRequestErrorLeavesTaskAndSubscribersUnchanged() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val inputRequired = task(TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val turnView = collect(stream.openTurnViewChannel())
        val subscriber = collect(stream.subscribeToStored().second)

        stream.endTurn(contextId, outcome = TurnOutcome.RequestError(A2AContentTypeNotSupportedException()))

        assertEquals(inputRequired, taskStorage.get(taskId))
        assertEquals(emptyList(), turnView.await())

        // The task subscriber is still open and receives the next event.
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(completed)
        assertEquals(listOf(completed), subscriber.await())
    }

    @Test
    fun testEndTurnWithFailureKeepsTerminalTaskAndCancellationDoesNotMarkTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        listOf(
            task(TaskState.TASK_STATE_COMPLETED) to TurnOutcome.Failed(IllegalStateException("boom")),
            task(TaskState.TASK_STATE_WORKING) to TurnOutcome.of(CancellationException("canceled"), rejection = null, isEventPublished = false),
        ).forEach { (stored, outcome) ->
            taskStorage = InMemoryTaskStorage().also { it.update(stored) }
            val stream = createStream()
            val turnView = stream.openTurnViewChannel()

            stream.endTurn(contextId, outcome = outcome)

            assertEquals(stored, taskStorage.get(taskId), stored.status.state.name)
            assertEquals(emptyList(), turnView.toList(), stored.status.state.name)
        }
    }

    @Test
    fun testPublishSuspendsUntilSubscriberAcceptsEvent() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val (_, subscriber) = stream.subscribeToStored()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val publish = launch { stream.publish(working) }
        advanceUntilIdle()

        // The event is persisted, but the publisher waits for the subscriber.
        assertTrue(publish.isActive)
        assertEquals(working.status, assertNotNull(taskStorage.get(taskId)).status)

        assertEquals(working, subscriber.receive())
        publish.join()
    }

    @Test
    fun testSlowSubscriberDelaysOthersButEveryoneReceivesEveryEventInOrder() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val updates = List(10) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val (_, slow) = stream.subscribeToStored()
        val (_, fast) = stream.subscribeToStored()

        val slowGate = CompletableDeferred<Unit>()
        val slowReceived = mutableListOf<Event>()
        val slowCollector = launch {
            for (event in slow) {
                slowReceived += event
                if (slowReceived.size == 1) slowGate.await()
            }
        }
        val fastReceived = mutableListOf<Event>()
        val fastCollector = launch { for (event in fast) fastReceived += event }

        val publisher = launch {
            (updates + completed).forEach { stream.publish(it) }
        }
        advanceUntilIdle()

        // The slow subscriber holds the first event, so the second one can't be delivered to anybody: the publisher
        // and the fast subscriber are paced by the slow one.
        assertEquals(listOf<Event>(updates[0]), slowReceived)
        assertEquals(listOf<Event>(updates[0]), fastReceived)
        assertTrue(publisher.isActive)

        slowGate.complete(Unit)
        publisher.join()
        slowCollector.join()
        fastCollector.join()

        assertEquals<List<Event>>(updates + completed, slowReceived)
        assertEquals<List<Event>>(updates + completed, fastReceived)
    }

    @Test
    fun testCancellingStuckSubscriberUnblocksPublisherAndOthers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        // The stuck subscriber never receives; it is registered first, so the active one waits behind it.
        val (_, stuck) = stream.subscribeToStored()
        val active = collect(stream.subscribeToStored().second)

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        val publisher = launch {
            stream.publish(working)
            stream.publish(completed)
        }
        advanceUntilIdle()
        assertTrue(publisher.isActive)

        // Cancelling needs no stream lock, so it works while the publisher holds it.
        stream.unsubscribe(stuck)
        publisher.join()

        assertEquals(listOf(working, completed), active.await())
    }

    @Test
    fun testCancelledPublisherReleasesStreamLock() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val (_, stuck) = stream.subscribeToStored()

        val publisher = launch { stream.publish(statusUpdate(TaskState.TASK_STATE_WORKING)) }
        advanceUntilIdle()
        assertTrue(publisher.isActive)

        publisher.cancelAndJoin()
        stream.unsubscribe(stuck)

        // The lock is free again: later operations don't hang.
        val (_, another) = stream.subscribeToStored()
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        val events = collect(another)
        stream.publish(completed)

        assertEquals(listOf(completed), events.await())
    }

    @Test
    fun testPublisherCancelledDuringTerminalFanOutStillClosesEverySubscriber() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        // The stuck subscriber is registered first, so the publisher never reaches the other one.
        val (_, stuck) = stream.subscribeToStored()
        val other = collect(stream.subscribeToStored().second)

        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        val publisher = launch { stream.publish(completed) }
        advanceUntilIdle()
        assertTrue(publisher.isActive)

        publisher.cancelAndJoin()
        advanceUntilIdle()

        // The task is terminal, so nothing would ever close the subscribers later.
        assertEquals(completed.status, assertNotNull(taskStorage.get(taskId)).status)
        assertTrue(other.isCompleted)
        assertEquals(emptyList(), other.await())
        assertTrue(stuck.receiveCatching().isClosed)
    }

    @Test
    fun testCommittedEventIsReportedEvenIfPublisherIsCancelledMeanwhile() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        lateinit var publisher: Job
        // Commits the event on another dispatcher while the publisher is cancelled: without protection, returning to
        // the cancelled publisher throws although the event is stored.
        val storage = object : TaskStorage by taskStorage {
            override suspend fun update(event: TaskEvent) = withContext(StandardTestDispatcher(testScheduler)) {
                taskStorage.update(event)
                publisher.cancel()
            }
        }
        val persisted = mutableListOf<TaskEvent>()
        val stream = TaskEventStream(taskId, storage, FixedMessageIdGenerator) { persisted += it }
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        publisher = launch { stream.publish(completed) }
        publisher.join()

        assertEquals(task(TaskState.TASK_STATE_WORKING).copy(status = completed.status), taskStorage.get(taskId))
        assertEquals(listOf<TaskEvent>(completed), persisted)
    }

    @Test
    fun testCancelledSubscriberDoesNotAffectOthers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val (_, cancelled) = stream.subscribeToStored()
        val active = collect(stream.subscribeToStored().second)

        cancelled.cancel()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(working)
        stream.publish(completed)

        assertEquals(listOf(working, completed), active.await())
    }

    private val evict = StreamDeliveryConfig.Evict(capacity = 2, timeout = 5.seconds)

    /** What an evicted subscriber fails with, see [StreamDeliveryConfig.Evict]. */
    private val evicted = Triple(
        A2AInternalErrorException::class,
        "Stream closed because the client is not reading events fast enough; resubscribe with SubscribeToTask or " +
            "call GetTask",
        listOf<ErrorData>(ErrorInfo(reason = "SLOW_CONSUMER", metadata = mapOf("taskId" to TASK_ID))),
    )

    private fun Throwable.described() = Triple(this::class, message, (this as? A2AException)?.details)

    /** Receives everything [channel] still delivers: the events and, if it ends with one, the failure. */
    private suspend fun drainChannel(channel: ReceiveChannel<Event>): Pair<List<Event>, Throwable?> {
        val received = mutableListOf<Event>()
        val failure = runCatching { for (event in channel) received += event }.exceptionOrNull()
        return received to failure
    }

    @Test
    fun testEvictedSubscriberGetsQueuedEventsThenErrorWhileOthersGetEverything() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val updates = List(3) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val (_, stalled) = stream.subscribeToStored(evict)
        val active = collect(stream.subscribeToStored(evict).second)

        // Two updates fit into the stalled queue; the third waits for the whole timeout, then evicts it.
        val start = currentTime
        updates.forEach { stream.publish(it) }
        assertEquals(5.seconds.inWholeMilliseconds, currentTime - start)

        // The evicted subscriber no longer holds anybody up.
        stream.publish(completed)
        assertEquals(5.seconds.inWholeMilliseconds, currentTime - start)

        assertEquals(updates + completed, active.await())
        val (received, failure) = drainChannel(stalled)
        assertEquals(updates.take(2), received)
        assertEquals(evicted, assertNotNull(failure).described())
    }

    @Test
    fun testEvictionDeadlineIsSharedByAllStalledSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val updates = List(3) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val stalled = List(3) { stream.subscribeToStored(evict).second }

        val start = currentTime
        updates.forEach { stream.publish(it) }

        // One timeout for the event that found all three queues full, not one per subscriber.
        assertEquals(5.seconds.inWholeMilliseconds, currentTime - start)
        stalled.forEach { channel ->
            val (received, failure) = drainChannel(channel)
            assertEquals(updates.take(2), received)
            assertEquals(evicted, assertNotNull(failure).described())
        }
    }

    @Test
    fun testTerminalEventWaitsForFullQueueLikeAnyOtherAndThenEvicts() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val updates = List(2) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val (_, stalled) = stream.subscribeToStored(evict)
        val active = collect(stream.subscribeToStored(evict).second)

        val start = currentTime
        (updates + completed).forEach { stream.publish(it) }

        assertEquals(5.seconds.inWholeMilliseconds, currentTime - start)
        assertEquals(completed.status, assertNotNull(taskStorage.get(taskId)).status)
        assertEquals(updates + completed, active.await())
        val (received, failure) = drainChannel(stalled)
        assertEquals(updates, received)
        assertEquals(evicted, assertNotNull(failure).described())
    }

    @Test
    fun testEvictWithoutQueueWaitsForSlowReaderAndEvictsStalledOne() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val updates = List(3) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val noQueue = StreamDeliveryConfig.Evict(capacity = 0, timeout = 5.seconds)

        // Slower than the publisher, but within the timeout.
        val (_, slowChannel) = stream.subscribeToStored(noQueue)
        val slow = async { slowChannel.consumeAsFlow().onEach { delay(1.seconds) }.toList() }
        // Takes the first event and then stops reading.
        val (_, stalled) = stream.subscribeToStored(noQueue)
        val stalledFirst = async { stalled.receive() }
        runCurrent()

        val start = currentTime
        updates.forEach { stream.publish(it) }
        stream.publish(statusUpdate(TaskState.TASK_STATE_COMPLETED))

        // The second update waits the whole timeout for the stalled reader; the slow one only delays the others.
        assertEquals(updates[0], stalledFirst.await())
        val (received, failure) = drainChannel(stalled)
        assertEquals(emptyList(), received)
        assertEquals(evicted, assertNotNull(failure).described())
        assertEquals(updates + statusUpdate(TaskState.TASK_STATE_COMPLETED), slow.await())
        assertEquals(7.seconds.inWholeMilliseconds, currentTime - start)
    }

    @Test
    fun testSuspendWithQueueBuffersAndThenWaitsWithoutDropping() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val updates = List(3) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        val (_, channel) = stream.subscribeToStored(StreamDeliveryConfig.Suspend(capacity = 2))

        // Two updates fit into the queue; the third waits for the reader however long it takes.
        val publisher = launch { (updates + completed).forEach { stream.publish(it) } }
        advanceTimeBy(1.hours)
        runCurrent()
        assertTrue(publisher.isActive)
        assertEquals(updates[2].status, assertNotNull(taskStorage.get(taskId)).status)

        assertEquals(updates + completed, channel.toList())
        publisher.join()
    }

    @Test
    fun testShutdownDropsQueuedEventsOnlyOfOpenSubscribersUnlessDrained() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))
        val queue = StreamDeliveryConfig.Evict(capacity = 4)
        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        val (_, dropped) = stream.subscribe(queue) { taskStorage.get(taskId) }
        val (_, drained) = stream.subscribe(queue) { taskStorage.get(taskId) }
        stream.publish(working)
        dropped.endOnShutdown(isDrained = false)
        drained.endOnShutdown(isDrained = true)

        // Already ended by the stream at the terminal event: the shutdown doesn't drop its queued terminal event.
        val (_, closed) = stream.subscribe(queue) { taskStorage.get(taskId) }
        stream.publish(completed)
        closed.endOnShutdown(isDrained = false)

        val shutDown = ServerShutdownException::class to "Server is shut down"
        val (droppedEvents, droppedFailure) = drainChannel(dropped.events)
        assertEquals(emptyList<Event>() to shutDown, droppedEvents to assertNotNull(droppedFailure).let { it::class to it.message })
        val (drainedEvents, drainedFailure) = drainChannel(drained.events)
        assertEquals(listOf<Event>(working) to shutDown, drainedEvents to assertNotNull(drainedFailure).let { it::class to it.message })
        assertEquals(listOf<Event>(completed) to null, drainChannel(closed.events))
    }
}
