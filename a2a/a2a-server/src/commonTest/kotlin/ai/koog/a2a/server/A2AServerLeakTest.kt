@file:OptIn(InternalA2AApi::class)

package ai.koog.a2a.server

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One test per request path: once the request and every subscriber are done, the server holds no running turn and no
 * task stream in memory.
 */
class A2AServerLeakTest {
    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private class ObservedServer(
        agentExecutor: ScriptedAgentExecutor,
        taskStorage: InMemoryTaskStorage,
        scope: CoroutineScope,
    ) : A2AServer(
        agentExecutor = agentExecutor,
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator(messageId = "generated-message-id"),
        coroutineScope = scope,
    ) {
        /** The number of running turns and of task streams held in memory. */
        suspend fun held(): Pair<Int, Int> = turnManager.activeTurns() to turnManager.activeStreams()
    }

    private val started = CompletableDeferred<Unit>()

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val startScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "start"))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    private val finishScript = AgentScript { _, processor ->
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
    }

    private val failScript = AgentScript { _, processor ->
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        throw IllegalStateException("Agent crashed")
    }

    /** Creates the task and keeps running until the server interrupts it. */
    private val runScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "run"))
        started.complete(Unit)
        awaitCancellation()
    }

    private val cancelHandler = CancelHandler { _, _ -> }

    private fun TestScope.createServer() = ObservedServer(
        agentExecutor = ScriptedAgentExecutor(
            scripts = mapOf("start" to startScript, "finish" to finishScript, "fail" to failScript, "run" to runScript),
            cancelHandler = cancelHandler,
        ),
        taskStorage = taskStorage,
        scope = backgroundScope,
    )

    @Test
    fun testSendMessageReleasesTurnAndStream() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer()

        server.onSendMessage(request("start"), ctx)
        server.onSendMessage(request("finish", TASK_ID), ctx)
        runCurrent()

        assertEquals(0 to 0, server.held())
    }

    @Test
    fun testSendStreamingMessageReleasesTurnAndStream() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer()

        server.onSendMessageStreaming(request("start"), ctx).toList()
        server.onSendMessageStreaming(request("finish", TASK_ID), ctx).toList()
        runCurrent()

        assertEquals(0 to 0, server.held())
    }

    @Test
    fun testSubscribeToTaskReleasesStream() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer()
        server.onSendMessage(request("start"), ctx)

        // A cancelled subscriber of an idle task.
        subscribe(server).also { it.firstEvent.await() }.job.cancelAndJoin()
        runCurrent()

        assertEquals(0 to 0, server.held())

        // A subscriber ended by the terminal state of the task.
        val subscription = subscribe(server).also { it.firstEvent.await() }
        server.onSendMessage(request("finish", TASK_ID), ctx)
        subscription.job.join()
        runCurrent()

        assertEquals(0 to 0, server.held())
    }

    @Test
    fun testCancelTaskReleasesTurnAndStream() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer()
        val response = async { server.onSendMessage(request("run"), ctx) }
        started.await()

        server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        response.await()
        runCurrent()

        assertEquals(0 to 0, server.held())
    }

    @Test
    fun testFailedTurnReleasesTurnAndStream() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer()
        server.onSendMessage(request("start"), ctx)
        val subscription = subscribe(server).also { it.firstEvent.await() }

        server.onSendMessage(request("fail", TASK_ID), ctx)
        subscription.job.join()
        runCurrent()

        assertEquals(0 to 0, server.held())
    }
}
