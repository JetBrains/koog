package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.IdGenerator
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.session.SessionManager
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * SubscribeToTask and the per-task event stream at the [A2AServer] level, with a scripted agent gated by
 * [CompletableDeferred]s.
 */
class A2AServerSubscribeTest {
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
        suspend fun run(context: RequestContext<SendMessageRequest>, processor: SessionEventProcessor)
    }

    /**
     * Runs the script registered for the text of the incoming message.
     */
    private class ScriptedAgentExecutor(private val scripts: Map<String, Script>) : AgentExecutor {
        override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: SessionEventProcessor) {
            val text = context.params.message.parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }
            val script = scripts[text] ?: error("No script for '$text'")
            script.run(context, eventProcessor)
        }
    }

    private object FixedIdGenerator : IdGenerator {
        override suspend fun generateContextId(message: Message): String = CONTEXT_ID
        override suspend fun generateTaskId(message: Message): String = TASK_ID
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

    /**
     * Scope for agent jobs: a supervisor, so that a failing agent doesn't fail the test.
     */
    private fun TestScope.agentScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    private fun TestScope.createServer(
        scripts: Map<String, Script>,
        bufferCapacity: Int = SessionManager.DEFAULT_SUBSCRIBER_BUFFER_CAPACITY,
        scope: CoroutineScope = agentScope(),
    ): A2AServer = object : A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts),
        agentCard = agentCard,
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator,
        coroutineScope = scope,
    ) {
        override val sessionManager: SessionManager = SessionManager(
            coroutineScope = coroutineScope,
            taskStorage = taskStorage,
            subscriberBufferCapacity = bufferCapacity,
        )
    }

    private fun userMessage(text: String, taskId: String? = null) = Message(
        messageId = "user-$text",
        role = Role.ROLE_USER,
        parts = listOf(TextPart(text)),
        contextId = CONTEXT_ID,
        taskId = taskId,
    )

    private fun request(text: String, taskId: String? = null, returnImmediately: Boolean? = null) = SendMessageRequest(
        message = userMessage(text, taskId),
        configuration = returnImmediately?.let { SendMessageConfiguration(returnImmediately = it) },
    )

    private fun task(state: TaskState, userText: String) = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, timestamp = TIMESTAMP),
        history = listOf(userMessage(userText)),
    )

    private fun status(state: TaskState, text: String = state.name) = TaskStatusUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(
            state = state,
            message = Message(
                messageId = "agent-$text",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart(text)),
                contextId = CONTEXT_ID,
                taskId = TASK_ID,
            ),
            timestamp = TIMESTAMP,
        ),
    )

    private fun artifactChunk(text: String) = TaskArtifactUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart(text))),
        append = true,
    )

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val startScript = Script { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "start"))
        processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    /** Follow-up turn that completes the task. */
    private val finishScript = Script { _, processor ->
        processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(artifactChunk("result"))
        processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
    }

    private val finishEvents = listOf(
        status(TaskState.TASK_STATE_WORKING),
        artifactChunk("result"),
        status(TaskState.TASK_STATE_COMPLETED),
    )

    private class Subscription(val events: MutableList<Event>, val firstEvent: CompletableDeferred<Unit>, val job: Job)

    /**
     * Subscribes to [TASK_ID] in a new coroutine, recording the received events.
     */
    private fun CoroutineScope.subscribe(
        server: A2AServer,
        onEvent: suspend (Event) -> Unit = {},
    ): Subscription {
        val events = mutableListOf<Event>()
        val firstEvent = CompletableDeferred<Unit>()

        val job = launch {
            server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collect {
                events += it
                firstEvent.complete(Unit)
                onEvent(it)
            }
        }

        return Subscription(events, firstEvent, job)
    }

    private suspend fun Flow<Event>.collectExpectingFailure(): Pair<Throwable, List<Event>> {
        val received = mutableListOf<Event>()
        val failure = runCatching { collect { received += it } }.exceptionOrNull()
        return assertNotNull(failure, "Collection was expected to fail") to received
    }

    @Test
    fun testSubscribeAfterInputRequiredTurnEndedFollowsLaterTurn() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript, "finish" to finishScript))

        // The first turn returns at input-required and ends.
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        assertEquals(TaskState.TASK_STATE_INPUT_REQUIRED, stored.status.state)

        val subscription = subscribe(server)
        subscription.firstEvent.await()

        // A follow-up turn from another coroutine (another client) completes the task.
        launch { server.onSendMessage(request("finish", TASK_ID), ctx) }

        // The subscription completes at the terminal state.
        subscription.job.join()

        assertEquals(listOf<Event>(stored) + finishEvents, subscription.events)
    }

    @Test
    fun testSubscribersAndSendStreamReceiveIdenticalEvents() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript, "finish" to finishScript))
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))

        val first = subscribe(server)
        val second = subscribe(server)
        first.firstEvent.await()
        second.firstEvent.await()

        val streamEvents = server.onSendMessageStreaming(request("finish", TASK_ID), ctx).toList()
        first.job.join()
        second.job.join()

        assertEquals(finishEvents, streamEvents)
        assertEquals(listOf<Event>(stored) + streamEvents, first.events)
        assertEquals(first.events, second.events)
    }

    @Test
    fun testCancellingOneSubscriberLeavesOthersRunning() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to startScript, "finish" to finishScript))
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))

        val cancelled = subscribe(server)
        val active = subscribe(server)
        cancelled.firstEvent.await()
        active.firstEvent.await()

        cancelled.job.cancelAndJoin()

        server.onSendMessage(request("finish", TASK_ID), ctx)
        active.job.join()

        assertEquals(listOf<Event>(stored), cancelled.events)
        assertEquals(listOf<Event>(stored) + finishEvents, active.events)
    }

    @Test
    fun testSubscribeToTerminalTaskFailsBeforeFirstEvent() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap())
        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED, "start"))

        val (failure, received) = server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collectExpectingFailure()

        assertEquals(A2AUnsupportedOperationException::class, failure::class)
        assertEquals(emptyList(), received)
    }

    @Test
    fun testSubscribeToUnknownTaskFailsBeforeFirstEvent() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap())

        val (failure, received) = server.onSubscribeToTask(SubscribeToTaskRequest("unknown"), ctx).collectExpectingFailure()

        assertEquals(A2ATaskNotFoundException::class, failure::class)
        assertEquals(emptyList(), received)
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

            val rapidScript = Script { _, processor ->
                processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "rapid"))
                taskCreated.complete(Unit)

                repeat(eventCount) { i ->
                    processor.sendTaskEvent(
                        if (i % 2 == 0) artifactChunk("chunk-$i") else status(TaskState.TASK_STATE_WORKING, "step-$i")
                    )
                }

                allSubscribed.await()
                processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
            }

            try {
                // Large buffer: this test is about snapshot consistency, not about slow subscribers.
                val server = createServer(mapOf("rapid" to rapidScript), bufferCapacity = 100_000, scope = serverScope)

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
        val failingScript = Script { _, processor ->
            processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
            throw IllegalStateException("Agent crashed")
        }
        val server = createServer(mapOf("start" to startScript, "fail" to failingScript))
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))

        val subscription = subscribe(server)
        subscription.firstEvent.await()

        // The requester of the failing turn gets the agent's exception.
        assertFailsWith<IllegalStateException> {
            server.onSendMessage(request("fail", TASK_ID), ctx)
        }

        // The subscriber sees FAILED, then its stream completes.
        subscription.job.join()

        val failed = assertNotNull(taskStorage.get(TASK_ID))
        assertEquals(TaskState.TASK_STATE_FAILED, failed.status.state)
        assertNotNull(failed.status.timestamp)
        assertEquals(
            listOf<Event>(
                stored,
                status(TaskState.TASK_STATE_WORKING),
                TaskStatusUpdateEvent(taskId = TASK_ID, contextId = CONTEXT_ID, status = failed.status),
            ),
            subscription.events
        )
    }

    @Test
    fun testSlowSubscriberIsEvictedOthersFinish() = runTest(timeout = TEST_TIMEOUT) {
        val bufferCapacity = 2
        val updates = List(bufferCapacity * 3) { status(TaskState.TASK_STATE_WORKING, "step-$it") }

        // Number of events the fast subscriber has received.
        val fastReceived = MutableStateFlow(0)

        // Emits one event at a time, waiting until the fast subscriber has received it.
        val manyScript = Script { _, processor ->
            updates.forEachIndexed { i, update ->
                processor.sendTaskEvent(update)
                fastReceived.first { it >= i + 2 } // the snapshot + updates so far
            }
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("start" to startScript, "many" to manyScript), bufferCapacity = bufferCapacity)
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))

        val releaseSlow = CompletableDeferred<Unit>()
        val slowEvents = mutableListOf<Event>()
        val slowSubscribed = CompletableDeferred<Unit>()
        val slowFailure = async {
            runCatching {
                server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ctx).collect {
                    slowEvents += it
                    slowSubscribed.complete(Unit)
                    // Stops consuming after the first event until released.
                    releaseSlow.await()
                }
            }.exceptionOrNull()
        }
        val fast = subscribe(server) { fastReceived.value++ }
        slowSubscribed.await()
        fast.firstEvent.await()

        // The agent and the requester are not blocked by the slow subscriber.
        val response = server.onSendMessage(request("many", TASK_ID), ctx)
        fast.job.join()

        releaseSlow.complete(Unit)

        val completed = status(TaskState.TASK_STATE_COMPLETED)
        assertEquals(assertNotNull(taskStorage.get(TASK_ID)), response)
        assertEquals(listOf<Event>(stored) + updates + completed, fast.events)

        // The slow subscriber gets what was buffered before its buffer overflowed, then an error.
        assertEquals(A2AInternalErrorException::class, assertNotNull(slowFailure.await())::class)
        assertEquals(listOf<Event>(stored) + updates.take(bufferCapacity), slowEvents)
    }
}
