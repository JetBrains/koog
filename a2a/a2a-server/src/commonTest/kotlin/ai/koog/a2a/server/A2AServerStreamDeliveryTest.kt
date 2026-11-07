package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How [A2AServer] delivers events to streams whose readers fall behind, see [StreamDeliveryConfig]: eviction, the
 * exempt blocking SendMessage, and how queued events end on shutdown.
 */
class A2AServerStreamDeliveryTest {
    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    /** A server whose in-process collectors of [onSendMessageCommon] take [collectorDelay] per event. */
    private class SlowCollectorServer(
        scripts: Map<String, AgentScript>,
        taskStorage: InMemoryTaskStorage,
        scope: CoroutineScope,
        streamDelivery: StreamDeliveryConfig,
        private val collectorDelay: kotlin.time.Duration,
    ) : A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts),
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator(),
        coroutineScope = scope,
        streamDelivery = streamDelivery,
    ) {
        override fun onSendMessageCommon(
            request: SendMessageRequest,
            ctx: ServerCallContext,
            includeTaskSnapshot: Boolean,
            viewDelivery: StreamDeliveryConfig,
        ): Flow<Event> = super.onSendMessageCommon(request, ctx, includeTaskSnapshot, viewDelivery)
            .onEach { delay(collectorDelay) }
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript>,
        streamDelivery: StreamDeliveryConfig,
        collectorDelay: kotlin.time.Duration = 0.milliseconds,
    ): A2AServer = SlowCollectorServer(scripts, taskStorage, backgroundScope, streamDelivery, collectorDelay)

    private val updates = List(5) { statusUpdate(TaskState.TASK_STATE_WORKING, "step-$it") }

    private val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

    /** Sends [updates], then completes the task. */
    private val manyScript = AgentScript { _, processor -> (updates + completed).forEach { processor.sendTaskEvent(it) } }

    /** Sets the task working, then waits for input: the turn ends without a terminal event. */
    private val inputScript = AgentScript { _, processor ->
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    private suspend fun storeIdleTask(): Task =
        task(TaskState.TASK_STATE_INPUT_REQUIRED).also { taskStorage.update(it, ctx) }

    private fun Throwable.described() = Triple(this::class, message, (this as? A2AException)?.details)

    private val evicted = Triple(
        A2AInternalErrorException::class,
        "Stream closed because the client is not reading events fast enough; resubscribe with SubscribeToTask or " +
            "call GetTask",
        listOf<ErrorData>(ErrorInfo(reason = "SLOW_CONSUMER", metadata = mapOf("taskId" to TASK_ID))),
    )

    private val shutDown = Triple(
        A2AInternalErrorException::class,
        "Server is shut down",
        listOf<ErrorData>(ErrorInfo(reason = "SERVER_SHUTDOWN", metadata = mapOf("taskId" to TASK_ID))),
    )

    /** Subscribes to [TASK_ID], holding the snapshot until [release] completes. */
    private fun TestScope.stalledSubscriber(server: A2AServer, release: CompletableDeferred<Unit>) = async {
        server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx)
            .onEach { if (it is Task) release.await() }
            .collectExpectingFailure()
    }

    @Test
    fun testStalledStreamingRequesterIsEvictedWhileTurnCompletes() = runTest(timeout = TEST_TIMEOUT) {
        val stored = storeIdleTask()
        val server = createServer(mapOf("many" to manyScript), StreamDeliveryConfig.Evict(capacity = 2, timeout = 5.seconds))
        val release = CompletableDeferred<Unit>()

        // Holds the snapshot, emitted with the first update; two more updates fit into the queue.
        val requester = async {
            server.onSendMessageStreaming(request("many", TASK_ID), ctx)
                .onEach { if (it is Task) release.await() }
                .collectExpectingFailure()
        }
        // Past the timeout of the update that found the queue full.
        advanceTimeBy(6.seconds)
        runCurrent()

        // The turn has gone on without the requester.
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertNotNull(taskStorage.read(TASK_ID)).status.state)

        release.complete(Unit)
        val (failure, received) = requester.await()
        assertEquals(listOf<Event>(stored) + updates.take(3), received)
        assertEquals(evicted, failure.described())
    }

    @Test
    fun testFailedTurnEndsWithinTimeoutDespiteStalledRequester() = runTest(timeout = TEST_TIMEOUT) {
        val failScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            throw IllegalStateException("Agent crashed")
        }
        val server = createServer(mapOf("fail" to failScript), StreamDeliveryConfig.Evict(capacity = 0, timeout = 5.seconds))
        val release = CompletableDeferred<Unit>()
        val requester = async {
            server.onSendMessageStreaming(request("fail"), ctx).onEach { release.await() }.collectExpectingFailure()
        }
        runCurrent()

        // The FAILED update evicts the stalled requester after the timeout, so the turn ends and the drain completes.
        val start = currentTime
        server.shutdown(1.hours)
        assertEquals(5.seconds.inWholeMilliseconds, currentTime - start)
        assertEquals(TaskState.TASK_STATE_FAILED, assertNotNull(taskStorage.read(TASK_ID)).status.state)

        release.complete(Unit)
        val (failure, received) = requester.await()
        val created = task(TaskState.TASK_STATE_WORKING).copy(history = listOf(request("fail").message.copy(taskId = TASK_ID)))
        assertEquals(evicted to listOf<Event>(created), failure.described() to received)
    }

    @Test
    fun testBlockingSendMessageWaitsForSlowCollectorInsteadOfEvicting() = runTest(timeout = TEST_TIMEOUT) {
        storeIdleTask()
        val server = createServer(
            mapOf("many" to manyScript),
            StreamDeliveryConfig.Evict(capacity = 0, timeout = 10.milliseconds),
            collectorDelay = 1.seconds,
        )

        val response = server.onSendMessage(request("many", TASK_ID), ctx)

        assertEquals(assertNotNull(taskStorage.read(TASK_ID)), response)
        assertEquals(TaskState.TASK_STATE_COMPLETED, (response as Task).status.state)

        // The same slow collector is evicted from a streaming response: it takes the snapshot and the first update.
        runCurrent()
        val stored = storeIdleTask()
        val (failure, received) = server.onSendMessageStreaming(request("many", TASK_ID), ctx).collectExpectingFailure()
        assertEquals(evicted to listOf<Event>(stored, updates[0]), failure.described() to received)
    }

    @Test
    fun testGracefulShutdownDeliversQueuedEventsBeforeShutdownError() = runTest(timeout = TEST_TIMEOUT) {
        val stored = storeIdleTask()
        val server = createServer(mapOf("input" to inputScript), StreamDeliveryConfig.Evict(capacity = 8, timeout = 1.hours))
        val release = CompletableDeferred<Unit>()
        val subscriber = stalledSubscriber(server, release)
        runCurrent()

        // The turn ends with its events queued for the stalled subscriber, so the drain completes.
        server.onSendMessage(request("input", TASK_ID), ctx)
        server.shutdown(1.minutes)

        release.complete(Unit)
        val (failure, received) = subscriber.await()
        assertEquals(
            listOf<Event>(stored, statusUpdate(TaskState.TASK_STATE_WORKING), statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED)),
            received,
        )
        assertEquals(shutDown, failure.described())
    }

    @Test
    fun testCancelDropsQueuedEventsOfOpenStream() = runTest(timeout = TEST_TIMEOUT) {
        val stored = storeIdleTask()
        val server = createServer(mapOf("input" to inputScript), StreamDeliveryConfig.Evict(capacity = 8, timeout = 1.hours))
        val release = CompletableDeferred<Unit>()
        val subscriber = stalledSubscriber(server, release)
        runCurrent()

        server.onSendMessage(request("input", TASK_ID), ctx)
        server.cancel()

        release.complete(Unit)
        val (failure, received) = subscriber.await()
        assertEquals(listOf<Event>(stored), received)
        assertEquals(shutDown, failure.described())
    }

    @Test
    fun testCancelKeepsQueuedTerminalEventOfStreamEndedBefore() = runTest(timeout = TEST_TIMEOUT) {
        val stored = storeIdleTask()
        val server = createServer(mapOf("many" to manyScript), StreamDeliveryConfig.Evict(capacity = 8, timeout = 1.hours))
        val release = CompletableDeferred<Unit>()
        val subscriber = async {
            val received = mutableListOf<Event>()
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collect {
                if (it is Task) release.await()
                received += it
            }
            received
        }
        runCurrent()

        // The terminal event is queued, and the stream closed after it, before the shutdown.
        server.onSendMessage(request("many", TASK_ID), ctx)
        runCurrent()
        server.cancel()

        release.complete(Unit)
        assertEquals(listOf<Event>(stored) + updates + completed, subscriber.await())
    }

    @Test
    fun testCancelledStuckSubscriberUnblocksAgent() = runTest(timeout = TEST_TIMEOUT) {
        val stored = storeIdleTask()
        var sent = 0
        val countingScript = AgentScript { _, processor ->
            (updates + completed).forEach {
                processor.sendTaskEvent(it)
                sent++
            }
        }
        val server = createServer(mapOf("count" to countingScript), StreamDeliveryConfig.Suspend(capacity = 2))

        // Never reads past the snapshot, so the agent can publish only what fits into the queue.
        val stuck = subscribe(server) { if (it is Task) awaitCancellation() }
        stuck.firstEvent.await()
        val response = async { server.onSendMessage(request("count", TASK_ID), ctx) }
        runCurrent()

        assertEquals(2, sent)
        assertEquals(false, response.isCompleted)
        assertEquals(TaskState.TASK_STATE_WORKING, assertNotNull(taskStorage.read(TASK_ID)).status.state)

        // The client disconnects.
        stuck.job.cancelAndJoin()

        val finished = response.await()
        assertEquals(assertNotNull(taskStorage.read(TASK_ID)), finished)
        assertEquals(TaskState.TASK_STATE_COMPLETED, (finished as Task).status.state)
        assertEquals(updates.size + 1, sent)
        assertEquals(listOf<Event>(stored), stuck.events)
    }

    @Test
    fun testRequestRejectedOnShutdownCarriesShutdownErrorInfo() = runTest(timeout = TEST_TIMEOUT) {
        storeIdleTask()
        val server = createServer(emptyMap(), StreamDeliveryConfig.Evict())
        server.cancel()

        val failure = assertNotNull(runCatching { server.onSendMessage(request("any", TASK_ID), ctx) }.exceptionOrNull())

        assertEquals(
            Triple(
                A2AInternalErrorException::class,
                "Server is shut down",
                listOf<ErrorData>(ErrorInfo(reason = "SERVER_SHUTDOWN")),
            ),
            failure.described(),
        )
    }
}
