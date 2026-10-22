package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.ctx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PushNotificationDispatcherTest {
    private lateinit var configStorage: InMemoryPushNotificationConfigStorage
    private lateinit var sender: GatedPushNotificationSender

    @BeforeTest
    fun setUp() {
        configStorage = InMemoryPushNotificationConfigStorage()
        sender = GatedPushNotificationSender()
    }

    private fun event(taskId: String, n: Int) = TaskStatusUpdateEvent(
        taskId = taskId,
        contextId = "context-1",
        status = TaskStatus(state = TaskState.TASK_STATE_WORKING, timestamp = Instant.fromEpochSeconds(n.toLong())),
    )

    private fun TestScope.createDispatcher(scope: CoroutineScope = backgroundScope) =
        PushNotificationDispatcher(scope, configStorage, sender)

    private suspend fun saveConfigs(taskId: String, vararg ids: String) {
        ids.forEach { configStorage.save(pushConfig(taskId, it), ctx) }
    }

    @Test
    fun testEventsOfTaskAreSentInOrder() = runTest {
        val dispatcher = createDispatcher()
        saveConfigs("task-1", "c1")
        sender.block("c1")
        val events = (1..3).map { event("task-1", it) }

        events.forEach { dispatcher.enqueue("task-1", it) }
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to events[0]), sender.started)

        sender.release("c1")
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to events[0]), sender.sent)
        assertEquals(listOf<Pair<String, Event>>("c1" to events[0], "c1" to events[1]), sender.started)

        sender.unblock("c1")
        runCurrent()

        assertEquals(pushes("c1", events), sender.sent)

        // A drained task gets a new worker for its next event.
        val next = event("task-1", 4)
        dispatcher.enqueue("task-1", next)
        runCurrent()

        assertEquals(pushes("c1", events + next), sender.sent)
    }

    @Test
    fun testEventIsSentToAllConfigsConcurrentlyBeforeNextEvent() = runTest {
        val dispatcher = createDispatcher()
        saveConfigs("task-1", "c1", "c2")
        sender.block("c1")
        val first = event("task-1", 1)
        val second = event("task-1", 2)

        dispatcher.enqueue("task-1", first)
        dispatcher.enqueue("task-1", second)
        runCurrent()

        // The stuck c1 doesn't delay c2, but the next event waits for both.
        assertEquals(listOf<Pair<String, Event>>("c1" to first, "c2" to first), sender.started)
        assertEquals(listOf<Pair<String, Event>>("c2" to first), sender.sent)

        sender.unblock("c1")
        runCurrent()

        assertEquals(
            listOf<Pair<String, Event>>("c2" to first, "c1" to first, "c1" to second, "c2" to second),
            sender.sent
        )
    }

    @Test
    fun testStuckTaskDoesNotDelayOtherTasks() = runTest {
        val dispatcher = createDispatcher()
        saveConfigs("task-a", "a1")
        saveConfigs("task-b", "b1")
        sender.block("a1")
        val eventsA = (1..2).map { event("task-a", it) }
        val eventsB = (1..2).map { event("task-b", it) }

        eventsA.forEach { dispatcher.enqueue("task-a", it) }
        eventsB.forEach { dispatcher.enqueue("task-b", it) }
        runCurrent()

        assertEquals(pushes("b1", eventsB), sender.sent)

        sender.unblock("a1")
        runCurrent()

        assertEquals(pushes("b1", eventsB) + pushes("a1", eventsA), sender.sent)
    }

    @Test
    fun testFailingSendDoesNotStopLaterEventsOrOtherConfigs() = runTest {
        val dispatcher = createDispatcher()
        saveConfigs("task-1", "c1", "c2")
        val events = (1..3).map { event("task-1", it) }
        sender.failure = { configId, event ->
            when {
                configId == "c1" && event == events[0] -> IllegalStateException("webhook down")
                // Not a cancellation of the dispatcher: must be treated as a failed send.
                configId == "c2" && event == events[1] -> CancellationException("stray cancellation")
                else -> null
            }
        }

        events.forEach { dispatcher.enqueue("task-1", it) }
        runCurrent()

        assertEquals(
            listOf<Pair<String, Event>>("c2" to events[0], "c1" to events[1], "c1" to events[2], "c2" to events[2]),
            sender.sent
        )
    }

    @Test
    fun testDeletedConfigGetsNoLaterEvents() = runTest {
        val dispatcher = createDispatcher()
        saveConfigs("task-1", "c1", "c2")
        sender.block("c1")
        val first = event("task-1", 1)
        val second = event("task-1", 2)

        dispatcher.enqueue("task-1", first)
        dispatcher.enqueue("task-1", second)
        runCurrent()

        configStorage.delete(DeleteTaskPushNotificationConfigRequest(taskId = "task-1", id = "c2"), ctx)
        sender.unblock("c1")
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c2" to first, "c1" to first, "c1" to second), sender.sent)
    }

    @Test
    fun testNoWorkerIsLeftAfterQueuesAreDrained() = runTest {
        val dispatcher = createDispatcher()
        saveConfigs("task-a", "a1")
        saveConfigs("task-b", "b1")
        sender.block("a1")
        sender.failure = { configId, _ -> if (configId == "b1") IllegalStateException("webhook down") else null }

        dispatcher.enqueue("task-a", event("task-a", 1))
        dispatcher.enqueue("task-b", event("task-b", 1))
        runCurrent()

        // Only the stuck task still has a worker; the one whose send failed has been drained.
        assertEquals(1, dispatcher.activeWorkers())

        sender.unblock("a1")
        runCurrent()

        assertEquals(0, dispatcher.activeWorkers())
    }

    @Test
    fun testCancelledScopeDropsQueuedEventsAndLeavesNoWorker() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val dispatcher = createDispatcher(scope)
        saveConfigs("task-1", "c1")
        sender.block("c1")
        val events = (1..3).map { event("task-1", it) }

        dispatcher.enqueue("task-1", events[0])
        dispatcher.enqueue("task-1", events[1])
        runCurrent()

        scope.cancel()
        runCurrent()

        assertEquals(0, dispatcher.activeWorkers())

        // An event queued after the shutdown doesn't leave a worker behind either.
        dispatcher.enqueue("task-1", events[2])
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to events[0]), sender.started)
        assertEquals(emptyList(), sender.sent)
        assertEquals(0, dispatcher.activeWorkers())
    }
}
