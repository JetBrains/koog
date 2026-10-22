package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.A2AMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [A2AServer.cancel] and the shutdown state: which requests are rejected, and which scope is cancelled.
 */
class A2AServerShutdownTest {
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript> = emptyMap(),
        scope: CoroutineScope = backgroundScope,
    ) = A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts),
        agentCard = testAgentCard(AgentCapabilities(streaming = true, pushNotifications = true, extendedAgentCard = true)),
        agentCardExtended = testAgentCard(name = "Extended agent"),
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = GatedPushNotificationSender(),
        idGenerator = FixedIdGenerator(),
        coroutineScope = scope,
    )

    /** The class and message of the failure of each operation of [server]. */
    private suspend fun failures(server: A2AServer): Map<A2AMethod, Pair<Any, String?>?> {
        val operations = operations(server)

        return A2AMethod.entries.associateWith { method ->
            runCatching { operations.getValue(method)(ctx) }.exceptionOrNull()?.let { it::class to it.message }
        }
    }

    private val shutDown = A2AInternalErrorException::class to "Server is shut down"

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
        assertEquals(
            task(TaskState.TASK_STATE_WORKING).copy(history = listOf(request("run").message.copy(taskId = TASK_ID))),
            taskStorage.get(TASK_ID),
        )
    }
}
