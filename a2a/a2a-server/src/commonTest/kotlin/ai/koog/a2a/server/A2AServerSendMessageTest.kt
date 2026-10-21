package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInvalidAgentResponseException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.turn.IdGenerator
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SendMessage and SendStreamingMessage semantics at the [A2AServer] level: blocking and `returnImmediately`, when
 * streams end and what they begin with, and the rules for messages referencing a task. Uses a scripted agent gated by
 * [CompletableDeferred]s.
 */
class A2AServerSendMessageTest {
    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage

    /** Released by the test to let a gated agent go on. */
    private lateinit var gate: CompletableDeferred<Unit>

    /** Completed when a gated agent has returned from `execute`. */
    private lateinit var agentFinished: CompletableDeferred<Unit>

    private lateinit var executor: ScriptedAgentExecutor

    /** Request contexts of the turns the agent has executed, in order. */
    private val executed get() = executor.executed

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        gate = CompletableDeferred()
        agentFinished = CompletableDeferred()
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript>,
        storage: TaskStorage = taskStorage,
    ): A2AServer {
        executor = ScriptedAgentExecutor(scripts)
        return A2AServer(
            agentExecutor = executor,
            agentCard = testAgentCard(AgentCapabilities(streaming = true)),
            taskStorage = storage,
            idGenerator = FixedIdGenerator(contextId = "generated-context"),
            coroutineScope = agentScope(),
        )
    }

    private fun userMessage(text: String, taskId: String? = null, contextId: String? = CONTEXT_ID) = Message(
        messageId = "user-$text",
        role = Role.ROLE_USER,
        parts = listOf(TextPart(text)),
        contextId = contextId,
        taskId = taskId,
    )

    private fun request(
        text: String,
        taskId: String? = null,
        contextId: String? = CONTEXT_ID,
        returnImmediately: Boolean? = null,
        historyLength: Int? = null,
    ) = SendMessageRequest(
        message = userMessage(text, taskId, contextId),
        configuration = if (returnImmediately != null || historyLength != null) {
            SendMessageConfiguration(returnImmediately = returnImmediately, historyLength = historyLength)
        } else {
            null
        },
    )

    /** A standalone reply of the agent: a [Message] instead of a task. */
    private val reply = Message(
        messageId = "agent-reply",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("Hello")),
        contextId = CONTEXT_ID,
    )

    private fun task(state: TaskState, userText: String) = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, timestamp = TIMESTAMP),
        history = listOf(userMessage(userText)),
    )

    private fun agentMessage(text: String) = Message(
        messageId = "agent-$text",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart(text)),
        contextId = CONTEXT_ID,
        taskId = TASK_ID,
    )

    private fun status(state: TaskState) = TaskStatusUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, message = agentMessage(state.name), timestamp = TIMESTAMP),
    )

    private fun artifactChunk(text: String) = TaskArtifactUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart(text))),
        append = true,
    )

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val askScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "ask"))
        processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    /** The task stored after [askScript]. */
    private val inputRequiredTask = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = status(TaskState.TASK_STATE_INPUT_REQUIRED).status,
        history = listOf(userMessage("ask")),
    )

    /** Follow-up turn that completes the task. */
    private val finishScript = AgentScript { _, processor ->
        processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(artifactChunk("result"))
        processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
    }

    private val finishEvents = listOf(
        status(TaskState.TASK_STATE_WORKING),
        artifactChunk("result"),
        status(TaskState.TASK_STATE_COMPLETED),
    )

    /** Replies with a [Message] that is not part of a task. */
    private val messageReplyScript = AgentScript { context, processor ->
        processor.sendMessage(agentMessage("reply").copy(taskId = null, contextId = context.contextId))
    }

    /** Runs [script], then waits for [gate] before returning. */
    private fun gated(script: AgentScript) = AgentScript { context, processor ->
        script.run(context, processor)
        gate.await()
        agentFinished.complete(Unit)
    }

    // Blocking SendMessage and returnImmediately

    @Test
    fun testBlockingReturnsAtInputRequiredWhileAgentStillRunning() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to gated(askScript)))

        val response = server.onSendMessage(request("ask"), ctx)

        assertEquals(inputRequiredTask, response)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testBlockingReturnsAtCompletedWithArtifacts() = runTest(timeout = TEST_TIMEOUT) {
        val completeScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "complete"))
            processor.sendTaskEvent(artifactChunk("part-1"))
            processor.sendTaskEvent(artifactChunk("part-2"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("complete" to gated(completeScript)))

        val response = server.onSendMessage(request("complete"), ctx)

        val expected = Task(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            status = status(TaskState.TASK_STATE_COMPLETED).status,
            history = listOf(userMessage("complete")),
            artifacts = listOf(
                Artifact(artifactId = "artifact-1", parts = listOf(TextPart("part-1"), TextPart("part-2")))
            ),
        )
        assertEquals(expected, response)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testBlockingWithoutStopStateReturnsStoredTaskAtEndOfTurn() = runTest(timeout = TEST_TIMEOUT) {
        val workingScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "work"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
        }
        val server = createServer(mapOf("work" to workingScript))

        val response = server.onSendMessage(request("work"), ctx)

        val expected = Task(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            status = status(TaskState.TASK_STATE_WORKING).status,
            history = listOf(userMessage("work")),
        )
        assertEquals(expected, response)
    }

    @Test
    fun testReturnImmediatelyReturnsWhileAgentGated() = runTest(timeout = TEST_TIMEOUT) {
        val longScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "long"))
            gate.await()
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
            agentFinished.complete(Unit)
        }
        val server = createServer(mapOf("long" to longScript))

        val response = server.onSendMessage(request("long", returnImmediately = true), ctx)

        assertEquals(task(TaskState.TASK_STATE_SUBMITTED, "long"), response)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testMessageReplyIgnoresReturnImmediately() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("hello" to AgentScript { _, processor -> processor.sendMessage(reply) }))

        assertEquals(reply, server.onSendMessage(request("hello", returnImmediately = true), ctx))
        assertEquals(reply, server.onSendMessage(request("hello", returnImmediately = false), ctx))
        assertEquals(reply, server.onSendMessage(request("hello"), ctx))
    }

    @Test
    fun testExecutorExceptionBeforeTaskCreatedSurfacesToRequester() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("crash" to AgentScript { _, _ -> throw IllegalStateException("Agent crashed") }))

        assertFailsWith<IllegalStateException> {
            server.onSendMessage(request("crash"), ctx)
        }
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testAgentOwnCancellationFailsLikeAnyCrash() = runTest(timeout = TEST_TIMEOUT) {
        /** Runs a blocking SendMessage whose agent creates the task, then fails with [failure]. */
        suspend fun sendFailing(failure: suspend () -> Unit): Pair<Any, Task?> {
            taskStorage = InMemoryTaskStorage()
            val server = createServer(
                mapOf(
                    "work" to AgentScript { _, processor ->
                        processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "work"))
                        failure()
                    }
                )
            )
            return server.onSendMessage(request("work"), ctx) to taskStorage.get(TASK_ID)
        }

        /** The FAILED task the server stores; the message id and timestamp are taken from [stored]. */
        fun failedTask(stored: Task?): Task {
            val storedStatus = assertNotNull(stored).status
            return Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_FAILED,
                    message = Message(
                        messageId = assertNotNull(storedStatus.message).messageId,
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Task failed due to an internal error")),
                        contextId = CONTEXT_ID,
                        taskId = TASK_ID,
                    ),
                    timestamp = storedStatus.timestamp,
                ),
                history = listOf(userMessage("work")),
            )
        }

        val (crashResponse, crashStored) = sendFailing { throw IllegalStateException("Agent crashed") }
        assertEquals(failedTask(crashStored), crashStored)
        assertEquals<Any?>(crashStored, crashResponse)

        val (timeoutResponse, timeoutStored) = sendFailing { withTimeout(1) { awaitCancellation() } }
        assertEquals(failedTask(timeoutStored), timeoutStored)
        assertEquals<Any?>(timeoutStored, timeoutResponse)
    }

    @Test
    fun testAgentOwnCancellationBeforeTaskCreatedSurfacesToRequester() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(
            mapOf(
                "crash" to AgentScript { _, _ -> throw IllegalStateException("Agent gave up") },
                "cancel" to AgentScript { _, _ -> throw CancellationException("Agent gave up") },
            )
        )

        val crash = assertFailsWith<IllegalStateException> { server.onSendMessage(request("crash"), ctx) }
        val cancel = assertFailsWith<IllegalStateException> { server.onSendMessage(request("cancel"), ctx) }

        assertEquals(crash.message, cancel.message)
        assertIs<CancellationException>(cancel.cause)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFailingAgentDoesNotCancelNonSupervisorScope() = runTest(timeout = TEST_TIMEOUT) {
        // A plain job, like `CoroutineScope(Dispatchers.IO)`.
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val idGenerator = object : IdGenerator {
            override suspend fun generateContextId(message: Message): String = CONTEXT_ID
            override suspend fun generateTaskId(message: Message): String = "task-${message.messageId}"
            override suspend fun generateMessageId(taskId: String, contextId: String): String = "server-message"
        }
        fun newTask(context: RequestContext<SendMessageRequest>, state: TaskState) = Task(
            id = context.taskId,
            contextId = context.contextId,
            status = TaskStatus(state = state, timestamp = TIMESTAMP),
        )
        executor = ScriptedAgentExecutor(
            mapOf(
                "crash" to AgentScript { context, processor ->
                    processor.sendTaskEvent(newTask(context, TaskState.TASK_STATE_WORKING))
                    throw IllegalStateException("Agent crashed")
                },
                "complete" to AgentScript { context, processor ->
                    processor.sendTaskEvent(newTask(context, TaskState.TASK_STATE_COMPLETED))
                },
            )
        )
        val server = A2AServer(
            agentExecutor = executor,
            agentCard = testAgentCard(),
            taskStorage = taskStorage,
            idGenerator = idGenerator,
            coroutineScope = scope,
        )

        val failedResponse = server.onSendMessage(request("crash"), ctx)
        val failedTask = assertNotNull(taskStorage.get("task-user-crash"))
        val failedStatus = TaskStatus(
            state = TaskState.TASK_STATE_FAILED,
            message = Message(
                messageId = "server-message",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Task failed due to an internal error")),
                contextId = CONTEXT_ID,
                taskId = "task-user-crash",
            ),
            timestamp = failedTask.status.timestamp,
        )
        assertEquals(failedStatus, failedTask.status)
        assertEquals(failedTask, failedResponse)

        val completedTask = Task(
            id = "task-user-complete",
            contextId = CONTEXT_ID,
            status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED, timestamp = TIMESTAMP),
            history = listOf(userMessage("complete").copy(taskId = "task-user-complete")),
        )
        assertEquals(completedTask, server.onSendMessage(request("complete"), ctx))
        assertTrue(scope.isActive)
    }

    // SendStreamingMessage

    @Test
    fun testStreamingClosesAtEndOfTurnAfterInputRequired() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to gated(askScript)))

        val events = async { server.onSendMessageStreaming(request("ask"), ctx).toList() }
        runCurrent()

        // Input-required is not terminal: the stream stays open until the turn ends.
        assertFalse(events.isCompleted, "The stream must stay open while the turn runs")

        gate.complete(Unit)

        assertEquals(
            listOf<Event>(task(TaskState.TASK_STATE_SUBMITTED, "ask"), status(TaskState.TASK_STATE_INPUT_REQUIRED)),
            events.await()
        )
    }

    @Test
    fun testStreamingClosesAtTerminalStateWhileAgentStillRunning() = runTest(timeout = TEST_TIMEOUT) {
        val completeScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "complete"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("complete" to gated(completeScript)))

        val events = server.onSendMessageStreaming(request("complete"), ctx).toList()

        assertEquals(
            listOf<Event>(task(TaskState.TASK_STATE_WORKING, "complete"), status(TaskState.TASK_STATE_COMPLETED)),
            events
        )
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testFollowUpStreamStartsWithTaskSnapshot() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("finish", TASK_ID), ctx).toList()

        // The snapshot is taken when the turn view is registered: the follow-up message is persisted later, with the
        // agent's first event.
        assertEquals(listOf<Event>(inputRequiredTask) + finishEvents, events)
    }

    @Test
    fun testTaskEventForExistingTaskIsIgnoredAndFollowUpStreamStartsWithSnapshot() = runTest(timeout = TEST_TIMEOUT) {
        val replaceScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_COMPLETED, "replace"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("ask" to askScript, "replace" to replaceScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("replace", TASK_ID), ctx).toList()

        // The Task replacement is neither streamed nor stored (not even its terminal state), so the stream begins with
        // the snapshot.
        assertEquals(
            listOf<Event>(
                inputRequiredTask,
                status(TaskState.TASK_STATE_WORKING),
                status(TaskState.TASK_STATE_COMPLETED),
            ),
            events
        )
        assertEquals(
            inputRequiredTask.copy(
                status = status(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    userMessage("replace", TASK_ID),
                    agentMessage(TaskState.TASK_STATE_WORKING.name),
                ),
            ),
            taskStorage.get(TASK_ID)
        )
    }

    @Test
    fun testFollowUpWhoseOnlyEventIsIgnoredTaskStreamsSnapshotOnly() = runTest(timeout = TEST_TIMEOUT) {
        val replaceScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_COMPLETED, "replace"))
        }
        val server = createServer(mapOf("ask" to askScript, "replace" to replaceScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("replace", TASK_ID), ctx).toList()

        // The ignored Task is not a status or artifact update: the user message is not persisted.
        assertEquals(listOf<Event>(inputRequiredTask), events)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFollowUpWithoutAgentEventsDoesNotPersistUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "silent" to AgentScript { _, _ -> }))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("silent", TASK_ID), ctx)

        assertEquals(inputRequiredTask, response)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFollowUpStreamSnapshotAppliesHistoryLength() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("finish", TASK_ID, historyLength = 0), ctx).toList()

        assertEquals(listOf<Event>(inputRequiredTask.copy(history = null)) + finishEvents, events)
    }

    @Test
    fun testMessageOnlyStreamHasExactlyOneMessage() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("hello" to gated { _, processor -> processor.sendMessage(reply) }))

        // The stream closes after the message, even though the agent hasn't returned yet.
        val events = server.onSendMessageStreaming(request("hello"), ctx).toList()

        assertEquals(listOf<Event>(reply), events)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testNewTaskStatusUpdateBeforeTaskIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val agentFailure = CompletableDeferred<Throwable>()
        val statusFirstScript = AgentScript { _, processor ->
            try {
                processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
            } catch (e: Exception) {
                agentFailure.complete(e)
                throw e
            }
        }
        val server = createServer(mapOf("status" to statusFirstScript))

        val (failure, received) = server.onSendMessageStreaming(request("status"), ctx).collectExpectingFailure()
        assertFailsWith<A2AInvalidAgentResponseException> { server.onSendMessage(request("status"), ctx) }

        // The agent still gets the storage exception; the requester gets an invalid agent response.
        assertEquals(TaskOperationException::class, agentFailure.await()::class)
        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
        assertEquals(emptyList(), received)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testSwallowedRejectedEventIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val swallowing = AgentScript { _, processor ->
            runCatching { processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING)) }
        }
        val server = createServer(mapOf("status" to swallowing))

        assertFailsWith<A2AInvalidAgentResponseException> {
            server.onSendMessage(request("status"), ctx)
        }
        val (failure, _) = server.onSendMessageStreaming(request("status"), ctx).collectExpectingFailure()
        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
    }

    @Test
    fun testNewTaskTurnWithoutEventsIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("silent" to AgentScript { _, _ -> }))

        assertFailsWith<A2AInvalidAgentResponseException> { server.onSendMessage(request("silent"), ctx) }
        val (failure, received) = server.onSendMessageStreaming(request("silent"), ctx).collectExpectingFailure()

        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
        assertEquals(emptyList(), received)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testMessageReplyForExistingTaskIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "reply" to messageReplyScript))
        server.onSendMessage(request("ask"), ctx)

        assertFailsWith<A2AInvalidAgentResponseException> {
            server.onSendMessage(request("reply", TASK_ID), ctx)
        }
        assertEquals(TaskState.TASK_STATE_FAILED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testMessageReplyForExistingTaskIsInvalidAgentResponseWhenStreaming() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "reply" to messageReplyScript))
        server.onSendMessage(request("ask"), ctx)

        val (failure, received) = server.onSendMessageStreaming(request("reply", TASK_ID), ctx).collectExpectingFailure()

        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
        assertEquals(emptyList(), received)
    }

    // Requester cancellation

    @Test
    fun testRequesterCancelledRightAfterTurnStartDoesNotBlockAgent() = runTest(timeout = TEST_TIMEOUT) {
        var cancelOnTurnViewRead = false
        // Cancels the requester while its turn view is being registered: the cancellation is first observed after
        // the turn has started, before the requester collects the view.
        val storage = object : TaskStorage by taskStorage {
            override suspend fun get(taskId: String, historyLength: Int?, includeArtifacts: Boolean): Task? =
                taskStorage.get(taskId, historyLength, includeArtifacts).also {
                    if (cancelOnTurnViewRead && includeArtifacts) {
                        cancelOnTurnViewRead = false
                        currentCoroutineContext().cancel()
                    }
                }
        }
        val server = createServer(mapOf("ask" to askScript, "finish" to gated(finishScript)), storage)
        server.onSendMessage(request("ask"), ctx)
        gate.complete(Unit)

        cancelOnTurnViewRead = true
        val requester = launch { server.onSendMessage(request("finish", TASK_ID), ctx) }
        requester.join()
        runCurrent()

        assertTrue(requester.isCancelled)
        // Nobody collects the requester's view: the agent's events must not wait for it.
        assertTrue(agentFinished.isCompleted, "The agent must not be blocked by the abandoned turn view")
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    // Messages referencing a task

    @Test
    fun testUnknownTaskIdFailsWithTaskNotFound() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("finish" to finishScript))

        assertFailsWith<A2ATaskNotFoundException> {
            server.onSendMessage(request("finish", taskId = "unknown"), ctx)
        }
        assertEquals(emptyList(), executed)
    }

    @Test
    fun testMessageToTerminalTaskFailsWithUnsupportedOperation() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("finish" to finishScript))
        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED, "ask"))

        assertFailsWith<A2AUnsupportedOperationException> {
            server.onSendMessage(request("finish", TASK_ID), ctx)
        }

        val (failure, received) = server.onSendMessageStreaming(request("finish", TASK_ID), ctx)
            .collectExpectingFailure()
        assertEquals(A2AUnsupportedOperationException::class, failure::class)
        assertEquals(emptyList(), received)

        assertEquals(emptyList(), executed)
    }

    @Test
    fun testContextIdInferredFromTask() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("finish", TASK_ID, contextId = null), ctx)

        // The follow-up runs in the task's context, not in a generated one.
        assertEquals(listOf(CONTEXT_ID, CONTEXT_ID), executed.map { it.contextId })
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = status(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    // Stored with the task id and the context id of the task, though the client sent no context id.
                    userMessage("finish", TASK_ID),
                    agentMessage(TaskState.TASK_STATE_WORKING.name),
                ),
                artifacts = listOf(Artifact(artifactId = "artifact-1", parts = listOf(TextPart("result")))),
            ),
            response
        )
    }

    @Test
    fun testMismatchedContextIdRejected() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        assertFailsWith<A2AInvalidParamsException> {
            server.onSendMessage(request("finish", TASK_ID, contextId = "other-context"), ctx)
        }

        assertEquals(1, executed.size)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
    }

    @Test
    fun testHistoryLengthAppliedToBlockingResponse() = runTest(timeout = TEST_TIMEOUT) {
        val stepsScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "steps"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("steps" to stepsScript))

        val response = server.onSendMessage(request("steps", historyLength = 1), ctx)

        // The stored history is [user message, WORKING status message]; only the most recent one is returned.
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = status(TaskState.TASK_STATE_INPUT_REQUIRED).status,
                history = listOf(agentMessage(TaskState.TASK_STATE_WORKING.name)),
            ),
            response
        )
    }

    @Test
    fun testNegativeHistoryLengthRejected() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript))

        assertFailsWith<A2AInvalidParamsException> {
            server.onSendMessage(request("ask", historyLength = -1), ctx)
        }
        assertEquals(emptyList(), executed)
    }

    @Test
    fun testFollowUpAgentCanOnlySendTaskEvents() = runTest(timeout = TEST_TIMEOUT) {
        val messageFailure = CompletableDeferred<Throwable>()
        val replyScript = AgentScript { context, processor ->
            // The task exists: a message reply is rejected, a status update without a Task first is accepted.
            messageFailure.complete(assertFailsWith<InvalidEventException> { messageReplyScript.run(context, processor) })
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("ask" to askScript, "reply" to replyScript))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("reply", TASK_ID), ctx)

        assertTrue(messageFailure.isCompleted)
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = status(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    userMessage("reply", TASK_ID),
                ),
            ),
            response
        )
    }

    // History persistence and RequestContext.task

    private fun bareTask(state: TaskState, message: Message? = null) = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, message = message, timestamp = TIMESTAMP),
    )

    private suspend fun A2AServer.history(): List<Message>? =
        onGetTask(GetTaskRequest(id = TASK_ID), ctx).history

    @Test
    fun testServerPersistsUserMessageOfNewTaskWhenAgentOmitsIt() = runTest(timeout = TEST_TIMEOUT) {
        val bareScript = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_SUBMITTED))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("bare" to bareScript))

        val response = server.onSendMessage(request("bare"), ctx)

        assertEquals(
            bareTask(TaskState.TASK_STATE_INPUT_REQUIRED, agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name))
                .copy(history = listOf(userMessage("bare", TASK_ID))),
            response
        )
        assertEquals(listOf(userMessage("bare", TASK_ID)), server.history())
    }

    @Test
    fun testInitialTaskStreamedToRequesterCarriesUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val bareScript = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("bare" to bareScript))

        val events = server.onSendMessageStreaming(request("bare"), ctx).toList()

        assertEquals(
            listOf<Event>(
                bareTask(TaskState.TASK_STATE_WORKING).copy(history = listOf(userMessage("bare", TASK_ID))),
                status(TaskState.TASK_STATE_COMPLETED),
            ),
            events
        )
    }

    @Test
    fun testHistoryAfterInputRequiredTurnAndFollowUpIsInOrderWithoutDuplicates() = runTest(timeout = TEST_TIMEOUT) {
        val askBare = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_SUBMITTED))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("ask" to askBare, "finish" to finishScript))

        server.onSendMessage(request("ask"), ctx)
        val response = server.onSendMessage(request("finish", TASK_ID), ctx)

        val expectedHistory = listOf(
            userMessage("ask", TASK_ID),
            agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
            userMessage("finish", TASK_ID),
            agentMessage(TaskState.TASK_STATE_WORKING.name),
        )
        assertEquals(expectedHistory, response.let { (it as Task).history })
        assertEquals(expectedHistory, server.history())
        assertEquals(expectedHistory.size, expectedHistory.map { it.messageId }.toSet().size)
    }

    @Test
    fun testAgentThatAlreadyIncludesUserMessageDoesNotDuplicateIt() = runTest(timeout = TEST_TIMEOUT) {
        // askScript creates the task with the user message in the history, and the same user message as the status
        // message of a Task, which the storage moves into the history on the next status update.
        val selfPersisting = AgentScript { _, processor ->
            val user = userMessage("self")
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_WORKING, user).copy(history = listOf(user)))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("self" to selfPersisting))

        server.onSendMessage(request("self"), ctx)

        assertEquals(listOf(userMessage("self")), server.history())
    }

    @Test
    fun testFollowUpRequestContextTaskIsFullStoredTask() = runTest(timeout = TEST_TIMEOUT) {
        val artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("draft")))
        val askWithArtifact = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(artifactChunk("draft"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("ask" to askWithArtifact, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        server.onSendMessage(request("finish", TASK_ID), ctx)

        assertEquals(null, executed[0].task)
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                // As stored when the turn starts: the follow-up message is not in it, and the status message is still
                // the status message.
                status = status(TaskState.TASK_STATE_INPUT_REQUIRED).status,
                history = listOf(userMessage("ask", TASK_ID)),
                artifacts = listOf(artifact),
            ),
            executed[1].task
        )
    }
}
